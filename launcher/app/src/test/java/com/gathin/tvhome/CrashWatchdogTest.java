package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the safe-mode decision: window, pruning, cap and clock changes. */
public class CrashWatchdogTest {
    private static final long MIN = 60 * 1000L;
    private static final long NOW = 1_000_000_000L;

    @Test
    public void threeCrashesInFiveMinutesTriggers() {
        assertTrue(CrashWatchdog.safeMode(Arrays.asList(NOW - 4 * MIN, NOW - 2 * MIN, NOW - MIN), NOW));
    }

    @Test
    public void twoCrashesDoNot() {
        assertFalse(CrashWatchdog.safeMode(Arrays.asList(NOW - 2 * MIN, NOW - MIN), NOW));
    }

    @Test
    public void crashesOutsideTheWindowDoNotCount() {
        assertFalse(CrashWatchdog.safeMode(Arrays.asList(NOW - 9 * MIN, NOW - 2 * MIN, NOW - MIN), NOW));
        assertEquals(2, CrashWatchdog.prune(Arrays.asList(NOW - 9 * MIN, NOW - 2 * MIN, NOW - MIN), NOW).size());
    }

    @Test
    public void safeModeEndsByItselfAfterTheWindow() {
        List<Long> crashes = Arrays.asList(NOW - 4 * MIN, NOW - 3 * MIN, NOW - 2 * MIN);
        assertTrue(CrashWatchdog.safeMode(crashes, NOW));
        assertFalse(CrashWatchdog.safeMode(crashes, NOW + 4 * MIN));
    }

    @Test
    public void recordPrunesOldAndCapsTheList() {
        List<Long> list = Collections.emptyList();
        for (int i = 0; i < 8; i++) list = CrashWatchdog.record(list, NOW + i * 1000L);
        assertEquals(CrashWatchdog.CAP, list.size());
        assertEquals(Long.valueOf(NOW + 7000L), list.get(list.size() - 1));
        assertEquals(Long.valueOf(NOW + 3000L), list.get(0));
        List<Long> fresh = CrashWatchdog.record(Collections.singletonList(NOW - 20 * MIN), NOW);
        assertEquals(Collections.singletonList(NOW), fresh);
    }

    @Test
    public void clockSetBackDropsFutureEntries() {
        assertFalse(CrashWatchdog.safeMode(Arrays.asList(NOW + MIN, NOW + 2 * MIN, NOW + 3 * MIN), NOW));
        assertEquals(Collections.singletonList(NOW), CrashWatchdog.record(Arrays.asList(NOW + MIN, NOW + 2 * MIN), NOW));
    }

    @Test
    public void storedTextRoundTripsAndToleratesJunk() {
        assertEquals(Arrays.asList(1L, 2L, 3L), CrashWatchdog.parse(CrashWatchdog.format(Arrays.asList(1L, 2L, 3L))));
        assertEquals(Arrays.asList(5L, 7L), CrashWatchdog.parse("5,x,,7"));
        assertTrue(CrashWatchdog.parse(null).isEmpty());
        assertEquals("", CrashWatchdog.format(Collections.<Long>emptyList()));
    }
}
