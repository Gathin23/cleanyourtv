package io.github.cleanyourtv.staticfm;

/**
 * Turns raw band energies into the envelopes the styles read. Each band is divided by its own slowly falling
 * peak, so quiet and loud music move the picture by about the same amount, then sharpened so a kick stands out
 * from the bar around it. {@link #bass} and {@link #high} jump up within a frame and fall back over a few;
 * {@link #mid} drifts. {@code reaction} scales the result (0.5 calm, 1.15 normal, 2.1 lively), so values can
 * go above 1. With no sound at all the picture settles to a gentle breath.
 */
final class Levels {
    float bass, mid, high;
    private float bassPeak = 1e-3f, midPeak = 1e-3f, highPeak = 1e-3f;

    void update(float rawBass, float rawMid, float rawHigh, float reaction, float timeSec) {
        bassPeak = Math.max(rawBass, bassPeak * 0.996f);
        midPeak = Math.max(rawMid, midPeak * 0.997f);
        highPeak = Math.max(rawHigh, highPeak * 0.996f);
        boolean silent = bassPeak < 0.01f && midPeak < 0.01f;
        float b, m, h;
        if (silent) {
            b = breath(timeSec);
            m = 0.5f + 0.5f * (float) Math.sin(timeSec * 0.2);
            h = 0;
        } else {
            b = sharpen(rawBass / bassPeak);
            m = rawMid / midPeak;
            h = sharpen(rawHigh / highPeak);
        }
        bass = follow(bass, b * reaction, 0.7f, 0.18f);
        high = follow(high, h * reaction, 0.8f, 0.3f);
        mid += (m * reaction - mid) * 0.08f;
    }

    /** Fast up, slower down. */
    private static float follow(float now, float target, float up, float down) {
        return now + (target - now) * (target > now ? up : down);
    }

    /** Pushes the quiet part of the bar down so the hits read: 0..1 to 0..1. */
    static float sharpen(float x) {
        x = x < 0 ? 0 : (x > 1 ? 1 : x);
        return x * x * (1.6f - 0.6f * x);
    }

    /** A slow in-and-out, about 6 s, used while nothing plays. */
    static float breath(float timeSec) {
        return 0.25f + 0.25f * (float) Math.sin(timeSec * 2 * Math.PI / 6);
    }
}
