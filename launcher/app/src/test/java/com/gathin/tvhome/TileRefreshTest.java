package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** When Home rebuilds its tiles and when it only puts the focus back. */
public class TileRefreshTest {

    @Test
    public void firstResumeBuildsTheTiles() {
        TileRefresh r = new TileRefresh();
        r.onStart();
        assertEquals(TileRefresh.Action.REBUILD, r.onResume(false));
    }

    @Test
    public void backFromAnAppWithNothingChangedOnlyRefocuses() {
        TileRefresh r = shown();
        r.onStop();
        r.onStart();
        assertEquals(TileRefresh.Action.REFOCUS, r.onResume(false));
    }

    @Test
    public void anAppChangedInTheBackgroundRebuildsOnceOnReturn() {
        TileRefresh r = shown();
        r.onStop();
        assertFalse(r.onPackagesChanged());
        assertFalse(r.onPackagesChanged());
        r.onStart();
        assertEquals(TileRefresh.Action.REBUILD, r.onResume(false));
        r.onStop();
        r.onStart();
        assertEquals(TileRefresh.Action.REFOCUS, r.onResume(false));
    }

    @Test
    public void aMissedBroadcastStillRebuilds() {
        TileRefresh r = shown();
        r.onStop();
        r.onStart();
        assertEquals(TileRefresh.Action.REBUILD, r.onResume(true));
    }

    @Test
    public void anAppChangedWhileHomeIsShownRebuildsAtOnce() {
        TileRefresh r = shown();
        assertTrue(r.onPackagesChanged());
        assertEquals(TileRefresh.Action.NOTHING, r.onResume(false));
    }

    @Test
    public void homeOnHomeGoesBackToTheFirstTileWithoutRebuilding() {
        TileRefresh r = shown();
        r.onHomeAgain();
        assertEquals(TileRefresh.Action.REFOCUS, r.onResume(false));
    }

    @Test
    public void homePressedInAnAppRefocusesWithoutRebuilding() {
        // onNewIntent calls onHomeAgain only while Home is stopped; on screen (#552) it just focuses the first tile.
        TileRefresh r = shown();
        r.onStop();
        r.onHomeAgain();
        r.onStart();
        assertEquals(TileRefresh.Action.REFOCUS, r.onResume(false));
    }

    @Test
    public void tilesDroppedForMemoryAreRebuiltOnReturn() {
        TileRefresh r = shown();
        r.onStop();
        r.invalidate();
        r.onStart();
        assertEquals(TileRefresh.Action.REBUILD, r.onResume(false));
    }

    @Test
    public void pauseWithoutStopChangesNothing() {
        TileRefresh r = shown();
        assertEquals(TileRefresh.Action.NOTHING, r.onResume(false));
    }

    private static TileRefresh shown() {
        TileRefresh r = new TileRefresh();
        r.onStart();
        r.onResume(false);
        return r;
    }
}
