package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the top shelf's Continue watching cards: which Watch Next rows survive, and their order. */
public class WatchNextShelfTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static WatchNextShelf.Item row(String pkg, String title, String intent, int type, long engaged,
                                           long position, long duration) {
        return new WatchNextShelf.Item(pkg, title, null, null, null, "https://x/p.jpg", null, intent, type,
                engaged, position, duration, 0, null);
    }

    private static List<WatchNextShelf.Item> rows(WatchNextShelf.Item... items) {
        List<WatchNextShelf.Item> list = new ArrayList<>();
        for (WatchNextShelf.Item i : items) list.add(i);
        return list;
    }

    @Test
    public void aRowWithoutADeepLinkIsDropped() {
        Map<String, List<WatchNextShelf.Item>> shelf = WatchNextShelf.select(rows(
                row("a", "No link", null, 1, NOW, -1, -1),
                row("a", "Empty link", " ", 1, NOW, -1, -1),
                row("a", "Linked", "intent:#Intent;end", 1, NOW, -1, -1)), NOW);
        assertEquals(1, shelf.get("a").size());
        assertEquals("Linked", shelf.get("a").get(0).title);
    }

    @Test
    public void aRowLastEngagedOverSixtyDaysAgoIsDropped() {
        Map<String, List<WatchNextShelf.Item>> shelf = WatchNextShelf.select(rows(
                row("a", "Old", "intent:#Intent;end", 1, NOW - 61 * DAY, -1, -1),
                row("a", "Fresh", "intent:#Intent;end", 1, NOW - 59 * DAY, -1, -1),
                row("a", "Never dated", "intent:#Intent;end", 1, -1, -1, -1)), NOW);
        assertEquals(2, shelf.get("a").size());
        assertEquals("Fresh", shelf.get("a").get(0).title);
        assertEquals("Never dated", shelf.get("a").get(1).title);
    }

    @Test
    public void aContinueRowAtNinetyFivePercentIsDroppedButANextRowIsNot() {
        Map<String, List<WatchNextShelf.Item>> shelf = WatchNextShelf.select(rows(
                row("a", "Done", "intent:#Intent;end", 0, NOW, 95, 100),
                row("a", "Nearly", "intent:#Intent;end", 0, NOW - 1, 94, 100),
                row("a", "Next", "intent:#Intent;end", 1, NOW - 2, 99, 100)), NOW);
        assertEquals(2, shelf.get("a").size());
        assertEquals("Nearly", shelf.get("a").get(0).title);
        assertEquals("Next", shelf.get("a").get(1).title);
    }

    @Test
    public void rowsGroupByAppNewestFirstAndStopAtEight() {
        List<WatchNextShelf.Item> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) all.add(row("a", "A" + i, "intent:#Intent;end", 1, NOW - i, -1, -1));
        all.add(row("b", "B newest", "intent:#Intent;end", 1, NOW + 5, -1, -1));
        Map<String, List<WatchNextShelf.Item>> shelf = WatchNextShelf.select(all, NOW + 10);
        assertEquals(8, shelf.get("a").size());
        assertEquals("A0", shelf.get("a").get(0).title);
        assertEquals("A7", shelf.get("a").get(7).title);
        assertEquals("B newest", shelf.get("b").get(0).title);
        assertEquals("b", shelf.keySet().iterator().next());
    }

    @Test
    public void aRowWithNoTitleAtAllIsDropped() {
        assertTrue(WatchNextShelf.select(rows(row("a", null, "intent:#Intent;end", 1, NOW, -1, -1)), NOW).isEmpty());
    }

    @Test
    public void progressIsOnlyKnownWithBothNumbers() {
        assertEquals(0.5f, row("a", "t", "i", 0, NOW, 50, 100).progress(), 0.0001f);
        assertEquals(-1f, row("a", "t", "i", 0, NOW, -1, 100).progress(), 0f);
        assertEquals(-1f, row("a", "t", "i", 0, NOW, 50, 0).progress(), 0f);
        assertEquals(1f, row("a", "t", "i", 0, NOW, 150, 100).progress(), 0f);
    }

    @Test
    public void anEpisodeShowsItsNumbersThenItsOwnTitle() {
        WatchNextShelf.Item both = new WatchNextShelf.Item("a", "Show", "Pilot", "1", "4", null, null, "i", 0,
                NOW, -1, -1, 0, null);
        assertEquals("S1 · E4", both.episodeLabel());
        assertEquals("S1 · E4 · Pilot", both.subtitle());
        WatchNextShelf.Item plain = new WatchNextShelf.Item("a", "Show", null, "2", "10", null, null, "i", 0,
                NOW, -1, -1, 0, null);
        assertEquals("S2 · E10", plain.subtitle());
        WatchNextShelf.Item film = row("a", "Film", "i", 0, NOW, -1, -1);
        assertNull(film.episodeLabel());
        assertNull(film.subtitle());
    }

    @Test
    public void browseModeTextCombinesTypeNumbersAndDescription() {
        WatchNextShelf.Item series = new WatchNextShelf.Item("a", "Show", "Pilot", "2", "132", null, null, "i", 0,
                NOW, -1, -1, 1, "A long night.");
        assertEquals("Series \u00B7 S2 \u00B7 E132", series.meta());
        assertEquals("Pilot\nA long night.", series.detail());
        WatchNextShelf.Item film = new WatchNextShelf.Item("a", "Film", null, null, null, null, null, "i", 0,
                NOW, -1, -1, 0, null);
        assertEquals("Movie", film.meta());
        assertNull(film.detail());
    }

    @Test
    public void theArtworkIsThePosterElseTheThumbnail() {
        WatchNextShelf.Item thumbOnly = new WatchNextShelf.Item("a", "t", null, null, null, "", "https://x/t.jpg",
                "i", 0, NOW, -1, -1, 0, null);
        assertEquals("https://x/t.jpg", thumbOnly.art());
        assertEquals("https://x/p.jpg", row("a", "t", "i", 0, NOW, -1, -1).art());
        assertNull(new WatchNextShelf.Item("a", "t", null, null, null, null, null, "i", 0, NOW, -1, -1, 0, null).art());
    }
}
