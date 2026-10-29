package io.github.maxhayim.tvhub

import io.github.maxhayim.tvhub.parse.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    private fun parse(text: String) = M3uParser.parse(text.reader().buffered(), 7, "Test")
    private fun fixture() = M3uParser.parse(javaClass.getResourceAsStream("/sample.m3u")!!.bufferedReader(), 7, "Test")

    @Test fun readsAttributesTitleAndGroup() {
        val r = fixture()
        val news = r.channels[0]
        assertEquals("Example News", news.name)
        assertEquals("news.example", news.tvgId)
        assertEquals("Example News HD", news.tvgName)
        assertEquals("2", news.number)
        assertEquals("News", news.group)
        assertEquals("http://192.168.1.10:8000/logos/news.png", news.logo)
        assertEquals(7L, news.sourceId)
    }

    @Test fun commaInsideQuotedValueDoesNotStartTheTitle() {
        val sport = fixture().channels[1]
        assertEquals("Sport, Live", sport.group)
        assertEquals("Example Sport, Weekend", sport.name)
        assertEquals("5.1", sport.number)
    }

    @Test fun vlcOptionsBecomeHeaders() {
        val sport = fixture().channels[1]
        assertEquals("ExamplePlayer/2.0", sport.userAgent)
        assertEquals("http://192.168.1.10/", sport.referrer)
        assertNull(fixture().channels[0].userAgent)
    }

    @Test fun extgrpAppliesWhenThereIsNoGroupTitle() {
        assertEquals("Movies", fixture().channels[2].group)
    }

    @Test fun skipsNonHttpStreamsAndCountsThem() {
        val r = fixture()
        assertEquals(1, r.skipped)
        assertTrue(r.channels.none { it.url.startsWith("udp") })
    }

    @Test fun emptyTvgIdAndNonHttpLogoAreDropped() {
        val kids = fixture().channels.first { it.name == "Kids" }
        assertNull(kids.tvgId)
        assertNull(kids.logo)
    }

    @Test fun bareUrlGetsNameFromPath() {
        val bare = fixture().channels.last()
        assertEquals("stream.ts", bare.name)
        assertNull(bare.group)
    }

    @Test fun headerGuideUrlsAreCollectedOnce() {
        assertEquals(listOf("http://192.168.1.10:8000/guide.xml.gz", "http://192.168.1.10:8000/extra.xml"), fixture().epgUrls)
    }

    @Test fun byteOrderMarkAndCrlfAreTolerated() {
        val r = parse("\uFEFF#EXTM3U\r\n#EXTINF:-1,One\r\nhttp://h/1\r\n")
        assertEquals(1, r.channels.size)
        assertEquals("One", r.channels[0].name)
        assertEquals("http://h/1", r.channels[0].url)
    }

    @Test fun emptyAndInvalidInput() {
        val empty = parse("")
        assertTrue(empty.channels.isEmpty())
        assertFalse(empty.isPlaylist)
        val html = parse("<html><body>Not found</body></html>")
        assertFalse(html.isPlaylist)
        val headerOnly = parse("#EXTM3U\n")
        assertTrue(headerOnly.isPlaylist)
        assertTrue(headerOnly.channels.isEmpty())
    }

    @Test fun infoWithoutUrlDoesNotLeakIntoNextEntry() {
        val r = parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"a\",A\nrtmp://x/a\n#EXTINF:-1,B\nhttp://h/b\n")
        assertEquals(1, r.channels.size)
        assertEquals("B", r.channels[0].name)
        assertNull(r.channels[0].tvgId)
    }

    @Test fun attributeParsingIsCaseInsensitiveOnKeysAndFirstWins() {
        val a = parse("#EXTM3U\n#EXTINF:-1 TVG-ID=\"x\" tvg-id=\"y\",N\nhttp://h/n\n").channels[0]
        assertEquals("x", a.tvgId)
    }
}
