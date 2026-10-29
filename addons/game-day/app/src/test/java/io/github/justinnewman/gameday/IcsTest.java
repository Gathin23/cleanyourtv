package io.github.justinnewman.gameday;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.Test;

public class IcsTest {
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final Instant FROM = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2028-01-01T00:00:00Z");

    private static String calendar(String... eventLines) {
        StringBuilder b = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\n");
        for (String l : eventLines) b.append(l).append("\r\n");
        return b.append("END:VEVENT\r\nEND:VCALENDAR\r\n").toString();
    }

    private static List<Ics.Event> parse(String text, ZoneId zone, Instant from, Instant to) throws Exception {
        return Ics.parse(new StringReader(text), zone, from, to);
    }

    @Test public void readsStanzaEvents() throws Exception {
        try (Reader r = new InputStreamReader(getClass().getResourceAsStream("/stanza-sample.ics"), StandardCharsets.UTF_8)) {
            List<Ics.Event> events = Ics.parse(r, UTC, FROM, TO);
            assertEquals(5, events.size());
            Ics.Event first = events.get(0);
            assertEquals("🏒 Dallas Stars @ Minnesota Wild", first.title);
            assertEquals(Instant.parse("2027-04-03T00:00:00Z"), first.start);
            assertEquals(Instant.parse("2027-04-03T03:00:00Z"), first.end);
            assertFalse(first.allDay);
            assertEquals("🏒 Colorado Avalanche @ Minnesota Wild", events.get(1).title);
        }
    }

    @Test public void unfoldsLongLines() throws Exception {
        List<Ics.Event> e = parse(calendar("DTSTART:20270403T000000Z", "SUMMARY:🏒 Minnesota Wild @ Dal", " las Stars"), UTC, FROM, TO);
        assertEquals("🏒 Minnesota Wild @ Dallas Stars", e.get(0).title);
    }

    @Test public void unescapesText() throws Exception {
        List<Ics.Event> e = parse(calendar("DTSTART:20270403T000000Z", "SUMMARY:Wild\\, Stars\\; and more"), UTC, FROM, TO);
        assertEquals("Wild, Stars; and more", e.get(0).title);
    }

    @Test public void tzidAndFloatingTimes() throws Exception {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        Instant expected = Instant.parse("2026-10-28T00:00:00Z");
        assertEquals(expected, parse(calendar("DTSTART;TZID=America/Denver:20261027T180000", "SUMMARY:A"), UTC, from, to).get(0).start);
        assertEquals(expected, parse(calendar("DTSTART;TZID=\"America/Denver\":20261027T180000", "SUMMARY:A"), UTC, from, to).get(0).start);
        assertEquals(expected, parse(calendar("DTSTART:20261027T180000", "SUMMARY:A"), DENVER, from, to).get(0).start);
        // A time zone name Java does not know falls back to the TV's zone.
        assertEquals(expected, parse(calendar("DTSTART;TZID=Mountain Standard Time:20261027T180000", "SUMMARY:A"), DENVER, from, to).get(0).start);
    }

    @Test public void allDayEvents() throws Exception {
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-01T00:00:00Z");
        Ics.Event one = parse(calendar("DTSTART;VALUE=DATE:20261027", "SUMMARY:Draft"), DENVER, from, to).get(0);
        assertTrue(one.allDay);
        assertEquals(Instant.parse("2026-10-27T06:00:00Z"), one.start);
        assertEquals(Instant.parse("2026-10-28T06:00:00Z"), one.end);
        Ics.Event two = parse(calendar("DTSTART;VALUE=DATE:20261027", "DTEND;VALUE=DATE:20261029", "SUMMARY:Camp"), DENVER, from, to).get(0);
        assertEquals(Instant.parse("2026-10-29T06:00:00Z"), two.end);
    }

    @Test public void cancelledAreSkipped() throws Exception {
        assertTrue(parse(calendar("DTSTART:20270403T000000Z", "STATUS:CANCELLED", "SUMMARY:A"), UTC, FROM, TO).isEmpty());
    }

    @Test public void onlyTheWindowIsKept() throws Exception {
        Instant from = Instant.parse("2027-04-03T12:00:00Z");
        Instant to = Instant.parse("2027-04-10T12:00:00Z");
        assertTrue("ended before the window", parse(calendar("DTSTART:20270403T080000Z", "DTEND:20270403T110000Z", "SUMMARY:A"), UTC, from, to).isEmpty());
        assertTrue("starts at the window's end", parse(calendar("DTSTART:20270410T120000Z", "SUMMARY:A"), UTC, from, to).isEmpty());
        assertEquals("still on at the window's start", 1, parse(calendar("DTSTART:20270403T100000Z", "DTEND:20270403T130000Z", "SUMMARY:A"), UTC, from, to).size());
        // Without an end the schedule decides how long it runs, so a start up to a day back is kept.
        assertEquals(1, parse(calendar("DTSTART:20270403T100000Z", "SUMMARY:A"), UTC, from, to).size());
        assertTrue(parse(calendar("DTSTART:20270401T100000Z", "SUMMARY:A"), UTC, from, to).isEmpty());
    }

    @Test(expected = Ics.NotACalendar.class)
    public void notACalendarIsRefused() throws Exception {
        parse("<!DOCTYPE html><html>Sign in</html>", UTC, FROM, TO);
    }

    @Test(expected = Ics.NotACalendar.class)
    public void emptyBodyIsRefused() throws Exception {
        parse("", UTC, FROM, TO);
    }

    @Test public void crlfAndLfBothWork() throws Exception {
        String crlf = calendar("DTSTART:20270403T000000Z", "SUMMARY:A");
        assertEquals(1, parse(crlf, UTC, FROM, TO).size());
        assertEquals(1, parse(crlf.replace("\r\n", "\n"), UTC, FROM, TO).size());
        assertEquals(1, parse("﻿" + crlf, UTC, FROM, TO).size());
    }

    @Test public void eventWithoutSummaryHasAnEmptyTitle() throws Exception {
        Ics.Event e = parse(calendar("DTSTART:20270403T000000Z"), UTC, FROM, TO).get(0);
        assertNotNull(e.title);
        assertEquals("", e.title);
    }
}
