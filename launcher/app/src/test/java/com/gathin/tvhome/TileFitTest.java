package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** AppAdapter.tileWidthPx with rows_below_dock: the dock, its title and the rows must fit the grid's height. */
public class TileFitTest {

    private static final float D = 2f;              // 1080p TV: 2 px per dp
    private static final int INNER = 1696;          // grid width minus padding at 1920 px
    private static final int GRID_H = 846;          // grid height under the top bar

    private static int height(int tileW, int rows, boolean title) {
        int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(tileW), D);
        return itemH * (rows + 1) + Math.round((AppAdapter.DOCK_EXTRA_DP + AppAdapter.BOTTOM_ROOM_DP
                + (title ? AppAdapter.TITLE_ROOM_DP : 0)) * D);
    }

    @Test
    public void noRowsBelowDockKeepsTheWidthOnlySize() {
        int widthOnly = AppAdapter.tileWidthPx(INNER, 4, D);
        assertEquals(widthOnly, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 0, AppAdapter.TITLE_ROOM_DP, true, D));
        assertEquals(widthOnly, AppAdapter.tileWidthPx(INNER, 0, 4, 3, AppAdapter.TITLE_ROOM_DP, true, D));   // not laid out yet
    }

    @Test
    public void dockTitleAndRowsFitTheHeightUnlessThatMeansTinyTiles() {
        int floor = Math.round(AppAdapter.tileWidthPx(INNER, 4, D) * AppAdapter.MIN_HEIGHT_FIT);
        for (int rows = 1; rows <= 3; rows++) {
            for (boolean title : new boolean[]{true, false}) {
                int w = AppAdapter.tileWidthPx(INNER, GRID_H, 4, rows, title ? AppAdapter.TITLE_ROOM_DP : 0, true, D);
                assertTrue("rows " + rows + " title " + title + " below the floor", w >= floor);
                if (w > floor) assertTrue("rows " + rows + " title " + title, height(w, rows, title) <= GRID_H + 2);
            }
        }
    }

    @Test
    public void oneRowUnderATitledDockFits() {
        int w = AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, AppAdapter.TITLE_ROOM_DP, true, D);
        assertTrue(w < AppAdapter.tileWidthPx(INNER, 4, D));
        assertTrue(height(w, 1, true) <= GRID_H + 2);
    }

    @Test
    public void moreRowsStopAtTheFloorInsteadOfShrinkingTheTiles() {
        // Two or three rows under a titled dock would need tiles under half their size: they stop at 85% and
        // the extra rows run off the bottom instead.
        int floor = Math.round(AppAdapter.tileWidthPx(INNER, 4, D) * AppAdapter.MIN_HEIGHT_FIT);
        assertEquals(floor, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 2, AppAdapter.TITLE_ROOM_DP, true, D));
        assertEquals(floor, AppAdapter.tileWidthPx(INNER, GRID_H, 4, 3, AppAdapter.TITLE_ROOM_DP, true, D));
    }

    @Test
    public void plainGridFitsFourRowsAtSixColumns() {
        // dock_row false, rows_below_dock 3: the first row and 3 more under a 12dp top margin.
        int w = AppAdapter.tileWidthPx(INNER, GRID_H, 6, 3, AppAdapter.TITLE_ROOM_DP, false, D);
        int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(w), false, D);
        int needed = itemH * 4 + Math.round((AppAdapter.TOP_ROOM_DP + AppAdapter.COMPACT_BOTTOM_ROOM_DP) * D);
        assertTrue(needed + " > " + GRID_H, needed <= GRID_H + 2);
        // With the compact spacing the width limit decides, not the height: no extra whitespace between tiles.
        assertEquals(AppAdapter.tileWidthPx(INNER, 6, D), w);
    }

    @Test
    public void droppingTheTitleGivesBiggerTiles() {
        assertTrue(AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, 0, true, D)
                > AppAdapter.tileWidthPx(INNER, GRID_H, 4, 1, AppAdapter.TITLE_ROOM_DP, true, D));
    }

    @Test
    public void aLargerDockTitleTakesMoreRoom() {
        assertEquals(AppAdapter.TITLE_ROOM_DP, AppAdapter.titleRoomDp(Config.DOCK_TITLE_SP), 0f);
        assertTrue(AppAdapter.titleRoomDp(Config.DOCK_TITLE_SP_MAX) > AppAdapter.TITLE_ROOM_DP);
        assertTrue(AppAdapter.titleRoomDp(Config.DOCK_TITLE_SP_MIN) < AppAdapter.TITLE_ROOM_DP);
    }

    @Test
    public void aChosenDockTitleSizeWinsOnlyInsideTheRange() {
        assertEquals(60, Config.chosenDockTitleSp(60, 48));
        assertEquals(48, Config.chosenDockTitleSp(-1, 48));
        assertEquals(48, Config.chosenDockTitleSp(80, 48));
    }
}
