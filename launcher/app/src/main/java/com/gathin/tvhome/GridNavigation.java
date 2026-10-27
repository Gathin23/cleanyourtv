package com.gathin.tvhome;

import android.view.KeyEvent;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
final class GridNavigation {
    private GridNavigation() { }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }

    /**
     * menu_wrap: the tile after (or before) {@code position} in reading order, past the empty dock slots that
     * fill_dock off leaves between the {@code dockFilled} dock apps and the grid at {@code dockSlots}; -1 past
     * either end ({@code last} is the last tile focus can take).
     */
    static int step(int position, boolean forward, int last, int dockFilled, int dockSlots) {
        int p = position + (forward ? 1 : -1);
        if (p >= dockFilled && p < dockSlots) p = forward ? dockSlots : dockFilled - 1;
        return p < 0 || p > last ? -1 : p;
    }

    static boolean isDpad(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }
}
