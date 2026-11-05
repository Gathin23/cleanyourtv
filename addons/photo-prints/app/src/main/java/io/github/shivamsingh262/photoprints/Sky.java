package io.github.shivamsingh262.photoprints;

/**
 * Time-of-day sky palette, the same one the cleanyourtv launcher paints behind Home, so the screensaver looks like
 * part of it.
 *
 * Pure maths, no Android types and no clock of its own: the caller passes minutes since midnight. The colours are
 * interpolated between a handful of anchor points through the day, so the sky drifts from night to dawn to sunset
 * instead of switching at fixed hours. Every colour stays dark enough for white text on top.
 */
final class Sky {

    /** One anchor: a minute of the day and the values the palette takes exactly there. */
    static final class Stop {
        final int minute;
        final int top;
        final int mid;
        final int bottom;
        /** Alpha of the sun/moon glow near the horizon, 0 to 255. */
        final int sun;
        /** Horizontal position of the glow, 0 = left edge, 1 = right edge. */
        final float sunX;

        Stop(int minute, int top, int mid, int bottom, int sun, float sunX) {
            this.minute = minute;
            this.top = top;
            this.mid = mid;
            this.bottom = bottom;
            this.sun = sun;
            this.sunX = sunX;
        }
    }

    /** The palette at one instant: the three gradient colours plus the glow. */
    static final class Palette {
        final int top;
        final int mid;
        final int bottom;
        final int sun;
        final float sunX;

        Palette(int top, int mid, int bottom, int sun, float sunX) {
            this.top = top;
            this.mid = mid;
            this.bottom = bottom;
            this.sun = sun;
            this.sunX = sunX;
        }
    }

    private static final int NIGHT_TOP = 0xFF04060E;
    private static final int NIGHT_MID = 0xFF070C1A;
    private static final int NIGHT_BOTTOM = 0xFF0B1326;

    // Anchors were picked around a temperate evening: full night until half past five, dawn before
    // seven, the warmest glow between seven in the evening and a quarter to eight. With a location
    // set, at(minute, sunrise, sunset) stretches this reference day onto the real one.
    static final int REF_SUNRISE = 450;
    static final int REF_SUNSET = 1140;
    private static final Stop[] STOPS = {
            new Stop(0, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            new Stop(320, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            new Stop(390, 0xFF160F2E, 0xFF3B2352, 0xFF8A4A6B, 100, 0.24f),
            new Stop(450, 0xFF12203F, 0xFF5C3A63, 0xFFD3674F, 160, 0.30f),
            new Stop(570, 0xFF0E1A33, 0xFF1F3557, 0xFF43648B, 90, 0.38f),
            new Stop(780, 0xFF0B1524, 0xFF17304F, 0xFF31587F, 60, 0.50f),
            new Stop(1020, 0xFF0D1526, 0xFF20395C, 0xFF4C5470, 90, 0.62f),
            new Stop(1140, 0xFF1B1030, 0xFF6C2E52, 0xFFE2704A, 190, 0.76f),
            new Stop(1185, 0xFF140C27, 0xFF4A2049, 0xFFA54A5C, 130, 0.82f),
            new Stop(1260, 0xFF0A0A1C, 0xFF191230, 0xFF3A1F49, 60, 0.86f),
            new Stop(1350, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
            new Stop(1440, NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM, 0, 0.30f),
    };

    private Sky() {
    }

    /**
     * The palette at {@code minuteOfDay} on a day whose sun rises at {@code sunrise} and sets at
     * {@code sunset} (minutes of the day): night, day and evening are each stretched piecewise so
     * the reference sunrise and sunset glows land on the real ones.
     */
    static Palette at(int minuteOfDay, int sunrise, int sunset) {
        if (sunrise <= 0 || sunset >= 1440 || sunrise >= sunset) return at(minuteOfDay);
        int m = Math.max(0, Math.min(1440, minuteOfDay));
        float ref;
        if (m < sunrise) ref = m * REF_SUNRISE / (float) sunrise;
        else if (m < sunset) ref = REF_SUNRISE + (m - sunrise) * (REF_SUNSET - REF_SUNRISE) / (float) (sunset - sunrise);
        else ref = REF_SUNSET + (m - sunset) * (1440 - REF_SUNSET) / (float) (1440 - sunset);
        return at(Math.round(ref));
    }

    /** The palette at {@code minuteOfDay}, clamped to a day. */
    static Palette at(int minuteOfDay) {
        int m = Math.max(0, Math.min(1440, minuteOfDay));
        Stop a = STOPS[0];
        Stop b = STOPS[STOPS.length - 1];
        for (int i = 0; i < STOPS.length - 1; i++) {
            if (m >= STOPS[i].minute && m <= STOPS[i + 1].minute) {
                a = STOPS[i];
                b = STOPS[i + 1];
                break;
            }
        }
        float t = b.minute == a.minute ? 0f : (m - a.minute) / (float) (b.minute - a.minute);
        return new Palette(
                lerpColor(a.top, b.top, t),
                lerpColor(a.mid, b.mid, t),
                lerpColor(a.bottom, b.bottom, t),
                lerp(a.sun, b.sun, t),
                lerp(a.sunX, b.sunX, t));
    }

    /** Minute of day, 0 to 1439, of a wall clock time. */
    static int minuteOfDay(int hour, int minute) {
        return Math.max(0, Math.min(23, hour)) * 60 + Math.max(0, Math.min(59, minute));
    }

    /** Blends two opaque ARGB colours channel by channel; {@code t} is 0 to 1. */
    static int lerpColor(int from, int to, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * f);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * f);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * f);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * Math.max(0f, Math.min(1f, t)));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * Math.max(0f, Math.min(1f, t));
    }
}
