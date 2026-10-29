package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

/** Plain JVM tests for wallpaper_events: which dates an event covers, and the pictures it picks. */
public class WallpaperEventTest {
    private static WallpaperEvent event(String start, String end, String... names) {
        return new WallpaperEvent(WallpaperEvent.date(start), WallpaperEvent.date(end), new HashSet<>(Arrays.asList(names)));
    }

    private static LocalDate day(int year, int month, int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth);
    }

    private static List<File> files(String... names) {
        List<File> out = new ArrayList<>();
        for (String name : names) out.add(new File("/walls", name));
        return out;
    }

    private static List<String> names(List<File> files) {
        List<String> out = new ArrayList<>();
        for (File f : files) out.add(f.getName());
        return out;
    }

    @Test
    public void theEndDateIsIncluded() {
        WallpaperEvent christmas = event("12-20", "12-26");
        assertFalse(christmas.covers(day(2026, 12, 19)));
        assertTrue(christmas.covers(day(2026, 12, 20)));   // the start day
        assertTrue(christmas.covers(day(2026, 12, 25)));
        assertTrue(christmas.covers(day(2026, 12, 26)));   // the end day: still on, all of it
        assertFalse(christmas.covers(day(2026, 12, 27)));
        assertFalse(christmas.covers(day(2026, 7, 4)));
    }

    @Test
    public void aYearlyEventWhoseStartIsAfterItsEndRunsOverNewYear() {
        WallpaperEvent newYear = event("12-31", "01-01");
        assertFalse(newYear.covers(day(2026, 12, 30)));
        assertTrue(newYear.covers(day(2026, 12, 31)));
        assertTrue(newYear.covers(day(2027, 1, 1)));       // the next calendar year, still the same event
        assertFalse(newYear.covers(day(2027, 1, 2)));
        assertFalse(newYear.covers(day(2027, 6, 15)));
        // and a longer one over the turn of the year
        WallpaperEvent holidays = event("12-20", "01-05");
        assertTrue(holidays.covers(day(2026, 12, 28)));
        assertTrue(holidays.covers(day(2027, 1, 5)));
        assertFalse(holidays.covers(day(2027, 1, 6)));
        assertFalse(holidays.covers(day(2026, 12, 19)));
    }

    @Test
    public void aSingleDayEventCoversThatDay() {
        WallpaperEvent day = event("02-14", "02-14");
        assertFalse(day.covers(day(2026, 2, 13)));
        assertTrue(day.covers(day(2026, 2, 14)));
        assertFalse(day.covers(day(2026, 2, 15)));
    }

    @Test
    public void aDatedEventCoversThatYearOnly() {
        WallpaperEvent easter = event("2026-04-03", "2026-04-06");
        assertFalse(easter.covers(day(2026, 4, 2)));
        assertTrue(easter.covers(day(2026, 4, 3)));
        assertTrue(easter.covers(day(2026, 4, 6)));
        assertFalse(easter.covers(day(2026, 4, 7)));
        assertFalse(easter.covers(day(2027, 4, 4)));       // same month and day, another year
        assertFalse(easter.covers(day(2025, 4, 4)));
    }

    @Test
    public void leapDayIsAValidYearlyDate() {
        WallpaperEvent leap = event("02-29", "02-29");
        assertTrue(leap.covers(day(2028, 2, 29)));
        assertFalse(leap.covers(day(2027, 3, 1)));
    }

    @Test
    public void dateReadsMonthDayAndFullDatesAndRejectsTheRest() {
        assertEquals(1220, WallpaperEvent.date("12-20"));
        assertEquals(101, WallpaperEvent.date(" 01-01 "));
        assertEquals(20261220, WallpaperEvent.date("2026-12-20"));
        assertEquals(229, WallpaperEvent.date("02-29"));
        assertEquals(-1, WallpaperEvent.date("02-30"));
        assertEquals(-1, WallpaperEvent.date("13-01"));
        assertEquals(-1, WallpaperEvent.date("00-10"));
        assertEquals(-1, WallpaperEvent.date("2027-02-29"));   // not a leap year
        assertEquals(-1, WallpaperEvent.date("Dec 20"));
        assertEquals(-1, WallpaperEvent.date("12-2"));
        assertEquals(-1, WallpaperEvent.date("12/20"));
        assertEquals(-1, WallpaperEvent.date(""));
    }

    @Test
    public void spanReadsAKeyAsStartAndEnd() {
        assertArrayEquals(new int[]{1220, 1226}, WallpaperEvent.span("12-20 to 12-26"));
        assertArrayEquals(new int[]{1231, 101}, WallpaperEvent.span("  12-31   to   01-01 "));   // over New Year
        assertArrayEquals(new int[]{1225, 1225}, WallpaperEvent.span("12-25"));                    // one date is that day
        assertArrayEquals(new int[]{20260403, 20260406}, WallpaperEvent.span("2026-04-03 to 2026-04-06"));
        assertNull(WallpaperEvent.span(""));
        assertNull(WallpaperEvent.span("christmas"));
        assertNull(WallpaperEvent.span("12-20 to"));
        assertNull(WallpaperEvent.span("to 12-26"));
        assertNull(WallpaperEvent.span("12-20 to 12-26 to 12-27"));
        assertNull(WallpaperEvent.span("12-20 to 2026-12-26"));      // a yearly date and a dated one
        assertNull(WallpaperEvent.span("2026-12-26 to 2026-12-20"));  // a year's dates the wrong way round
        assertNull(WallpaperEvent.span("12-20 to 02-30"));           // a day the calendar does not have
        assertNull(WallpaperEvent.span("12-20..12-26"));
    }

    @Test
    public void todayGivesTheEventsPicturesInTheCollectionsOrder() {
        List<File> all = files("a.jpg", "xmas-2.jpg", "b.jpg", "xmas-1.jpg");
        List<WallpaperEvent> events = Collections.singletonList(event("12-20", "12-26", "xmas-1.jpg", "xmas-2.jpg", "gone.jpg"));
        assertEquals(Arrays.asList("xmas-2.jpg", "xmas-1.jpg"), names(WallpaperEvent.today(all, events, day(2026, 12, 26))));
        assertNull(WallpaperEvent.today(all, events, day(2026, 12, 27)));          // no event that day: the usual rotation
        assertNull(WallpaperEvent.today(all, Collections.emptyList(), day(2026, 12, 25)));
    }

    @Test
    public void anEventWithNoneOfTheCollectionsPicturesChangesNothing() {
        List<File> all = files("a.jpg", "b.jpg");
        List<WallpaperEvent> events = Collections.singletonList(event("12-20", "12-26", "gone.jpg"));
        assertNull(WallpaperEvent.today(all, events, day(2026, 12, 25)));
    }

    @Test
    public void theFirstListedEventWinsWhenTwoOverlap() {
        List<File> all = files("xmas.jpg", "nye.jpg", "a.jpg");
        List<WallpaperEvent> events = Arrays.asList(
                event("12-20", "12-31", "xmas.jpg"),
                event("12-31", "01-01", "nye.jpg"));
        assertEquals(Collections.singletonList("xmas.jpg"), names(WallpaperEvent.today(all, events, day(2026, 12, 31))));
        assertEquals(Collections.singletonList("nye.jpg"), names(WallpaperEvent.today(all, events, day(2027, 1, 1))));
    }

    @Test
    public void eventPicturesStayOutOfTheRestOfTheYear() {
        List<File> all = files("a.jpg", "xmas.jpg", "b.jpg", "nye.jpg");
        List<WallpaperEvent> events = Arrays.asList(event("12-20", "12-26", "xmas.jpg"), event("12-31", "01-01", "nye.jpg"));
        assertEquals(Arrays.asList("a.jpg", "b.jpg"), names(WallpaperEvent.without(all, events)));
        // no events: the very same list, so nothing changes for a theme without them
        assertSame(all, WallpaperEvent.without(all, Collections.emptyList()));
        // a collection made only of event pictures still has something to show
        List<File> onlyEvents = files("xmas.jpg", "nye.jpg");
        assertSame(onlyEvents, WallpaperEvent.without(onlyEvents, events));
    }

    @Test
    public void emptyOrUnreadableTextIsNoEvents() {
        assertTrue(WallpaperEvent.parse("").isEmpty());
        assertTrue(WallpaperEvent.parse("   ").isEmpty());
    }
}
