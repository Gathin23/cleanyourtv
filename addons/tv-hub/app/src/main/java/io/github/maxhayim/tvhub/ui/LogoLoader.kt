package io.github.maxhayim.tvhub.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import io.github.maxhayim.tvhub.data.Http
import io.github.maxhayim.tvhub.parse.M3uParser
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Channel logos named by the playlist, fetched on demand, downscaled, and cached in memory and in
 * the cache directory (which Android may clear). Three threads, a small memory share: budget TVs
 * have 1 GB of RAM.
 */
class LogoLoader(context: Context) {
    private val dir = File(context.cacheDir, "logos").apply { mkdirs() }
    private val mem = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = HashSet<String>()
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    /** [shown] runs on the main thread with true once a logo is on the view, false if there is none. */
    fun load(url: String?, view: ImageView, heightPx: Int, shown: (Boolean) -> Unit = {}) {
        view.setImageDrawable(null)
        view.tag = url
        if (url == null || !M3uParser.isHttp(url) || synchronized(failed) { url in failed }) { shown(false); return }
        mem.get(url)?.let { view.setImageBitmap(it); shown(true); return }
        shown(false)
        pool.execute {
            val bmp = runCatching { fetch(url, heightPx) }.getOrNull()
            if (bmp == null) synchronized(failed) { failed += url } else mem.put(url, bmp)
            main.post { if (view.tag == url && bmp != null) { view.setImageBitmap(bmp); shown(true) } }
        }
    }

    /** For views that draw logos themselves: the bitmap if cached, else null and [ready] once it arrives. */
    fun bitmap(url: String?, heightPx: Int, ready: () -> Unit): Bitmap? {
        if (url == null || !M3uParser.isHttp(url) || synchronized(failed) { url in failed }) return null
        mem.get(url)?.let { return it }
        synchronized(pending) { if (!pending.add(url)) return null }
        pool.execute {
            val bmp = runCatching { fetch(url, heightPx) }.getOrNull()
            if (bmp == null) synchronized(failed) { failed += url } else mem.put(url, bmp)
            synchronized(pending) { pending -= url }
            if (bmp != null) main.post(ready)
        }
        return null
    }
    private val pending = HashSet<String>()

    private fun fetch(url: String, heightPx: Int): Bitmap? {
        val f = File(dir, sha(url))
        val bytes = if (f.exists() && System.currentTimeMillis() - f.lastModified() < 7 * 86_400_000L) f.readBytes()
        else Http.open(url, 1024 * 1024).use { it.readBytes() }.also { runCatching { f.writeBytes(it) } }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outHeight <= 0) return null
        var sample = 1
        while (o.outHeight / (sample * 2) >= heightPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
