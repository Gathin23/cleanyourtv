package com.gathin.matchday;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MatchLoadStateTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);

    @Test public void acceptsLeagueResultsProgressively() {
        MatchLoadState load = new MatchLoadState(DAY);
        MatchRepository.Result result = new MatchRepository.Result(
                Collections.singletonList(new Match(Instant.parse("2026-11-07T12:00:00Z"), "FINISHED",
                        new Match.Team(1, "Home", null, null),
                        new Match.Team(2, "Away", null, null), 1, 0)), 1L, false, null);

        assertTrue(load.accept(DAY, Competition.PREMIER_LEAGUE, result));
        assertEquals(result, load.result(Competition.PREMIER_LEAGUE));
        assertEquals(Competition.values().length - 1, load.remaining());
        assertFalse(load.isComplete());
    }

    @Test public void ignoresResultsForADateTheViewerHasLeft() {
        MatchLoadState load = new MatchLoadState(DAY);
        MatchRepository.Result result = MatchRepository.Result.failure("old date");

        assertFalse(load.accept(DAY.plusDays(1), Competition.PREMIER_LEAGUE, result));
        assertNull(load.result(Competition.PREMIER_LEAGUE));
        assertEquals(Competition.values().length - 1, load.remaining());
    }

    @Test public void emptySchedulesAreCompleteOnlyAfterEveryCompetitionResponds() {
        MatchLoadState load = new MatchLoadState(DAY);
        for (Competition competition : Competition.values()) {
            assertFalse(load.isComplete());
            assertTrue(load.accept(DAY, competition,
                    new MatchRepository.Result(Collections.emptyList(), 1L, false, null)));
        }

        assertTrue(load.isComplete());
        assertEquals(0, load.remaining());
        assertEquals(Competition.values().length, load.results().size());
    }

    @Test public void keepsErrorsAssociatedWithTheirCompetition() {
        MatchLoadState load = new MatchLoadState(DAY);
        MatchRepository.Result failure = MatchRepository.Result.failure("HTTP 403");

        assertTrue(load.accept(DAY, Competition.BUNDESLIGA, failure));
        assertEquals("HTTP 403", load.result(Competition.BUNDESLIGA).error);
        assertNull(load.result(Competition.LIGUE_1));
    }
}
