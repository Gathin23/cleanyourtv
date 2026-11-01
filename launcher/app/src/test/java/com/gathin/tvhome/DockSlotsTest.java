package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DockSlotsTest {
    @Test public void dockRowIsTheFirstColumnsTilesOrEveryTileOfASingleRow() {
        // #2428: the glass stays level with a dock tile, which advanced_effects no longer lifts.
        assertTrue(DockSlots.inDockRow(true, false, 0, 6));
        assertTrue(DockSlots.inDockRow(true, false, 5, 6));
        assertFalse(DockSlots.inDockRow(true, false, 6, 6));
        assertTrue(DockSlots.inDockRow(true, true, 40, 6));
        assertFalse(DockSlots.inDockRow(false, false, 0, 6));
        assertFalse(DockSlots.inDockRow(false, true, 0, 6));
    }

    private static Set<String> set(String... keys) {
        return new HashSet<>(Arrays.asList(keys));
    }

    @Test public void anAppCarriedIntoTheDockStaysThere() {
        // #499: Apple TV (not in dock_apps) carried into the 4th slot, YouTube carried out into the grid.
        List<String> keys = Arrays.asList("tivimate", "jellyfin", "iplayer", "appletv", "my5", "youtube");
        Set<String> dock = set("tivimate", "jellyfin", "iplayer", "appletv");
        assertEquals(keys, DockSlots.dockFirst(keys, 4, dock));
        assertEquals(4, DockSlots.dockCount(keys, 4, dock));
    }

    @Test public void aGridAppThatOnlySlidIntoAFreeSlotGoesBackToTheGrid() {
        // #162: three dock apps and a free slot. Reorder mode shows no empty slot, so "my5" sits in the 4th place
        // without the owner putting it there; only a grid tile moved.
        List<String> keys = Arrays.asList("tivimate", "jellyfin", "iplayer", "my5", "vlc", "appletv");
        Set<String> dock = set("tivimate", "jellyfin", "iplayer");
        assertEquals(keys, DockSlots.dockFirst(keys, 4, dock));
        assertEquals(3, DockSlots.dockCount(keys, 4, dock));
    }

    @Test public void aSlidInGridAppAheadOfADockAppMovesBehindTheDock() {
        // "tivimate" carried from the 1st place to the 4th: "my5" slid in at the 3rd and must not split the dock.
        List<String> keys = Arrays.asList("jellyfin", "iplayer", "my5", "tivimate", "vlc");
        Set<String> dock = set("tivimate", "jellyfin", "iplayer");
        assertEquals(Arrays.asList("jellyfin", "iplayer", "tivimate", "my5", "vlc"), DockSlots.dockFirst(keys, 4, dock));
        assertEquals(3, DockSlots.dockCount(keys, 4, dock));
    }

    @Test public void aDockAppPushedPastTheDockLeavesIt() {
        // An app carried into the 1st place pushes the 4th dock app into the grid.
        List<String> keys = Arrays.asList("appletv", "tivimate", "jellyfin", "iplayer", "youtube");
        Set<String> dock = set("appletv", "tivimate", "jellyfin", "iplayer", "youtube");
        assertEquals(keys, DockSlots.dockFirst(keys, 4, dock));
        assertEquals(4, DockSlots.dockCount(keys, 4, dock));
    }

    @Test public void fewerTilesThanDockSlotsStillPutTheDockFirst() {
        List<String> keys = Arrays.asList("a", "b");
        assertEquals(Arrays.asList("b", "a"), DockSlots.dockFirst(keys, 4, set("b")));
        assertEquals(1, DockSlots.dockCount(keys, 4, set("b")));
    }

    @Test public void threeDockAppsInFourColumnsLeaveOneSlotEmpty() {
        assertEquals(1, DockSlots.empty(3, 4, 20, false));
    }

    @Test public void fillDockKeepsTheOldBehaviour() {
        assertEquals(0, DockSlots.empty(3, 4, 20, true));
    }

    @Test public void aFullDockNeedsNoEmptySlot() {
        assertEquals(0, DockSlots.empty(4, 4, 20, false));
        assertEquals(0, DockSlots.empty(6, 6, 20, false));
    }

    @Test public void noInstalledDockAppFillsTheFirstRow() {
        assertEquals(0, DockSlots.empty(0, 4, 20, false));
    }

    @Test public void nothingAfterTheDockNeedsNoEmptySlot() {
        assertEquals(0, DockSlots.empty(2, 4, 2, false));
    }

    @Test public void focusOnAnEmptySlotGoesToTheLastDockApp() {
        assertEquals(2, DockSlots.focusable(3, 3, 1));
        assertEquals(1, DockSlots.focusable(2, 2, 2));
        assertEquals(1, DockSlots.focusable(3, 2, 2));
    }

    @Test public void focusOnATileStaysPut() {
        assertEquals(0, DockSlots.focusable(0, 3, 1));
        assertEquals(2, DockSlots.focusable(2, 3, 1));
        assertEquals(4, DockSlots.focusable(4, 3, 1));
        assertEquals(3, DockSlots.focusable(3, 4, 0));
    }
}
