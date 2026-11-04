package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class RaceWeekendTest {

    // The 2026 United States GP, times as Jolpica gives them (UTC).
    private static final RaceWeekend.Race AUSTIN = new RaceWeekend.Race(18, "United States Grand Prix", "Austin",
            Arrays.asList(
                    // Out of order on purpose: the calendar is sorted by start time.
                    new RaceWeekend.Session("race", Instant.parse("2026-10-25T20:00:00Z")),
                    new RaceWeekend.Session("FP1", Instant.parse("2026-10-23T17:30:00Z")),
                    new RaceWeekend.Session("FP2", Instant.parse("2026-10-23T21:00:00Z")),
                    new RaceWeekend.Session("FP3", Instant.parse("2026-10-24T17:30:00Z")),
                    new RaceWeekend.Session("Qualifying", Instant.parse("2026-10-24T21:00:00Z"))));
    private static final List<RaceWeekend.Race> CALENDAR = Collections.singletonList(AUSTIN);

    @Test
    public void nothingBeforeTheWeekendOpens() {
        assertNull(RaceWeekend.at(CALENDAR, Instant.parse("2026-10-23T05:29:00Z")));
    }

    @Test
    public void opensTwelveHoursBeforeFirstPractice() {
        RaceWeekend.State s = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-23T05:30:00Z"));
        assertSame(AUSTIN, s.race);
        assertEquals("FP1", s.next.name);
        assertEquals(Duration.ofHours(12), s.untilNext);
        assertEquals(RaceWeekend.Status.NEXT, s.statuses.get(0));
        assertEquals(RaceWeekend.Status.LATER, s.statuses.get(4));
    }

    @Test
    public void aSessionOnTrackIsLiveAndTheNextOneIsCountedDown() {
        RaceWeekend.State s = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-24T21:30:00Z"));
        assertEquals("QUALIFYING", s.live.name);
        assertTrue(s.next.isRace());
        assertEquals(Arrays.asList(RaceWeekend.Status.DONE, RaceWeekend.Status.DONE, RaceWeekend.Status.DONE,
                RaceWeekend.Status.LIVE, RaceWeekend.Status.NEXT), s.statuses);
    }

    @Test
    public void theRaceIsLiveForTwoHoursThenFinished() {
        RaceWeekend.State live = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-25T21:59:00Z"));
        assertTrue(live.live.isRace());
        assertNull(live.next);
        RaceWeekend.State done = RaceWeekend.at(CALENDAR, Instant.parse("2026-10-25T22:00:00Z"));
        assertTrue(done.finished());
    }

    @Test
    public void closesFourHoursAfterTheRaceStarts() {
        assertTrue(RaceWeekend.at(CALENDAR, Instant.parse("2026-10-25T23:59:00Z")).finished());
        assertNull(RaceWeekend.at(CALENDAR, Instant.parse("2026-10-26T00:00:00Z")));
    }

    @Test
    public void countdownRoundsUpAndChangesUnits() {
        assertEquals("1 min", RaceWeekend.countdown(Duration.ofSeconds(20)));
        assertEquals("42 min", RaceWeekend.countdown(Duration.ofMinutes(42)));
        assertEquals("2h 14m", RaceWeekend.countdown(Duration.ofMinutes(134)));
        assertEquals("1d 3h", RaceWeekend.countdown(Duration.ofHours(27).plusMinutes(10)));
    }

    @Test
    public void namesForThePanel() {
        assertEquals("United States GP", AUSTIN.shortName());
        assertEquals("QUALI", AUSTIN.sessions.get(3).shortName());
        assertEquals("Qualifying", AUSTIN.sessions.get(3).title());
        assertEquals("SQ", new RaceWeekend.Session("sprint quali", Instant.EPOCH).shortName());
    }
}
