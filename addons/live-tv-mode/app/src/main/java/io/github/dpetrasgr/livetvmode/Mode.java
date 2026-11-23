package io.github.dpetrasgr.livetvmode;

/**
 * The rules, apart from Android so they can be tested: the home screen coming to the front while it is locked opens
 * Live TV, quick returns to it count toward the burst that unlocks it, and sleep locks it again.
 */
final class Mode {
    /** Longest gap between two Home presses that still counts them as one burst. */
    static final long PRESS_GAP_MS = 1000;
    /** One appearance of the home screen can be reported twice in a row; closer than this, it counts once. */
    static final long SAME_APPEARANCE_MS = 150;
    /** A Live TV app that keeps closing (no signal, a crash) is opened at most this often in LOOP_WINDOW_MS. A
     *  closing app that comes back within PRESS_GAP_MS unlocks Home by itself after a few rounds; this catches a
     *  slower one. Ten in fifteen seconds is more than a viewer pressing Home does. */
    static final int LOOP_OPENS = 10;
    static final long LOOP_WINDOW_MS = 15_000;
    /** The presses the settings screen offers. */
    static final int MIN_PRESSES = 2;
    static final int MAX_PRESSES = 3;

    enum Action { NONE, OPEN_LIVE_TV, UNLOCKED }

    private static final long NEVER = Long.MIN_VALUE / 2;
    private int presses;
    private long lastPress = NEVER;
    private long lastOpen = NEVER;
    private boolean unlocked;
    private final long[] opens = new long[LOOP_OPENS];
    private int next;

    Mode() {
        java.util.Arrays.fill(opens, NEVER);
    }

    /** The home screen came to the front at {@code now}: a Home press, Back from an app, or the TV starting. */
    Action home(long now, int needed) {
        if (unlocked || now - lastPress < SAME_APPEARANCE_MS) return Action.NONE;
        presses = now - lastPress <= PRESS_GAP_MS ? presses + 1 : 1;
        lastPress = now;
        if (presses >= needed) {
            presses = 0;
            unlocked = true;
            return Action.UNLOCKED;
        }
        return looping(now) ? Action.NONE : Action.OPEN_LIVE_TV;
    }

    /** Live TV has been opened {@code LOOP_OPENS} times within {@code LOOP_WINDOW_MS}: stop, Home stays. */
    boolean looping(long now) {
        return now - opens[next] < LOOP_WINDOW_MS;
    }

    void opened(long now) {
        opens[next] = now;
        next = (next + 1) % LOOP_OPENS;
        lastOpen = now;
    }

    /** Live TV was started at most {@code ms} before {@code now}, so that start may still be on its way to the screen. */
    boolean openedWithin(long now, long ms) {
        return now - lastOpen <= ms;
    }

    boolean unlocked() {
        return unlocked;
    }

    /** The TV went to sleep or woke up: the next viewer starts on Live TV again, with the loop count cleared. */
    void lock() {
        unlocked = false;
        presses = 0;
        lastPress = NEVER;
        java.util.Arrays.fill(opens, NEVER);   // a new start: waking up always opens Live TV
    }

    /** Waking up opens Live TV over the home screen always, and over another app only when {@code fromApps}. An
     *  app already showing Live TV is left as it is. */
    static boolean opensOnWake(boolean homeInFront, boolean liveTvInFront, boolean fromApps) {
        if (liveTvInFront) return false;
        return homeInFront || fromApps;
    }

    /** The next value of the presses setting: 2, 3, then back to 2. */
    static int nextPresses(int current) {
        return current >= MAX_PRESSES || current < MIN_PRESSES ? MIN_PRESSES : current + 1;
    }
}
