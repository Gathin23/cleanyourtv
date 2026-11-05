package io.github.maxhayim.tvhub

import io.github.maxhayim.tvhub.model.RawChannel
import io.github.maxhayim.tvhub.parse.ChannelMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMergerTest {
    private fun raw(src: Long, name: String, url: String, tvg: String? = null, num: String? = null, logo: String? = null, tvgName: String? = null) =
        RawChannel(src, "S$src", name, num, tvg, tvgName, logo, null, url)

    @Test fun mergesSameTvgIdAcrossSourcesKeepingBothStreams() {
        val out = ChannelMerger.merge(listOf(raw(1, "News", "http://a/1", "news", "2"), raw(2, "News HD", "http://b/1", "NEWS", logo = "http://l")), true)
        assertEquals(1, out.size)
        val c = out[0]
        assertEquals("tvg:news", c.key)
        assertEquals("News", c.name)
        assertEquals(listOf("http://a/1", "http://b/1"), c.streams.map { it.url })
        assertEquals(listOf("S1", "S2"), c.sourceNames)
        assertEquals("http://l", c.logo)
        assertEquals(listOf("news", "NEWS"), c.epgIds)
    }

    @Test fun keepsSourcesSeparateWhenMergingIsOff() {
        val out = ChannelMerger.merge(listOf(raw(1, "News", "http://a/1", "news"), raw(2, "News", "http://b/1", "news")), false)
        assertEquals(2, out.size)
        assertNotEquals(out[0].key, out[1].key)
    }

    @Test fun neverMergesByNameAlone() {
        val out = ChannelMerger.merge(listOf(raw(1, "News", "http://a/1"), raw(2, "News", "http://b/1")), true)
        assertEquals(2, out.size)
    }

    @Test fun duplicateNamesInOneSourceGetStableDistinctKeys() {
        val list = listOf(raw(1, "Live", "http://a/1"), raw(1, "Live", "http://a/2"))
        val a = ChannelMerger.merge(list, true).map { it.key }
        val b = ChannelMerger.merge(list, true).map { it.key }
        assertEquals(2, a.toSet().size)
        assertEquals(a, b)
    }

    @Test fun sameUrlTwiceIsOneStream() {
        val out = ChannelMerger.merge(listOf(raw(1, "N", "http://a/1", "n"), raw(2, "N", "http://a/1", "n")), true)
        assertEquals(1, out.single().streams.size)
    }

    @Test fun guideIdByDisplayNameForChannelsWithoutTvgId() {
        val out = ChannelMerger.merge(listOf(raw(1, "Movies Channel", "http://a/1"), raw(1, "Other", "http://a/2")), true, mapOf("movieschannel" to "movies.example"))
        assertEquals(listOf("movies.example"), out.first { it.name == "Movies Channel" }.epgIds)
        assertTrue(out.first { it.name == "Other" }.epgIds.isEmpty())
    }

    @Test fun ordersByNumberThenName() {
        val out = ChannelMerger.merge(listOf(
            raw(1, "Zed", "http://a/1"), raw(1, "Twelve", "http://a/2", num = "12"), raw(1, "Four ten", "http://a/3", num = "4.10"),
            raw(1, "Four one", "http://a/4", num = "4.1"), raw(1, "Alpha", "http://a/5"),
        ), true)
        assertEquals(listOf("Four one", "Four ten", "Twelve", "Alpha", "Zed"), out.map { it.name })
    }

    @Test fun emptyInput() { assertTrue(ChannelMerger.merge(emptyList(), true).isEmpty()) }
}
