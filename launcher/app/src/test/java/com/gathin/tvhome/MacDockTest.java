package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MacDockTest {
    @Test
    public void macDockTilesAreSquareAndOtherTilesKeepTheBannerShape() {
        assertEquals(200, AppAdapter.tileHeightPx(200, true));
        assertEquals(AppAdapter.tileHeightPx(200), AppAdapter.tileHeightPx(200, false));
    }

    @Test
    public void theFocusedTileDoesNotMove() {
        assertEquals(0f, AppAdapter.dockShift(0, 200), 0.001f);
    }

    @Test
    public void neighboursMoveJustFarEnoughNotToOverlap() {
        int w = 200;
        float[] m = AppAdapter.DOCK_MAGNIFY;
        for (int d = 1; d <= m.length + 1; d++) {
            float inner = d - 1 < m.length ? m[d - 1] : 1f;
            float outer = d < m.length ? m[d] : 1f;
            // The gap the shift closes between neighbours d-1 and d: half of each one's growth.
            float needed = (inner - 1f) * w / 2f + (outer - 1f) * w / 2f;
            float moved = AppAdapter.dockShift(d, w) - AppAdapter.dockShift(d - 1, w);
            assertEquals("tile " + d, needed, moved, 0.001f);
        }
    }

    @Test
    public void tilesPastTheMagnifiedOnesAllMoveTheSameDistance() {
        int w = 200;
        int far = AppAdapter.DOCK_MAGNIFY.length;
        assertEquals(AppAdapter.dockShift(far, w), AppAdapter.dockShift(far + 3, w), 0.001f);
    }
}
