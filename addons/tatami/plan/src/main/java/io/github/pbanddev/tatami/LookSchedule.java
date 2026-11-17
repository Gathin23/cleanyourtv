package io.github.pbanddev.tatami;

/**
 * Which look the plan shows at a moment, and how bright: the Look setting ({@link Mode}), the swap period, the wall
 * clock, when Nagaya became ready and when this view started decide, so every view of the plan agrees once it has
 * settled. Pure Java; {@link #at} fills {@link #look} and {@link #fade} without allocating.
 *
 * <p>Nagaya is the look the plan leads with; Yojōhan is built at once and stands in whenever Nagaya is not there.
 * {@link Mode#BOTH}: period n = floor(t / swapSeconds) shows Nagaya when n is even and Yojōhan when n is odd. A swap
 * dips to dark: the picture fades out over the {@link #DIP_SECONDS} before the period starts and back in over the
 * {@link #DIP_SECONDS} after; the look changes at the start, in the dark. A swap between 2 periods that show the same
 * look has no dip.
 *
 * <p>Nagaya builds in the background when a view first asks for it. If a view starts while Nagaya is wanted and not
 * ready, the picture stays dark for up to {@link #WAIT_SECONDS} and fades Nagaya in as soon as it is ready. Later
 * than that, Yojōhan fades in instead, and Nagaya takes over with a dip once ready (in Both, only in its own periods).
 */
public final class LookSchedule {

    /** The Look setting, in the order OK steps through them. {@link #key} is its stored name. */
    public enum Mode {
        NAGAYA("nagaya"), YOJOHAN("yojohan"), BOTH("both");

        public final String key;

        Mode(String key) {
            this.key = key;
        }

        /** The mode stored as {@code key}, or null for any other value. */
        public static Mode of(String key) {
            for (Mode mode : values()) {
                if (mode.key.equals(key)) return mode;
            }
            return null;
        }

        /** The mode after this one, as OK steps through them in the settings. */
        public Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    /** A swap fades out over this many seconds and back in over as many. */
    public static final double DIP_SECONDS = 1.2;
    /** The longest a starting view waits in the dark for Nagaya before Yojōhan fades in. */
    public static final double WAIT_SECONDS = 2.0;

    /** The look to draw. */
    public Look look = Look.YOJOHAN;
    /** How bright: 1 as drawn, 0 black. */
    public float fade = 1f;

    /**
     * Picks the look and the fade for wall-clock time {@code t} (seconds).
     *
     * @param readySince when Nagaya became ready to draw, on the same clock; positive infinity while it is not
     *                   (not built yet, or failed)
     * @param startedAt  when this view drew its first frame, on the same clock
     */
    public void at(double t, Mode mode, double swapSeconds, double readySince, double startedAt) {
        look = Look.YOJOHAN;
        fade = 1f;
        if (mode == Mode.YOJOHAN) return;
        long n = (long) Math.floor(t / swapSeconds);
        boolean now = nagaya(t, n, mode, readySince, startedAt);
        if (now) look = Look.NAGAYA;
        // a view that starts while Nagaya is wanted and not ready: dark, then whichever look comes first fades in
        if (readySince > startedAt && wanted(nOf(startedAt, swapSeconds), mode)) {
            double settle = Math.min(readySince, startedAt + WAIT_SECONDS);
            if (t < settle) {
                fade = 0f;
                return;
            }
            fade = Math.min(fade, dip(t - settle));
        }
        // a Nagaya that is ready too late for the start takes over with a dip, in a period that wants it
        double over = readySince + DIP_SECONDS;
        if (readySince > startedAt + WAIT_SECONDS && over < Double.POSITIVE_INFINITY
                && wanted((long) Math.floor(over / swapSeconds), mode)) {
            fade = Math.min(fade, dip(Math.abs(t - over)));
        }
        if (mode == Mode.BOTH) {
            double start = n * swapSeconds, end = start + swapSeconds;
            if (now != nagaya(start, n - 1, mode, readySince, startedAt)) fade = Math.min(fade, dip(t - start));
            if (now != nagaya(end, n + 1, mode, readySince, startedAt)) fade = Math.min(fade, dip(end - t));
        }
    }

    private static long nOf(double t, double swapSeconds) {
        return (long) Math.floor(t / swapSeconds);
    }

    /** True when period n wants Nagaya: always in {@link Mode#NAGAYA}, even periods in {@link Mode#BOTH}. */
    private static boolean wanted(long n, Mode mode) {
        return mode == Mode.NAGAYA || (mode == Mode.BOTH && Math.floorMod(n, 2) == 0);
    }

    /**
     * True when Nagaya shows (fade aside) at time t, counted in period n: it is wanted, and ready by then, or, when it
     * was ready too late for the view's start, a dip after that.
     */
    private static boolean nagaya(double t, long n, Mode mode, double readySince, double startedAt) {
        if (!wanted(n, mode)) return false;
        return readySince <= startedAt + WAIT_SECONDS ? t >= readySince : t >= readySince + DIP_SECONDS;
    }

    /** The brightness {@code seconds} away from a swap: 0 at it, 1 from {@link #DIP_SECONDS} on, eased at both ends. */
    private static float dip(double seconds) {
        if (seconds >= DIP_SECONDS) return 1f;
        if (seconds <= 0) return 0f;
        return (float) ((1 - Math.cos(Math.PI * seconds / DIP_SECONDS)) / 2);
    }
}
