package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LibraryOrderTest {
    @Test public void orderRoundTripsWithPinnedEntries() {
        LibraryOrder order = new LibraryOrder(
                Arrays.asList("com.example.beta", "com.example.alpha"),
                Collections.singleton("com.example.beta"));

        String encoded = LibraryOrder.encode(order);

        assertEquals(order.keys, LibraryOrder.decode(encoded).keys);
        assertEquals(order.pinned, LibraryOrder.decode(encoded).pinned);
    }

    @Test public void pinMovesAnAppToTheFrontAndUnpinKeepsItsPosition() {
        List<String> catalog = Arrays.asList("com.example.alpha", "com.example.beta", "com.example.gamma");
        LibraryOrder order = LibraryOrder.empty().pin("com.example.gamma", catalog);

        assertTrue(order.isPinned("com.example.gamma"));
        assertEquals(Arrays.asList("com.example.gamma", "com.example.alpha", "com.example.beta"),
                order.sort(catalog));

        order = order.unpin("com.example.gamma");
        assertFalse(order.isPinned("com.example.gamma"));
        assertEquals(Arrays.asList("com.example.gamma", "com.example.alpha", "com.example.beta"),
                order.sort(catalog));
    }

    @Test public void movingAnAppSwapsItWithItsNeighbour() {
        List<String> catalog = Arrays.asList("com.example.alpha", "com.example.beta", "com.example.gamma");
        LibraryOrder order = LibraryOrder.empty().move("com.example.gamma", -2, catalog);

        assertEquals(Arrays.asList("com.example.gamma", "com.example.alpha", "com.example.beta"),
                order.sort(catalog));
        assertEquals(Arrays.asList("com.example.alpha", "com.example.beta", "com.example.gamma"),
                order.move("com.example.gamma", 20, catalog).sort(catalog));
    }

    @Test public void pinnedEntriesSortBeforeManualEntriesAndUnorderedEntries() {
        LibraryOrder order = new LibraryOrder(
                Arrays.asList("com.example.beta", "com.example.alpha"),
                Collections.singleton("com.example.beta"));
        List<String> base = Arrays.asList("com.example.gamma", "com.example.alpha", "com.example.beta");

        assertEquals(Arrays.asList("com.example.beta", "com.example.alpha", "com.example.gamma"), order.sort(base));
    }

    @Test public void topBarAppsKeepConfiguredOrderAndAppendPinnedEntries() {
        LibraryOrder order = new LibraryOrder(
                Arrays.asList("com.example.beta", "com.example.gamma", "com.example.alpha"),
                Collections.singleton("com.example.beta"));

        assertEquals(Arrays.asList("com.example.zeta", "com.example.alpha", "com.example.beta"),
                order.topBarApps(Arrays.asList("com.example.zeta", "com.example.alpha")));
    }

    @Test public void topBarAppsDoNotDuplicateConfiguredPinnedEntriesAndFollowReorder() {
        List<String> catalog = Arrays.asList("com.example.alpha", "com.example.beta", "com.example.gamma");
        LibraryOrder order = LibraryOrder.empty().pin("com.example.gamma", catalog).pin("com.example.beta", catalog);

        assertEquals(Arrays.asList("com.example.alpha", "com.example.beta", "com.example.gamma"),
                order.topBarApps(Arrays.asList("com.example.alpha", "com.example.beta")));

        order = order.move("com.example.beta", 1, catalog);
        assertEquals(Arrays.asList("com.example.gamma", "com.example.beta"),
                order.topBarApps(Collections.emptyList()));
        order = order.unpin("com.example.beta");
        assertEquals(Collections.singletonList("com.example.gamma"), order.topBarApps(Collections.emptyList()));
    }

    @Test public void malformedOrderIsRejected() {
        assertEquals(null, LibraryOrder.decode("tvhome-library 1\norder com.example\norder com.example\n"));
        assertEquals(null, LibraryOrder.decode("tvhome-library 1\npinned com.example\n"));
    }
}
