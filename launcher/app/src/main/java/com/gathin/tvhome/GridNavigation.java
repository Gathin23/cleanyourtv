package com.gathin.tvhome;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
final class GridNavigation {
    private GridNavigation() { }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }

    /** Dim level of a non-carried tile: edit-dock dims only the other dock places, Move mode dims every tile. */
    static float idleAlpha(int pos, int columns, boolean dockEditing, int dockEditSlot, boolean reordering) {
        if (dockEditing) return dockEditSlot >= 0 && pos >= 0 && pos < columns && pos != dockEditSlot ? 0.45f : 1f;
        return reordering ? 0.45f : 1f;
    }
}
