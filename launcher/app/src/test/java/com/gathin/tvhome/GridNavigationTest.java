package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GridNavigationTest {
    @Test public void hebrewDockStartsOnTheRightAndStopsAtTheLeft() {
        assertTrue(GridNavigation.atHorizontalEdge(0, 11, 4, true, false));
        assertFalse(GridNavigation.atHorizontalEdge(0, 11, 4, true, true));
        assertTrue(GridNavigation.atHorizontalEdge(3, 11, 4, true, true));
        assertFalse(GridNavigation.atHorizontalEdge(3, 11, 4, true, false));
    }

    @Test public void incompleteHebrewLastRowDoesNotWrapIntoEmptySpace() {
        assertTrue(GridNavigation.atHorizontalEdge(8, 11, 4, true, false));
        assertTrue(GridNavigation.atHorizontalEdge(10, 11, 4, true, true));
        assertFalse(GridNavigation.atHorizontalEdge(9, 11, 4, true, true));
        assertFalse(GridNavigation.atHorizontalEdge(9, 11, 4, true, false));
    }

    @Test public void originalLeftToRightBoundariesArePreserved() {
        assertTrue(GridNavigation.atHorizontalEdge(0, 11, 4, false, true));
        assertTrue(GridNavigation.atHorizontalEdge(3, 11, 4, false, false));
        assertTrue(GridNavigation.atHorizontalEdge(10, 11, 4, false, false));
        assertFalse(GridNavigation.atHorizontalEdge(1, 11, 4, false, true));
        assertFalse(GridNavigation.atHorizontalEdge(1, 11, 4, false, false));
    }

    @Test public void singleColumnHasBothHorizontalEdges() {
        assertTrue(GridNavigation.atHorizontalEdge(1, 3, 1, true, true));
        assertTrue(GridNavigation.atHorizontalEdge(1, 3, 1, true, false));
        assertTrue(GridNavigation.atHorizontalEdge(1, 3, 1, false, true));
        assertTrue(GridNavigation.atHorizontalEdge(1, 3, 1, false, false));
    }

    @Test public void horizontalEdgesMatchExplicitRowsAcrossColumnCountsAndDirections() {
        for (GridCase grid : grids()) {
            for (boolean rtl : new boolean[] {false, true}) {
                for (int position = 0; position < grid.count; position++) {
                    for (boolean left : new boolean[] {false, true}) {
                        boolean expected = isPhysicalEdge(grid.rows, position, rtl, left);
                        String message = "count=" + grid.count + ", columns=" + grid.columns
                                + ", position=" + position + ", rtl=" + rtl
                                + ", direction=" + (left ? "left" : "right");
                        assertEquals(message, expected, GridNavigation.atHorizontalEdge(
                                position, grid.count, grid.columns, rtl, left));
                    }
                }
            }
        }
    }

    @Test public void sparseDockRedirectsEmptySlotsAndKeepsGridRowBoundary() {
        int dockApps = 2;
        int columns = 6;
        int gridApps = 12;
        int emptySlots = DockSlots.empty(dockApps, columns, dockApps + gridApps, false);
        int totalSlots = dockApps + gridApps + emptySlots;
        assertEquals("two occupied dock slots leave four empty slots", 4, emptySlots);
        for (int position = dockApps; position < dockApps + emptySlots; position++) {
            assertEquals("empty dock position=" + position, dockApps - 1,
                    DockSlots.focusable(position, dockApps, emptySlots));
        }
        assertEquals("occupied dock position remains focused", 0,
                DockSlots.focusable(0, dockApps, emptySlots));
        assertEquals("first grid app remains focused", dockApps + emptySlots,
                DockSlots.focusable(dockApps + emptySlots, dockApps, emptySlots));

        for (boolean rtl : new boolean[] {false, true}) {
            boolean dockStartIsLeft = !rtl;
            assertTrue("dock count=" + dockApps + ", columns=" + columns
                            + ", position=0, rtl=" + rtl + ", direction="
                            + (dockStartIsLeft ? "left" : "right"),
                    GridNavigation.atHorizontalEdge(0, dockApps, columns, rtl, dockStartIsLeft));
            assertFalse("dock count=" + dockApps + ", columns=" + columns
                            + ", position=0, rtl=" + rtl + ", direction="
                            + (dockStartIsLeft ? "right" : "left"),
                    GridNavigation.atHorizontalEdge(0, dockApps, columns, rtl, !dockStartIsLeft));
            assertTrue("dock count=" + dockApps + ", columns=" + columns
                            + ", position=1, rtl=" + rtl + ", direction="
                            + (dockStartIsLeft ? "right" : "left"),
                    GridNavigation.atHorizontalEdge(1, dockApps, columns, rtl, !dockStartIsLeft));
            assertFalse("dock count=" + dockApps + ", columns=" + columns
                            + ", position=1, rtl=" + rtl + ", direction="
                            + (dockStartIsLeft ? "left" : "right"),
                    GridNavigation.atHorizontalEdge(1, dockApps, columns, rtl, dockStartIsLeft));

            // MainActivity must pass the reduced dock count to keep the outward dock edge here.
            // This helper contract does not exercise that wiring.
            assertTrue("grid count=" + totalSlots + ", columns=" + columns
                            + ", position=" + columns + ", rtl=" + rtl
                            + ", direction=" + (dockStartIsLeft ? "left" : "right"),
                    GridNavigation.atHorizontalEdge(columns, totalSlots, columns, rtl, dockStartIsLeft));
            assertFalse("grid count=" + totalSlots + ", columns=" + columns
                            + ", position=" + columns + ", rtl=" + rtl
                            + ", direction=" + (dockStartIsLeft ? "right" : "left"),
                    GridNavigation.atHorizontalEdge(columns, totalSlots, columns, rtl, !dockStartIsLeft));
        }
    }

    private static List<GridCase> grids() {
        List<GridCase> grids = new ArrayList<>();
        for (int columns = 1; columns <= 8; columns++) {
            grids.add(new GridCase(columns, 1));
            grids.add(new GridCase(columns, columns));
            grids.add(new GridCase(columns, columns, 1));
            grids.add(new GridCase(columns, columns, columns));
        }
        return grids;
    }

    /** Row lengths declare the expected groups; positions are assigned in reading order. */
    private static final class GridCase {
        final int columns;
        final int count;
        final List<List<Integer>> rows = new ArrayList<>();

        GridCase(int columns, int... rowLengths) {
            this.columns = columns;
            int nextPosition = 0;
            for (int rowLength : rowLengths) {
                List<Integer> row = new ArrayList<>();
                for (int i = 0; i < rowLength; i++) row.add(nextPosition++);
                rows.add(row);
            }
            count = nextPosition;
        }
    }

    private static boolean isPhysicalEdge(List<List<Integer>> rows, int position,
                                          boolean rtl, boolean left) {
        for (List<Integer> row : rows) {
            int leftEdge = rtl ? row.get(row.size() - 1) : row.get(0);
            int rightEdge = rtl ? row.get(0) : row.get(row.size() - 1);
            if (position == (left ? leftEdge : rightEdge)) return true;
        }
        return false;
    }

    @Test public void stepWalksTheTilesInReadingOrderAndEndsAtBothEnds() {
        assertEquals(5, GridNavigation.step(4, true, 10, 8, 8));
        assertEquals(3, GridNavigation.step(4, false, 10, 8, 8));
        assertEquals(-1, GridNavigation.step(10, true, 10, 8, 8));
        assertEquals(-1, GridNavigation.step(0, false, 10, 8, 8));
    }

    @Test public void stepSkipsTheEmptyDockSlots() {
        // Three dock apps, five empty slots, the grid from 8.
        assertEquals(8, GridNavigation.step(2, true, 12, 3, 8));
        assertEquals(2, GridNavigation.step(8, false, 12, 3, 8));
    }
}
