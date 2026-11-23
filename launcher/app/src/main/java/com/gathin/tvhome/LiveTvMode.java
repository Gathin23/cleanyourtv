package com.gathin.tvhome;

/**
 * live_tv_mode: Home, Back to Home, power-on and wake open the Live TV app instead of the home screen, for a
 * TV whose viewers only watch broadcast TV. Pressing Home quickly {@code live_tv_mode_home_presses} times shows
 * the home screen, which then stays reachable until the TV next goes to sleep.
 *
 * The state lives for the process, not the activity: Home rebuilds itself (recreate()) after a Launcher settings
 * change, and an unlocked home screen must not lock again on the way back from those settings.
 */
final class LiveTvMode {
    /** Longest gap between two Home presses that still counts them as one burst. */
    static final long PRESS_GAP_MS = 1000;
    /** A Live TV app that is gone again this soon after Home opened it closed on its own (no signal, a crash):
     *  Home stays on screen instead of opening it in a loop. */
    static final long BOUNCE_MS = 4000;
    /** The on-TV choice for live_tv_mode_home_presses cycles through these. */
    static final int MIN_PRESSES = 2;
    static final int MAX_PRESSES = 3;

    private int presses;
    private long lastPress = Long.MIN_VALUE / 2;
    private long lastOpen = Long.MIN_VALUE / 2;
    private boolean unlocked;

    /** Counts a Home press at {@code now}; true when it completes the burst that unlocks the home screen. */
    boolean press(long now, int needed) {
        presses = now - lastPress <= PRESS_GAP_MS ? presses + 1 : 1;
        lastPress = now;
        if (presses < needed) return false;
        presses = 0;
        unlocked = true;
        return true;
    }

    boolean unlocked() {
        return unlocked;
    }

    /** The TV went to sleep: the next viewer starts on Live TV again. */
    void lock() {
        unlocked = false;
        presses = 0;
    }

    /** Whether the home screen coming to the front at {@code now} opens Live TV instead. */
    boolean opensLiveTv(long now) {
        return !unlocked && now - lastOpen > BOUNCE_MS;
    }

    void opened(long now) {
        lastOpen = now;
    }

    /** Live TV was started at most {@code ms} before {@code now}, so that start may still be on its way to the screen. */
    boolean openedWithin(long now, long ms) {
        return now - lastOpen <= ms;
    }

    /** The next value of the on-TV presses choice: 2, 3, then back to 2. */
    static int nextPresses(int current) {
        return current >= MAX_PRESSES || current < MIN_PRESSES ? MIN_PRESSES : current + 1;
    }
}
