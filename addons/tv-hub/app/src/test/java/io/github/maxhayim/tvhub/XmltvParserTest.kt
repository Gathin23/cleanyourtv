package io.github.maxhayim.tvhub

import io.github.maxhayim.tvhub.model.Programme
import io.github.maxhayim.tvhub.parse.XmltvParser
import io.github.maxhayim.tvhub.parse.XmltvTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

class XmltvParserTest {
    private val t1800 = XmltvTime.parse("20261022180000 +0000")!!

    private fun run(wanted: Set<String>, names: Set<String> = emptySet(), from: Long = t1800 - 86_400_000, to: Long = t1800 + 2 * 86_400_000L): Pair<XmltvParser, List<Programme>> {
        val p = KXmlParser()
        p.setInput(javaClass.getResourceAsStream("/sample.xml"), null)
        val x = XmltvParser(wanted, names, from, to)
        val out = ArrayList<Programme>()
        x.parse(p) { out += it }
        return x to out
    }

    @Test fun timesWithOffsetsAndWithout() {
        assertEquals(1792692000000L, t1800) // 2026-10-22 18:00 UTC
        assertEquals(t1800 - 2 * 3_600_000, XmltvTime.parse("20261022180000 +0200"))
        assertEquals(t1800 + 5 * 3_600_000 + 30 * 60_000, XmltvTime.parse("20261022180000 -0530"))
        assertEquals(t1800, XmltvTime.parse("20261022180000"))
        assertEquals(t1800, XmltvTime.parse("202610221800"))
        assertEquals(t1800 + 60 * 60_000, XmltvTime.parse("20261022180000 +01:00".replace("+01:00", "-01:00")))
        assertNull(XmltvTime.parse("garbage"))
        assertNull(XmltvTime.parse("20261322180000 +0000"))
        assertNull(XmltvTime.parse(null))
    }

    @Test fun keepsOnlyWantedChannelsInTheWindow() {
        val (x, list) = run(setOf("news.example", "sport.example"))
        assertEquals(listOf("Evening News", "Weather", "Match Day"), list.map { it.title })
        assertEquals(4, x.channelCount)
    }

    @Test fun readsSubtitleDescriptionCategoryAndEntities() {
        val news = run(setOf("news.example")).second.first()
        assertEquals("Headlines", news.subtitle)
        assertEquals("The day's news & weather.", news.description)
        assertEquals("News", news.category)
        assertEquals(t1800, news.start)
        assertEquals(t1800 + 3_600_000, news.stop)
    }

    @Test fun offsetIsAppliedToProgrammeTimes() {
        val sport = run(setOf("sport.example")).second.single()
        assertEquals(t1800 - 2 * 3_600_000, sport.start)
    }

    @Test fun matchesChannelsWithoutIdByDisplayName() {
        val (x, list) = run(emptySet(), names = setOf(XmltvParser.normalise("Movies Channel")))
        assertEquals("movies.example", x.nameMatches["movieschannel"])
        assertEquals(listOf("Feature Film"), list.map { it.title })
    }

    @Test fun windowExcludesPastAndFarFuture() {
        val (_, list) = run(setOf("news.example"), from = t1800 + 3_600_000, to = t1800 + 2 * 3_600_000)
        assertEquals(listOf("Weather"), list.map { it.title })
    }

    @Test fun badTimesAreSkipped() {
        val titles = run(setOf("news.example"), to = t1800 + 30 * 86_400_000L).second.map { it.title }
        assertTrue("Bad time" !in titles)
        assertTrue("Stop before start" !in titles)
        assertTrue("Too far ahead" in titles)
    }

    @Test fun emptyGuide() {
        val p = KXmlParser(); p.setInput("<tv></tv>".reader())
        val x = XmltvParser(setOf("a"), emptySet(), 0, Long.MAX_VALUE)
        var n = 0
        x.parse(p) { n++ }
        assertEquals(0, n); assertEquals(0, x.channelCount)
    }
}
