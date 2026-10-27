package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for dock_most_used: the ranking and the 7-day window of launches. */
public class MostUsedTest {

    private static final List<String> DOCK = Arrays.asList("com.tcl.tv", "in.startv.hotstar", "com.suntv.sunnxt");

    @Test
    public void noLaunchesYetKeepsDockApps() {
        assertEquals(DOCK, MostUsed.rank(Collections.emptyMap(), DOCK, 3));
    }

    @Test
    public void mostOpenedComeFirstThenDockAppsFillTheRest() {
        Map<String, Float> scores = new HashMap<>();
        scores.put("org.smarttube.stable", 5f);
        scores.put("in.startv.hotstar", 9f);
        assertEquals(Arrays.asList("in.startv.hotstar", "org.smarttube.stable", "com.tcl.tv"),
                MostUsed.rank(scores, DOCK, 3));
    }

    @Test
    public void tiesKeepTheDockAppsOrder() {
        Map<String, Float> scores = new HashMap<>();
        scores.put("com.suntv.sunnxt", 2f);
        scores.put("com.tcl.tv", 2f);
        assertEquals(Arrays.asList("com.tcl.tv", "com.suntv.sunnxt"), MostUsed.rank(scores, DOCK, 2));
    }

    @Test
    public void rankingIsCutToTheDockSize() {
        Map<String, Float> scores = new HashMap<>();
        scores.put("a.a", 3f);
        scores.put("b.b", 2f);
        scores.put("c.c", 1f);
        assertEquals(Arrays.asList("a.a", "b.b"), MostUsed.rank(scores, DOCK, 2));
    }

    @Test
    public void onlyTheLastSevenDaysCount() {
        long today = 20_000;
        Map<String, Object> stored = new HashMap<>();
        stored.put(MostUsed.countKey(today, "a.a"), 2);
        stored.put(MostUsed.countKey(today - 6, "a.a"), 3);                 // oldest day still in the window
        stored.put(MostUsed.countKey(today - MostUsed.WINDOW_DAYS, "a.a"), 50);   // a week ago: out
        stored.put(MostUsed.countKey(today - 1, "b.b"), 4);
        stored.put("count:junk", 1);
        Map<String, Float> counts = MostUsed.counts(stored, today);
        assertEquals(2, counts.size());
        assertEquals(5f, counts.get("a.a"), 0f);
        assertEquals(4f, counts.get("b.b"), 0f);
    }

    @Test
    public void aPackageNameWithColonsStaysWhole() {
        Map<String, Object> stored = new HashMap<>();
        stored.put(MostUsed.countKey(5, "a.b:c"), 1);
        assertTrue(MostUsed.counts(stored, 5).containsKey("a.b:c"));
    }

    @Test
    public void theRankingChangesEveryEightHours() {
        java.time.LocalDateTime midnight = java.time.LocalDateTime.of(2026, 10, 27, 0, 0);
        long first = MostUsed.period(midnight);
        assertEquals(first, MostUsed.period(midnight.plusHours(7).plusMinutes(59)));
        assertEquals(first + 1, MostUsed.period(midnight.plusHours(8)));
        assertEquals(first + 2, MostUsed.period(midnight.plusHours(16)));
        assertEquals(first + 3, MostUsed.period(midnight.plusDays(1)));
    }
}
