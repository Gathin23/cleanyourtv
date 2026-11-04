package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the saved tile order: file format, dock_apps reset and the sort step. */
public class OrderTest {

    private static final List<String> DOCK = Arrays.asList("com.plexapp.android", "com.netflix.ninja");

    @Test
    public void roundTrips() {
        Order o = new Order(Order.dockKey(DOCK), Arrays.asList("b/.B", "a/.A", "c/.C"));
        Order back = Order.decode(Order.encode(o));
        assertEquals(o.dock, back.dock);
        assertEquals(o.keys, back.keys);
    }

    @Test
    public void emptyOrderRoundTrips() {
        Order back = Order.decode(Order.encode(new Order("", Collections.emptyList())));
        assertEquals("", back.dock);
        assertTrue(back.keys.isEmpty());
    }

    @Test
    public void rejectsWhatIsNotAnOrder() {
        assertNull(Order.decode(null));
        assertNull(Order.decode(""));
        assertNull(Order.decode("{\"version\":2}"));
        assertNull(Order.decode("tvhome-order 2\ndock x\na/.A\n"));
        assertNull(Order.decode("tvhome-order 1\na/.A\n"));                 // no dock line
        assertNull(Order.decode("tvhome-order 1\ndock x\na/.A\n\nb/.B\n"));  // blank key in the middle
        assertNull(Order.decode("tvhome-order 1\ndock x\na/.A\na/.A\n"));    // duplicate key
    }

    @Test
    public void ranksFollowTheSavedOrder() {
        Order o = new Order(Order.dockKey(DOCK), Arrays.asList("b/.B", "a/.A"));
        Map<String, Integer> r = Order.ranks(o, DOCK);
        assertEquals(Integer.valueOf(0), r.get("b/.B"));
        assertEquals(Integer.valueOf(1), r.get("a/.A"));
    }

    @Test
    public void changedDockAppsDropTheSavedOrder() {
        Order o = new Order(Order.dockKey(DOCK), Arrays.asList("b/.B", "a/.A"));
        assertTrue(Order.ranks(o, Arrays.asList("com.netflix.ninja", "com.plexapp.android")).isEmpty());
        assertTrue(Order.ranks(o, Collections.singletonList("com.plexapp.android")).isEmpty());
        assertTrue(Order.ranks(null, DOCK).isEmpty());
    }

    @Test
    public void savedTilesComeFirstAndNewOnesKeepTheirOrderAfter() {
        Order o = new Order(Order.dockKey(DOCK), Arrays.asList("c/.C", "a/.A", "gone/.Gone"));
        Map<String, Integer> r = Order.ranks(o, DOCK);
        // "n" and "m" are new installs: unranked, so the usual rule (here: list order) decides between them.
        List<String> tiles = new ArrayList<>(Arrays.asList("m/.M", "a/.A", "n/.N", "c/.C"));
        List<String> usual = new ArrayList<>(tiles);
        tiles.sort((x, y) -> {
            int c = Order.compare(r, x, y);
            return c != 0 ? c : Integer.compare(usual.indexOf(x), usual.indexOf(y));
        });
        assertEquals(Arrays.asList("c/.C", "a/.A", "m/.M", "n/.N"), tiles);
    }
}
