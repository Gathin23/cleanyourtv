package io.github.maxhayim.quiethours;

import java.util.Locale;

/**
 * The settings and the decisions, with no Android types, so the JVM tests cover all of it: whether the cap applies
 * at a given minute, the cap as a volume step, whether a volume must come down, and when the schedule next changes.
 */
final class Quiet {
    static final int DAY_MINUTES = 24 * 60;
    static final int TIME_STEP = 15;
    static final int CAP_STEP = 5;
    /** The lowest cap: 5 % of the TV's range, never silence, so a cap can't look like a broken TV. */
    static final int MIN_CAP = 5;

    enum Mode { OFF, SCHEDULED, ALWAYS }

    static final class Settings {
        Mode mode = Mode.SCHEDULED;
        /** The highest volume allowed, in percent of the TV's media volume range. */
        int cap = 20;
        int start = 20 * 60;
        int end = 7 * 60;
        /** Headphones don't wake the room: no cap while sound goes to them. */
        boolean headphonesFree = true;

        Settings clamped() {
            if (mode == null) mode = Mode.SCHEDULED;
            cap = clamp(Math.round(cap / (float) CAP_STEP) * CAP_STEP, MIN_CAP, 100);
            start = wrap(Math.round(start / (float) TIME_STEP) * TIME_STEP);
            end = wrap(Math.round(end / (float) TIME_STEP) * TIME_STEP);
            return this;
        }
    }

    private Quiet() {
    }

    /** True from start up to, not including, end; a window may run past midnight. start == end is never. */
    static boolean inWindow(int start, int end, int minute) {
        if (start == end) return false;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    static boolean capNow(Settings s, int minute) {
        switch (s.mode) {
            case ALWAYS:
                return true;
            case SCHEDULED:
                return inWindow(s.start, s.end, minute);
            default:
                return false;
        }
    }

    /** The cap as a volume step of a stream that goes 0 to max: at least step 1, so it is never silence. */
    static int capIndex(int capPercent, int max) {
        if (max <= 0) return 0;
        return clamp(Math.round(capPercent * max / 100f), 1, max);
    }

    /**
     * True when the volume must come down to the cap right now. Never while muted: on Android, setting a muted
     * stream's volume can unmute it, so the cap waits for the unmute, which is itself a volume change.
     */
    static boolean mustLower(Settings s, int minute, int volume, int max, boolean onHeadphones, boolean fixedVolume,
                             boolean muted) {
        if (fixedVolume || muted || !capNow(s, minute)) return false;
        if (onHeadphones && s.headphonesFree) return false;
        return volume > capIndex(s.cap, max);
    }

    /** Minutes until the cap can start or stop by itself; -1 when it never does on its own. */
    static int minutesToNextChange(Settings s, int minute) {
        if (s.mode != Mode.SCHEDULED || s.start == s.end) return -1;
        int toStart = wrap(s.start - minute);
        int toEnd = wrap(s.end - minute);
        if (toStart == 0) toStart = DAY_MINUTES;
        if (toEnd == 0) toEnd = DAY_MINUTES;
        return Math.min(toStart, toEnd);
    }

    /** The service runs while a cap applies or a schedule waits for its start. */
    static boolean needsService(Settings s) {
        return s.mode != Mode.OFF;
    }

    static String clock(int minute, boolean h24) {
        int h = minute / 60, m = minute % 60;
        if (h24) return String.format(Locale.ROOT, "%02d:%02d", h, m);
        return String.format(Locale.ROOT, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static int wrap(int minute) {
        return ((minute % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES;
    }
}
