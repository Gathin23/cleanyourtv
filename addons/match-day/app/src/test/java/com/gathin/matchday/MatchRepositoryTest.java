package com.gathin.matchday;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MatchRepositoryTest {
    @Test public void providerCachesAreIsolatedAndFootballDataNamesStayCompatible() {
        LocalDate day = LocalDate.of(2026, 11, 7);
        assertEquals("2026-11-07.json", MatchRepository.cacheFileName(day, Competition.PREMIER_LEAGUE,
                DataProvider.FOOTBALL_DATA));
        assertEquals("sportscore-2026-11-07.json", MatchRepository.cacheFileName(day,
                Competition.PREMIER_LEAGUE, DataProvider.SPORT_SCORE));
        assertEquals("CL-2026-11-07.json", MatchRepository.cacheFileName(day, Competition.CHAMPIONS_LEAGUE,
                DataProvider.FOOTBALL_DATA));
        assertEquals("sportscore-CL-2026-11-07.json", MatchRepository.cacheFileName(day,
                Competition.CHAMPIONS_LEAGUE, DataProvider.SPORT_SCORE));
    }

    @Test public void rejectsCacheEntriesFromThePreviousScheduleVersion() throws Exception {
        String oldCache = """
                {"savedAt":1000,"payload":{"matches":[]}}
                """;

        assertNull(MatchRepository.parseCache(oldCache, LocalDate.of(2026, 11, 7), ZoneId.of("UTC")));
    }

    @Test public void readsCurrentVersionedCacheEntries() throws Exception {
        String currentCache = """
                {"version":2,"savedAt":1000,"payload":{"matches":[]}}
                """;

        MatchRepository.Result result = MatchRepository.parseCache(currentCache,
                LocalDate.of(2026, 11, 7), ZoneId.of("UTC"));

        assertEquals(1000L, result.updatedAt);
        assertEquals(0, result.matches.size());
        assertTrue(result.fromCache);
    }

    @Test public void parsesSportScoreCacheWithItsOwnProviderParser() throws Exception {
        String cache = """
                {"version":2,"savedAt":2000,"payload":{"matches":[
                  {"time":"2026-11-07T12:00:00Z","status":"finished","home":"A","away":"B",
                   "home_score":"1","away_score":"0"}
                ]}}
                """;
        MatchRepository.Result result = MatchRepository.parseCache(cache, LocalDate.of(2026, 11, 7),
                ZoneId.of("UTC"), DataProvider.SPORT_SCORE);
        assertEquals(2000L, result.updatedAt);
        assertEquals(1, result.matches.size());
        assertEquals("A", result.matches.get(0).home.name);
        assertTrue(result.fromCache);
    }
}
