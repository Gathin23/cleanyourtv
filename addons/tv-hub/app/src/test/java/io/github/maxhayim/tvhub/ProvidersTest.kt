package io.github.maxhayim.tvhub

import io.github.maxhayim.tvhub.model.SourceConfig
import io.github.maxhayim.tvhub.model.SourceType
import io.github.maxhayim.tvhub.source.HdHomeRun
import io.github.maxhayim.tvhub.source.Providers
import io.github.maxhayim.tvhub.util.Log
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.zip.CRC32

class ProvidersTest {
    @Test fun dispatcharrOutputUrls() {
        assertEquals("http://nas:9191/output/m3u" to "http://nas:9191/output/epg", Providers.dispatcharrUrls("http://nas:9191/", ""))
        assertEquals("http://nas:9191/output/m3u/Living%20Room" to "http://nas:9191/output/epg/Living%20Room", Providers.dispatcharrUrls(" http://nas:9191 ", "Living Room"))
    }

    @Test fun hdhomerunLineupSkipsDrmAndAddsTranscode() {
        val arr = JSONArray("""[
            {"GuideNumber":"2.1","GuideName":"KABC","URL":"http://192.168.1.50:5004/auto/v2.1"},
            {"GuideNumber":"7.1","GuideName":"Cable","URL":"http://192.168.1.50:5004/auto/v7.1","DRM":1},
            {"GuideNumber":"9.1","GuideName":"","URL":"http://192.168.1.50:5004/auto/v9.1"}
        ]""")
        val cfg = SourceConfig(id = 3, type = SourceType.HDHOMERUN, name = "Antenna", transcode = true)
        val r = HdHomeRun.parseLineup(arr, cfg)
        assertEquals(1, r.skipped)
        assertEquals(listOf("KABC", "9.1"), r.channels.map { it.name })
        assertEquals("http://192.168.1.50:5004/auto/v2.1?transcode=heavy", r.channels[0].url)
        assertEquals("2.1", r.channels[0].number)
    }

    @Test fun hdhomerunDiscoverPacketHasValidCrc() {
        val p = HdHomeRun.request()
        assertEquals(20, p.size)
        assertEquals(2, p[1].toInt()) // discover request
        val crc = CRC32().apply { update(p, 0, 16) }.value
        val le = (p[16].toLong() and 0xff) or ((p[17].toLong() and 0xff) shl 8) or ((p[18].toLong() and 0xff) shl 16) or ((p[19].toLong() and 0xff) shl 24)
        assertEquals(crc, le)
    }

    @Test fun redactHidesPathAndQuery() {
        assertEquals("http://iptv.example:8080/…", Log.redact("http://iptv.example:8080/get.php?username=me&password=secret"))
        assertEquals("https://host/…", Log.redact("https://user:pw@host/path"))
        assertEquals("…", Log.redact("garbage"))
    }
}
