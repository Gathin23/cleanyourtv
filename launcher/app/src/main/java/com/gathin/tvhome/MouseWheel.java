package com.gathin.tvhome;

import android.view.KeyEvent;

/**
 * Mouse-wheel and touchpad scrolls over Home as remote arrow presses, so a wheel moves the focus one row per notch,
 * like Up and Down, instead of scrolling the grid out from under the focused tile. A wheel notch reports 1.0 on its
 * axis; a touchpad reports fractions, which add up here until they make a whole press.
 */
final class MouseWheel {
    /** Most presses one scroll event may send: a fast touchpad fling stays a short hop, not a jump to the end. */
    static final int MAX_PRESSES = 3;
    /** Float sums of touchpad fractions (ten 0.1s) land just under a whole press; this much counts as reaching it. */
    private static final float SLACK = 1e-3f;

    private int lastKey;
    private float travel;

    /**
     * The arrow key one scroll event stands for, or 0 when it moves neither axis; the larger axis wins. The vertical
     * wheel moves a row, Down when turned towards you (AXIS_VSCROLL negative); in single_row it walks the row, forward
     * being Left in a right-to-left grid. The horizontal wheel moves Left or Right the way it turns (AXIS_HSCROLL).
     */
    static int keyFor(float vscroll, float hscroll, boolean singleRow, boolean rtl) {
        if (vscroll == 0f && hscroll == 0f) return 0;
        if (Math.abs(vscroll) >= Math.abs(hscroll)) {
            boolean forward = vscroll < 0f;
            if (!singleRow) return forward ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP;
            return forward != rtl ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        }
        return hscroll > 0f ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
    }

    /**
     * How many presses of {@code key} a scroll of {@code amount} notches on its axis adds up to, at most MAX_PRESSES.
     * A fraction carries over to the next scroll the same way; turning the other way starts again from nothing.
     */
    int presses(int key, float amount) {
        if (key == 0) return 0;
        if (key != lastKey) {
            lastKey = key;
            travel = 0f;
        }
        travel += Math.abs(amount);
        int whole = (int) (travel + SLACK);
        travel = Math.max(0f, travel - whole);
        return Math.min(whole, MAX_PRESSES);
    }
}
