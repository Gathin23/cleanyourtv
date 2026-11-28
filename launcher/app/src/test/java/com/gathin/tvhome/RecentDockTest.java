package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/** Plain JVM tests for dock_order "recent": which apps the dock holds, and in which order. */
public class RecentDockTest {

    private static final List<String> DOCK = Arrays.asList("smarttube", "prime", "arte", "ard");
    private static final Set<String> INSTALLED =
            new HashSet<>(Arrays.asList("smarttube", "prime", "arte", "ard", "kodi", "netflix", "zdf"));

    private static Map<String, Long> opened(Object... pkgAndTime) {
        Map<String, Long> m = new HashMap<>();
        for (int i = 0; i < pkgAndTime.length; i += 2) m.put((String) pkgAndTime[i], (Long) pkgAndTime[i + 1]);
        return m;
    }

    @Test
    public void nothingOpenedKeepsDockApps() {
        assertEquals(DOCK, RecentDock.order(DOCK, Collections.emptyMap(), INSTALLED, 4));
    }

    @Test
    public void newestFirstThenDockAppsFillTheRest() {
        assertEquals(Arrays.asList("kodi", "arte", "smarttube", "prime"),
                RecentDock.order(DOCK, opened("arte", 200L, "kodi", 300L), INSTALLED, 4));
    }

    @Test
    public void leastRecentDropsOutWhenTheDockIsFull() {
        assertEquals(Arrays.asList("zdf", "netflix", "kodi", "ard"),
                RecentDock.order(DOCK, opened("ard", 10L, "kodi", 20L, "netflix", 30L, "zdf", 40L, "prime", 5L),
                        INSTALLED, 4));
    }

    @Test
    public void uninstalledAppsTakeNoSlot() {
        Set<String> installed = new HashSet<>(Arrays.asList("prime", "arte", "ard", "kodi"));
        assertEquals(Arrays.asList("prime", "arte", "ard"),
                RecentDock.order(DOCK, opened("gone", 99L), installed, 4));
    }

    @Test
    public void tiesKeepTheDockAppsOrder() {
        assertEquals(Arrays.asList("prime", "ard", "smarttube", "arte"),
                RecentDock.order(DOCK, opened("ard", 7L, "prime", 7L), INSTALLED, 4));
    }

    @Test
    public void noSlotsNoDock() {
        assertEquals(Collections.emptyList(), RecentDock.order(DOCK, opened("kodi", 1L), INSTALLED, 0));
    }
}
