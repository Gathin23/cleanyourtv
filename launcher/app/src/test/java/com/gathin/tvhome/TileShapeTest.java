package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** tile_shape, tile_corner_radius, tile_label_inside and row_titles: the tile fit and the shapes they change. */
public class TileShapeTest {

    private static final float D = 2f;              // 1080p TV: 2 px per dp
    private static final int INNER = 1696;          // grid width minus padding at 1920 px
    private static final int GRID_H = 846;          // grid height under the top bar

    @Test
    public void bannerAndSquareHeights() {
        assertEquals(AppAdapter.tileHeightPx(360), AppAdapter.tileHeightPx(360, false));
        assertEquals(202, AppAdapter.tileHeightPx(360, false));
        assertEquals(360, AppAdapter.tileHeightPx(360, true));
    }

    @Test
    public void defaultsLeaveTheFitAlone() {
        for (int rows = 0; rows <= 3; rows++) {
            assertEquals(AppAdapter.tileWidthPx(INNER, GRID_H, 6, rows, false, true, D),
                    AppAdapter.tileWidthPx(INNER, GRID_H, 6, rows, false, true, false, 0f, D));
        }
    }

    @Test
    public void squareRowsFitTheHeight() {
        // One row under a square dock at 6 columns, each with a heading and the name inside: both rows fit, with the
        // tile shrunk below its width-only size to make room for them.
        float extra = AppAdapter.rowExtraDp(true, true, true);
        int w = AppAdapter.tileWidthPx(INNER, GRID_H, 6, 1, false, true, true, extra, D);
        assertTrue(w <= AppAdapter.tileWidthPx(INNER, 6, D));
        int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(w, true), D) + Math.round(extra * D);
        int used = itemH * 2 + Math.round((AppAdapter.DOCK_EXTRA_DP + AppAdapter.BOTTOM_ROOM_DP) * D);
        assertTrue(used <= GRID_H);
    }

    @Test
    public void rowExtraAddsHeadingsAndDropsNames() {
        assertEquals(0f, AppAdapter.rowExtraDp(false, false, true), 0f);
        assertEquals(AppAdapter.ROW_TITLE_DP, AppAdapter.rowExtraDp(true, false, true), 0f);
        assertEquals(-29f, AppAdapter.rowExtraDp(false, true, true), 0f);
        assertEquals(-24f, AppAdapter.rowExtraDp(false, true, false), 0f);
    }

    @Test
    public void cornerRadiusFollowsTheKey() {
        int design = Math.round(AppAdapter.DESIGN_W_DP * D);
        assertEquals(AppAdapter.cornerRadius(design, D), AppAdapter.cornerRadius(design, D, 16), 0f);
        assertEquals(0f, AppAdapter.cornerRadius(design, D, 0), 0f);
        assertEquals(2f * D, AppAdapter.cornerRadius(design, D, 2), 0.001f);
        assertEquals(D, AppAdapter.cornerRadius(design / 2, D, 2), 0.001f);   // smaller tile, smaller corner
    }

    @Test
    public void rowOfPosition() {
        assertEquals(0, RowTitles.row(0, 6));
        assertEquals(0, RowTitles.row(5, 6));
        assertEquals(1, RowTitles.row(6, 6));
        assertEquals(-1, RowTitles.row(-1, 6));
    }
}
