package io.github.nottoobright.koipond;

/**
 * Time-of-day colour grade. Five key palettes pinned to minutes of the day and blended with smoothstep between
 * neighbouring anchors, wrapping at midnight, so the pond follows the TV clock without visible steps.
 */
final class TimeOfDay {

    static final int DAWN = 0, DAY = 1, GOLDEN = 2, DUSK = 3, NIGHT = 4;

    static final int MINUTES = 1440;

    // Anchors in minute order; the last one wraps round to the first one, plus a day.
    private static final int[] ANCHOR_MIN = {270, 360, 480, 960, 1050, 1140, 1230};
    private static final int[] ANCHOR_KEY = {NIGHT, DAWN, DAY, DAY, GOLDEN, DUSK, NIGHT};

    private static final int FIREFLY_RISE_START = 1140, FIREFLY_RISE_END = 1230;
    private static final int FIREFLY_FALL_START = 270, FIREFLY_FALL_END = 360;

    private static final Palette[] PALETTES = {
            // DAWN: a little cooler and paler than day, pink-lavender light, soft caustics.
            new Palette(0xFF1A4A48, 0xFF4A9C94, 0xFF1E4E4A, 0xFFF2DCE6,
                    0xFF327142, 0xFF559F55, 0xFF23532F, 0xFFF28AAE, 0xFFF0AE50, 0xFF6CBE50),
            // DAY: jade water, full caustics, no grade. The pads are lighter, yellower greens than the water, so
            // they read as floating leaves; dawn and golden hour are shifted the same way.
            new Palette(0xFF16473F, 0xFF3E9A86, 0xFF2A6A5C, 0xFFFFFFFF,
                    0xFF2E7238, 0xFF4E9E48, 0xFF1F5228, 0xFFF27AA0, 0xFFF2A23A, 0xFF62C23A),
            // GOLDEN: amber light and strong, warm caustics.
            new Palette(0xFF2A4038, 0xFF6A9482, 0xFF8A6428, 0xFFFFD8A8,
                    0xFF387234, 0xFF5DA143, 0xFF255223, 0xFFF2789A, 0xFFF5A030, 0xFF78C238),
            // DUSK: violet-blue light, weak caustics.
            new Palette(0xFF123C40, 0xFF2E7A7A, 0xFF183A44, 0xFFB8A8D8,
                    0xFF1A4E36, 0xFF2A6C48, 0xFF123828, 0xFFE070A0, 0xFFE09440, 0xFF52A840),
            // NIGHT: deep blue, caustics a faint shimmer of moonlight. Keyed bright enough that after the grade
            // the floor stays near luma 30 (TVs crush much darker to black) and the pads sit clearly above it.
            new Palette(0xFF0E2C30, 0xFF24605E, 0xFF060C10, 0xFF7080B0,
                    0xFF1E5A3C, 0xFF2E7A4E, 0xFF143E2C, 0xFFD07098, 0xFFD09040, 0xFF4A9A40),
    };

    // at() is asked every frame. It is quantised to 1/16 of a minute (under 4 s, far below one colour step)
    // and the last answer cached, so a blended palette is built a few times a minute instead of 30 times a second.
    private static final float QUANT = 16f;

    private static final class Cached {
        final float minute;
        final Palette palette;

        Cached(float minute, Palette palette) {
            this.minute = minute;
            this.palette = palette;
        }
    }

    private static volatile Cached cached = new Cached(-1f, null);

    private TimeOfDay() {
    }

    /** The blended palette for a time, minuteOfDay in [0, 1440) (fractional minutes allowed; values outside wrap). */
    static Palette at(float minuteOfDay) {
        float m = Math.round(wrap(minuteOfDay) * QUANT) / QUANT;
        if (m >= MINUTES) m -= MINUTES;
        Cached c = cached;
        if (c.minute == m) return c.palette;
        int i = segment(m);
        Palette p = Palette.lerp(PALETTES[ANCHOR_KEY[i]], PALETTES[ANCHOR_KEY[next(i)]], weight(m, i));
        cached = new Cached(m, p);
        return p;
    }

    /** Firefly intensity 0..1 for the time. */
    static float fireflies(float minuteOfDay) {
        float m = wrap(minuteOfDay);
        if (m >= FIREFLY_RISE_END || m < FIREFLY_FALL_START) return 1f;
        if (m >= FIREFLY_RISE_START) {
            return smoothstep((m - FIREFLY_RISE_START) / (FIREFLY_RISE_END - FIREFLY_RISE_START));
        }
        if (m < FIREFLY_FALL_END) {
            return 1f - smoothstep((m - FIREFLY_FALL_START) / (FIREFLY_FALL_END - FIREFLY_FALL_START));
        }
        return 0f;
    }

    /** The dominant key (the one with the larger weight) for the time; for logging and tests. */
    static int key(float minuteOfDay) {
        float m = wrap(minuteOfDay);
        int i = segment(m);
        return weight(m, i) > 0.5f ? ANCHOR_KEY[next(i)] : ANCHOR_KEY[i];
    }

    /** Linear blend of two 0xAARRGGBB colours, t clamped to 0..1. */
    static int lerp(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            int c = (int) (ca + (cb - ca) * t + 0.5f);
            out |= c << shift;
        }
        return out;
    }

    /** Unblended palette of one key. */
    static Palette palette(int key) {
        return PALETTES[key];
    }

    private static float wrap(float minute) {
        float m = minute % MINUTES;
        if (m < 0f) m += MINUTES;
        // -0.00001f % 1440 + 1440 rounds to 1440 in float.
        return m >= MINUTES ? 0f : m;
    }

    /** Index of the anchor at or before m; the last anchor's segment runs over midnight to the first. */
    private static int segment(float m) {
        int last = ANCHOR_MIN.length - 1;
        if (m < ANCHOR_MIN[0] || m >= ANCHOR_MIN[last]) return last;
        int i = 0;
        while (m >= ANCHOR_MIN[i + 1]) i++;
        return i;
    }

    private static int next(int i) {
        return i + 1 < ANCHOR_MIN.length ? i + 1 : 0;
    }

    /** Smoothstepped weight of the next anchor's key within segment i. */
    private static float weight(float m, int i) {
        int start = ANCHOR_MIN[i];
        int end = ANCHOR_MIN[next(i)];
        if (end <= start) end += MINUTES;
        if (m < start) m += MINUTES;
        return smoothstep((m - start) / (end - start));
    }

    private static float smoothstep(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        return x * x * (3f - 2f * x);
    }
}
