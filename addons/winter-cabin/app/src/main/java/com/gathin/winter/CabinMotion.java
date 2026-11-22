package com.gathin.winter;

/** Small deterministic animation model: no Android, random work, clocks or allocations per frame. */
public final class CabinMotion {
    public static final int FLAKES = 64;
    public static final long FRAME_MS = 83L; // 12 fps, deliberately not a 60 fps full-screen effect
    private CabinMotion() { }

    public static boolean shouldRun(boolean requested, boolean attached, boolean visible) {
        return requested && attached && visible;
    }

    private static float unit(int seed) {
        int n = seed * 1103515245 + 12345;
        return (n & 0x7fffffff) / 2147483648f;
    }

    public static float snowX(int index, double seconds) {
        double x = unit(index * 7 + 19) + Math.sin(seconds * 0.22 + index * 2.4) * 0.025;
        return (float) (x - Math.floor(x));
    }

    public static float snowY(int index, double seconds) {
        double y = unit(index * 13 + 71) + seconds * (0.023 + unit(index + 97) * 0.045);
        return (float) (y - Math.floor(y));
    }

    public static float snowRadius(int index) { return 0.8f + unit(index + 37) * 1.7f; }

    public static float fire(double seconds) {
        return (float) (0.75 + 0.12 * Math.sin(seconds * 5.3)
                + 0.07 * Math.sin(seconds * 11.7) + 0.04 * Math.cos(seconds * 17.1));
    }

    public static float twinkle(int index, double seconds) {
        return (float) (0.7 + 0.3 * Math.sin(seconds * 1.15 + index * 2.17));
    }
}
