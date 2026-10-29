package io.github.maxhayim.tvhub.player

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.maxhayim.tvhub.TvHubApp
import io.github.maxhayim.tvhub.model.Channel
import io.github.maxhayim.tvhub.model.Programme
import io.github.maxhayim.tvhub.model.StreamRef
import io.github.maxhayim.tvhub.ui.Ui
import io.github.maxhayim.tvhub.ui.Ui.dp
import io.github.maxhayim.tvhub.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen live playback with Media3. Keys:
 * Up/Down and Channel +/- change channel; Left or the Last button goes back to the previous one;
 * digits tune by number; OK shows what is on; Menu or a long OK opens audio and subtitles;
 * Back returns to where playback started. The player is released as soon as the screen stops.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private val scope = MainScope()
    private val repo get() = (application as TvHubApp).repo
    private val main = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var spinner: ProgressBar
    private lateinit var overlay: InfoOverlay
    private lateinit var errorBox: android.widget.LinearLayout
    private lateinit var errorText: android.widget.TextView

    private var list: List<Channel> = emptyList()
    private var index = 0
    private var previous = -1
    private var streamIndex = 0
    private var retries = 0
    private var triedHls = false
    private var digits = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        list = Queue.channels
        index = Queue.index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        if (list.isEmpty()) { finish(); return }

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(0xFF000000.toInt())
            isFocusable = false
        }
        root.addView(playerView, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        spinner = ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(Ui.TEXT) }
        root.addView(spinner, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        overlay = InfoOverlay(this)
        root.addView(overlay, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM))
        errorBox = Ui.vertical(this, 10).apply {
            background = Ui.rounded(this@PlayerActivity, 0xE6161A20.toInt(), 16)
            setPadding(dp(32), dp(24), dp(32), dp(24)); visibility = View.GONE
        }
        errorText = Ui.text(this, "", 18f, lines = 4)
        errorBox.addView(errorText)
        errorBox.addView(Ui.text(this, "OK: try again · Up/Down: other channels · Back: guide", 15f, Ui.DIM))
        root.addView(errorBox, FrameLayout.LayoutParams(dp(640), Ui.WRAP, Gravity.CENTER))
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        if (list.isEmpty()) return
        createPlayer()
        tune(index, remember = false)
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    override fun onDestroy() { scope.cancel(); main.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun createPlayer() {
        if (player != null) return
        player = ExoPlayer.Builder(this).build().also { p ->
            playerView.player = p
            p.playWhenReady = true
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    spinner.visibility = if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE && errorBox.visibility != View.VISIBLE) View.VISIBLE else View.GONE
                    if (state == Player.STATE_READY) {
                        retries = 0; errorBox.visibility = View.GONE
                        // One line per tune for diagnostics: formats only, never the address.
                        val v = p.videoFormat; val a = p.audioFormat
                        Log.i("Playing: video ${v?.sampleMimeType} ${v?.width}x${v?.height}, audio ${a?.sampleMimeType} ${a?.channelCount}ch")
                    }
                    // A live stream that ends was cut by the server; start it again.
                    if (state == Player.STATE_ENDED) recover("The stream ended")
                }
                override fun onPlayerError(error: PlaybackException) { onError(error) }
            })
        }
    }

    private fun releasePlayer() {
        main.removeCallbacksAndMessages(null)
        playerView.player = null
        player?.release()
        player = null
    }

    private fun tune(i: Int, remember: Boolean = true) {
        if (list.isEmpty()) return
        val target = ((i % list.size) + list.size) % list.size
        if (remember && target != index) previous = index
        index = target
        streamIndex = 0; retries = 0; triedHls = false
        errorBox.visibility = View.GONE
        val ch = list[index]
        repo.addRecent(ch.key)
        playStream(ch.streams.getOrNull(0))
        showInfo()
    }

    private fun playStream(s: StreamRef?, forceHls: Boolean = false) {
        val p = player ?: return
        if (s == null) { showError("This channel has no stream address."); return }
        val headers = HashMap<String, String>()
        s.referrer?.let { headers["Referer"] = it }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(s.userAgent ?: "TVHub/1.0 (Android TV; Media3)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000).setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(headers)
        val item = MediaItem.Builder().setUri(s.url).apply {
            if (forceHls || looksHls(s.url)) setMimeType(MimeTypes.APPLICATION_M3U8)
            setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(C.TIME_UNSET).build())
        }.build()
        // One retry per load: Media3's default of three, each waiting on a timeout, kept a dead host
        // on a spinner for over a minute. Recovery below decides what happens next.
        val factory = DefaultMediaSourceFactory(this).setDataSourceFactory(http)
            .setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(1))
        p.setMediaSource(factory.createMediaSource(item))
        p.prepare()
        p.play()
    }

    private fun looksHls(url: String) = url.substringBefore('?').lowercase().let { it.endsWith(".m3u8") || it.endsWith(".m3u") } || url.contains("m3u8", ignoreCase = true)

    /**
     * Recovery: fall behind a live window → jump to live; a stream with no extension that is really
     * HLS → retry as HLS; network trouble → retry twice with a short wait; then the next stream of
     * a merged channel; then say what went wrong.
     */
    private fun onError(e: PlaybackException) {
        Log.w("Playback error ${e.errorCodeName}")
        val p = player ?: return
        val ch = list[index]
        val s = ch.streams.getOrNull(streamIndex)
        when {
            e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> { p.seekToDefaultPosition(); p.prepare() }
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && !triedHls && s != null && !looksHls(s.url) -> {
                triedHls = true; playStream(s, forceHls = true)
            }
            isTransient(e) && retries < 2 -> recover(null)
            streamIndex + 1 < ch.streams.size -> {
                streamIndex++; retries = 0; triedHls = false
                playStream(ch.streams[streamIndex])
                overlay.setSource(ch.streams[streamIndex].sourceName, index, list.size)
                overlay.flash("Trying ${ch.streams[streamIndex].sourceName}")
            }
            else -> showError(message(e))
        }
    }

    private fun recover(note: String?) {
        retries++
        if (retries > 3) { showError(note ?: "The stream keeps stopping."); return }
        spinner.visibility = View.VISIBLE
        main.postDelayed({ player?.let { it.seekToDefaultPosition(); it.prepare(); it.play() } }, 1500L * retries)
    }

    /** Errors worth starting again from scratch. Connection failures are not: the load already retried. */
    private fun isTransient(e: PlaybackException) = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_TIMEOUT -> true
        else -> false
    }

    private fun message(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (val code = (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode) {
            404, 410 -> "The stream is not on its server any more (HTTP $code)."
            401, 403 -> "The stream's server refused it (HTTP $code). The link may have expired, or the account may be in use elsewhere."
            null -> "The stream's server answered with an error."
            else -> "The stream's server answered HTTP $code."
        }
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The stream was not found on its server."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Can't reach the stream's server."
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "This TV blocked an unencrypted (http) stream."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "This stream's format is not supported."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
            "This TV can't decode this channel's video or sound (for example MPEG-2 broadcast video or AC-4 audio)."
        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED, PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> "This channel is encrypted (DRM) and can't be played here."
        else -> "Playback failed (${e.errorCodeName})."
    }

    private fun showError(msg: String) {
        spinner.visibility = View.GONE
        errorText.text = "${list[index].name}\n$msg"
        errorBox.visibility = View.VISIBLE
    }

    private fun showInfo() {
        val ch = list[index]
        overlay.bindChannel(ch, list.size, index)
        scope.launch {
            val nn = withContext(Dispatchers.IO) { repo.nowNext(listOf(ch))[ch.key] }
            if (list.getOrNull(index)?.key == ch.key) overlay.bindProgrammes(nn?.first, nn?.second)
        }
        overlay.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { tune(index + 1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { tune(index - 1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_LAST_CHANNEL -> { if (previous >= 0) tune(previous) else overlay.flash("No previous channel yet"); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> { showInfo(); return true }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_CAPTIONS -> { tracksDialog(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { event.startTracking(); return true }
        }
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) { digit(keyCode - KeyEvent.KEYCODE_0); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) { tracksDialog(); return true }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.isTracking && !event.isCanceled) {
                if (errorBox.visibility == View.VISIBLE) tune(index, remember = false) else showInfo()
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /** Number entry: digits gather for 1.5 s, then the channel with that number is tuned. */
    private fun digit(d: Int) {
        digits = (digits + d).takeLast(5)
        overlay.flash(digits)
        main.removeCallbacks(tuneDigits)
        main.postDelayed(tuneDigits, 1500)
    }

    private val tuneDigits = Runnable {
        val i = list.indexOfFirst { it.number == digits }.takeIf { it >= 0 } ?: list.indexOfFirst { it.number?.substringBefore('.') == digits }
        if (i >= 0) tune(i) else overlay.flash("No channel $digits")
        digits = ""
    }

    /** Audio and subtitle tracks the stream offers, as one D-pad list. */
    private fun tracksDialog() {
        val p = player ?: return
        data class Opt(val label: String, val apply: () -> Unit)
        val opts = ArrayList<Opt>()
        val groups = p.currentTracks.groups
        groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { g ->
            for (t in 0 until g.length) {
                if (!g.isTrackSupported(t)) continue
                val f = g.getTrackFormat(t)
                val mark = if (g.isTrackSelected(t)) "● " else "   "
                opts += Opt(mark + "Audio: " + (f.label ?: f.language ?: "Track ${opts.size + 1}") + (f.channelCount.takeIf { it > 0 }?.let { " · ${it}ch" } ?: "")) {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, t)).build()
                }
            }
        }
        val text = groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val textOn = text.any { it.isSelected }
        opts += Opt((if (!textOn) "● " else "   ") + "Subtitles: off") {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        }
        text.forEach { g: Tracks.Group ->
            for (t in 0 until g.length) {
                if (!g.isTrackSupported(t)) continue
                val f = g.getTrackFormat(t)
                opts += Opt((if (g.isTrackSelected(t)) "● " else "   ") + "Subtitles: " + (f.label ?: f.language ?: "Track ${t + 1}")) {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, t)).build()
                }
            }
        }
        if (text.isEmpty()) opts += Opt("   No subtitles or captions in this stream") {}
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Audio and subtitles")
            .setItems(opts.map { it.label }.toTypedArray()) { _, i -> opts[i].apply() }
            .show()
    }

    /** Hands the channel list to the player without putting thousands of entries through an Intent. */
    object Queue {
        var channels: List<Channel> = emptyList()
        var index = 0
    }

    companion object {
        fun start(c: Context, list: List<Channel>, index: Int) {
            Queue.channels = list
            Queue.index = index
            c.startActivity(Intent(c, PlayerActivity::class.java))
        }
    }

    /** Channel banner at the bottom: number, name, now and next, hidden after a few seconds. */
    private inner class InfoOverlay(c: Context) : android.widget.LinearLayout(c) {
        private val title = Ui.text(c, "", 30f, bold = true)
        private val position = Ui.text(c, "", 15f, Ui.DIM)
        private val now = Ui.text(c, "", 19f)
        private val next = Ui.text(c, "", 16f, Ui.DIM)
        private val note = Ui.text(c, "", 16f, Ui.ACCENT)
        private val hide = Runnable { animate().alpha(0f).setDuration(if (Ui.animations(context)) 250 else 0).start() }

        init {
            orientation = VERTICAL
            setPadding(dp(64), dp(48), dp(64), dp(40))
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xE6000000.toInt(), 0x00000000))
            addView(note); addView(title); addView(position); addView(now); addView(next)
            alpha = 0f
        }

        fun bindChannel(ch: Channel, total: Int, i: Int) {
            title.text = listOfNotNull(ch.number, ch.name).joinToString("   ")
            position.text = "${i + 1} of $total · ${ch.streams.getOrNull(streamIndex)?.sourceName ?: ""}"
            now.text = if (ch.epgIds.isEmpty()) "No guide information" else ""
            next.text = ""; note.text = ""
        }

        fun setSource(name: String, i: Int, total: Int) { position.text = "${i + 1} of $total · $name" }

        fun bindProgrammes(cur: Programme?, nx: Programme?) {
            if (cur != null) now.text = "Now  ${Ui.time(context, cur.start)} – ${Ui.time(context, cur.stop)}   ${cur.title}"
            if (nx != null) next.text = "Next  ${Ui.time(context, nx.start)}   ${nx.title}"
        }

        fun show() { alpha = 1f; main.removeCallbacks(hide); main.postDelayed(hide, 5000) }
        fun flash(msg: String) { note.text = msg; show() }
    }
}
