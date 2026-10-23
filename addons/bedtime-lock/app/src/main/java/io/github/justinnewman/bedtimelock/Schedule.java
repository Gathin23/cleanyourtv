package io.github.justinnewman.bedtimelock;

/** The locked hours as minutes of the day, wrapping past midnight (21:00 to 07:00 is one window). */
final class Schedule {
    static final int DAY = 24 * 60;

    private Schedule() { }

    /** True when `now` falls in [from, until); a window that ends at or before it starts runs past midnight. */
    static boolean inWindow(int now, int from, int until) {
        if (from == until) return false;
        return from < until ? now >= from && now < until : now >= from || now < until;
    }

    /** Minutes from `now` to the next start or end of the window, 1 to a full day. */
    static int minutesToNextChange(int now, int from, int until) {
        int toStart = Math.floorMod(from - now, DAY);
        int toEnd = Math.floorMod(until - now, DAY);
        if (toStart == 0) toStart = DAY;
        if (toEnd == 0) toEnd = DAY;
        return Math.min(toStart, toEnd);
    }

    /** A minute of the day moved by `delta` minutes, wrapping round midnight. */
    static int shift(int minute, int delta) {
        return Math.floorMod(minute + delta, DAY);
    }
}
