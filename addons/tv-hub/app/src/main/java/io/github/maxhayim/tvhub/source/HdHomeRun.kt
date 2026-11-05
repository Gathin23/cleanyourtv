package io.github.maxhayim.tvhub.source

import io.github.maxhayim.tvhub.data.Http
import io.github.maxhayim.tvhub.model.RawChannel
import io.github.maxhayim.tvhub.model.SourceConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.zip.CRC32

/**
 * SiliconDust HDHomeRun network tuners, through the device's own local HTTP interface
 * (discover.json and lineup.json). Nothing goes to SiliconDust's cloud, so the guide is not read
 * from it: add an XMLTV source for programme data.
 */
object HdHomeRun {
    data class Device(val host: String, val name: String, val model: String, val tuners: Int)

    /** UDP broadcast discovery on port 65001, the tuners' own protocol. Blocking for [timeoutMs]. */
    fun discover(timeoutMs: Int = 2500): List<Device> {
        val hosts = LinkedHashSet<String>()
        DatagramSocket().use { s ->
            s.broadcast = true
            s.soTimeout = 300
            val req = request()
            val deadline = System.currentTimeMillis() + timeoutMs
            var nextSend = 0L
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                if (System.currentTimeMillis() >= nextSend) {
                    runCatching { s.send(DatagramPacket(req, req.size, InetAddress.getByName("255.255.255.255"), 65001)) }
                    nextSend = System.currentTimeMillis() + 800
                }
                val p = DatagramPacket(buf, buf.size)
                try {
                    s.receive(p)
                    // A discover reply is packet type 0x0003.
                    if (p.length >= 4 && buf[0].toInt() == 0 && buf[1].toInt() == 3) p.address.hostAddress?.let { hosts += it }
                } catch (_: SocketTimeoutException) {
                }
            }
        }
        return hosts.mapNotNull { h -> runCatching { info(h) }.getOrNull() }
    }

    fun info(host: String): Device {
        val o = JSONObject(Http.text("http://${clean(host)}/discover.json", 64 * 1024))
        return Device(clean(host), o.optString("FriendlyName", "HDHomeRun"), o.optString("ModelNumber"), o.optInt("TunerCount"))
    }

    fun load(cfg: SourceConfig): ProviderResult {
        val host = clean(cfg.url)
        if (host.isEmpty()) throw IOException("Enter the tuner's address, or scan for it")
        try {
            val lineupUrl = runCatching { JSONObject(Http.text("http://$host/discover.json", 64 * 1024)).optString("LineupURL") }
                .getOrNull()?.takeIf { it.startsWith("http") } ?: "http://$host/lineup.json"
            val arr = JSONArray(Http.text(lineupUrl, 8L * 1024 * 1024))
            return parseLineup(arr, cfg)
        } catch (e: org.json.JSONException) {
            throw IOException("The tuner's channel list could not be read")
        } catch (e: Exception) {
            throw Http.friendly(e)
        }
    }

    /** Encrypted cable channels (DRM 1) cannot be played by any app but the tuner maker's, so they are skipped. */
    internal fun parseLineup(arr: JSONArray, cfg: SourceConfig): ProviderResult {
        val out = ArrayList<RawChannel>()
        var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var url = o.optString("URL")
            if (!url.startsWith("http") || o.optInt("DRM") == 1) { skipped++; continue }
            if (cfg.transcode) url += (if ('?' in url) "&" else "?") + "transcode=heavy"
            out += RawChannel(
                sourceId = cfg.id, sourceName = cfg.name,
                name = o.optString("GuideName").ifBlank { o.optString("GuideNumber") },
                number = o.optString("GuideNumber").takeIf { it.isNotBlank() },
                url = url,
            )
        }
        return ProviderResult(out, emptyList(), skipped)
    }

    private fun clean(h: String) = h.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')

    internal fun request(): ByteArray {
        // Payload: device type = any (tag 1), device id = any (tag 2), each 4 bytes.
        val payload = byteArrayOf(1, 4, -1, -1, -1, -1, 2, 4, -1, -1, -1, -1)
        val head = byteArrayOf(0, 2, 0, payload.size.toByte())
        val body = head + payload
        val crc = CRC32().apply { update(body) }.value
        return body + byteArrayOf(crc.toByte(), (crc shr 8).toByte(), (crc shr 16).toByte(), (crc shr 24).toByte())
    }
}
