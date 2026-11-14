package com.gathin.matchday;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MatchRepositoryTest {
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
}
