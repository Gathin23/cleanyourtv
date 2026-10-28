package io.github.savioronezero.worldclock;

/** Monotonic, bounded movement. No wall-clock or time-zone change can move a clock off screen. */
final class AmbientMotion {
    static final long FRAME_MS = 67; // At most 15 updates/second; reduced motion schedules none.
    static final int DRIFT_DP = 12;
    static int columns(int count) {
        if (count < 1 || count > ClockModel.LIMIT) throw new IllegalArgumentException("clock count");
        return Math.min(3, count);
    }
    static int rows(int count) { return (count + columns(count) - 1) / columns(count); }
    static float drift(long elapsed, long period) {
        return (float) Math.sin(Math.floorMod(elapsed, period) * (2 * Math.PI / period));
    }
    static float clockAlpha(int brightness) {
        return brightness == 0 ? 0.35f : brightness == 2 ? 1f : 0.65f;
    }
}
