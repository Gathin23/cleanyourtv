package io.github.maxhayim.brightness;

/**
 * The settings and the arithmetic, with no Android types, so the JVM tests cover all of it: which level applies
 * at a given minute, the one colour the layer is drawn in, and when the schedule next changes.
 */
final class Dimming {
    /** The darkest the layer goes: 1 % of normal, for a TV whose own backlight is already at its lowest. */
    static final int MIN_BRIGHTNESS = 1;
    static final int MAX_BRIGHTNESS = 100;
    static final int BRIGHTNESS_STEP = 5;
    /** Below this the slider moves in 1 % steps, where each step is a large share of what is left. */
    static final int FINE_BELOW = 10;
    static final int WARMTH_STEP = 10;
    static final int TIME_STEP = 15;
    static final int DAY_MINUTES = 24 * 60;
    /** Warmth 100 lays this amber over the picture at MAX_WARM_ALPHA: blue and green drop, red stays. */
    static final int WARM_RGB = 0xFF8A1E;
    static final float MAX_WARM_ALPHA = 0.4f;

    enum Night { OFF, ON, SCHEDULED }

    /**
     * Night mode after the one-press toggle (NightToggleActivity): On when it turns night on; when it turns night
     * off, the mode that toggle replaced if it was the schedule, so a scheduled night is not lost, otherwise Off.
     */
    static Night nightAfterToggle(boolean turnOn, Night replaced) {
        if (turnOn) return Night.ON;
        return replaced == Night.SCHEDULED ? Night.SCHEDULED : Night.OFF;
    }

    static final class Settings {
        /** The master switch. Off draws nothing, whatever the rest says. */
        boolean on;
        int brightness = 70;
        Night night = Night.SCHEDULED;
        int nightBrightness = 40;
        int warmth = 50;
        /** Minutes after midnight, local time. */
        int nightStart = 21 * 60;
        int nightEnd = 7 * 60;
        /** The TV's own picture at night too (Realtek TVs): the values below, and the day's back in the morning. */
        boolean tvPicture;
        Picture tvNight = Picture.NIGHT;

        /** Every value back in range, whatever the stored copy held. */
        Settings clamped() {
            brightness = clampBrightness(brightness);
            nightBrightness = clampBrightness(nightBrightness);
            warmth = clamp(snap(warmth, WARMTH_STEP), 0, 100);
            nightStart = wrap(snap(nightStart, TIME_STEP));
            nightEnd = wrap(snap(nightEnd, TIME_STEP));
            if (night == null) night = Night.SCHEDULED;
            tvNight = tvNight == null ? Picture.NIGHT : tvNight.clamped();
            return this;
        }
    }

    /** The TV's own picture settings: backlight, contrast and brightness 0 to 100, and dynamic backlight. */
    static final class Picture {
        /** The night default: the darkest the panel goes, with contrast and black level down and dynamic backlight on. */
        static final Picture NIGHT = new Picture(0, 15, 35, true);
        /** The day default when the day's own values were never seen: the TVs' Standard picture. */
        static final Picture DAY = new Picture(100, 50, 50, false);

        final int backlight;
        final int contrast;
        final int brightness;
        final boolean dynamicBacklight;

        Picture(int backlight, int contrast, int brightness, boolean dynamicBacklight) {
            this.backlight = backlight;
            this.contrast = contrast;
            this.brightness = brightness;
            this.dynamicBacklight = dynamicBacklight;
        }

        Picture clamped() {
            return new Picture(clamp(backlight, 0, 100), clamp(contrast, 0, 100), clamp(brightness, 0, 100), dynamicBacklight);
        }

        int[] values() {
            return new int[]{backlight, contrast, brightness, dynamicBacklight ? 1 : 0};
        }

        Picture withBacklight(int v) {
            return new Picture(v, contrast, brightness, dynamicBacklight).clamped();
        }

        Picture withContrast(int v) {
            return new Picture(backlight, v, brightness, dynamicBacklight).clamped();
        }

        Picture withBrightness(int v) {
            return new Picture(backlight, contrast, v, dynamicBacklight).clamped();
        }

        Picture withDynamicBacklight(boolean v) {
            return new Picture(backlight, contrast, brightness, v);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Picture && java.util.Arrays.equals(values(), ((Picture) o).values());
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(values());
        }
    }

    /**
     * The picture to put back in the morning, chosen when night begins from what the TV shows then. If it already
     * shows the night picture (set by hand before), the day's values are unknown and the remembered or default
     * day picture stands.
     */
    static Picture dayPicture(Picture now, Picture night, Picture remembered) {
        if (now != null && !now.equals(night)) return now;
        return remembered != null ? remembered : Picture.DAY;
    }

    /** True when the TV's picture should be the night one now. */
    static boolean tvNightNow(Settings s, int minute) {
        return s.on && s.tvPicture && level(s, minute).night;
    }

    /** What the layer shows right now. brightness 100 and warmth 0 means no layer at all. */
    static final class Level {
        final int brightness;
        final int warmth;
        final boolean night;

        Level(int brightness, int warmth, boolean night) {
            this.brightness = brightness;
            this.warmth = warmth;
            this.night = night;
        }

        boolean visible() {
            return brightness < MAX_BRIGHTNESS || warmth > 0;
        }
    }

    private Dimming() {
    }

    /** True from start up to, not including, end; a window may run past midnight. start == end is never night. */
    static boolean inWindow(int start, int end, int minute) {
        if (start == end) return false;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    static boolean nightNow(Settings s, int minute) {
        switch (s.night) {
            case ON:
                return true;
            case SCHEDULED:
                return inWindow(s.nightStart, s.nightEnd, minute);
            default:
                return false;
        }
    }

    static Level level(Settings s, int minute) {
        if (!s.on) return new Level(MAX_BRIGHTNESS, 0, false);
        boolean night = nightNow(s, minute);
        return night ? new Level(s.nightBrightness, s.warmth, true) : new Level(s.brightness, 0, false);
    }

    /**
     * The layer's ARGB colour. Drawing it over the picture equals an amber layer at warmth's alpha with black
     * at (1 - brightness) over that, folded into one window: out = (1 - a) * picture + a * colour.
     */
    static int color(Level l) {
        double black = 1 - l.brightness / 100.0;
        double warm = MAX_WARM_ALPHA * l.warmth / 100.0;
        double alpha = 1 - (1 - black) * (1 - warm);
        if (alpha <= 0) return 0;
        double k = (1 - black) * warm / alpha;
        int r = (int) Math.round(((WARM_RGB >> 16) & 0xFF) * k);
        int g = (int) Math.round(((WARM_RGB >> 8) & 0xFF) * k);
        int b = (int) Math.round((WARM_RGB & 0xFF) * k);
        int a = (int) Math.round(alpha * 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** Android draws a window that lets touches through at most this opaque (InputManager's default). */
    static final int MAX_PASS_THROUGH_ALPHA = Math.round(0.8f * 255);

    /** True when the layer must keep touches to be drawn as dark as its colour says. */
    static boolean keepsTouches(int color) {
        return (color >>> 24) > MAX_PASS_THROUGH_ALPHA;
    }

    /**
     * Minutes from minute until the level can next change by itself: the next start or end of the night window.
     * -1 when nothing changes on its own (dimming off, or night mode not scheduled, or an empty window).
     */
    static int minutesToNextChange(Settings s, int minute) {
        if (!s.on || s.night != Night.SCHEDULED || s.nightStart == s.nightEnd) return -1;
        int toStart = wrap(s.nightStart - minute);
        int toEnd = wrap(s.nightEnd - minute);
        if (toStart == 0) toStart = DAY_MINUTES;
        if (toEnd == 0) toEnd = DAY_MINUTES;
        return Math.min(toStart, toEnd);
    }

    /** True when the service has work: a layer to hold, or a schedule to follow. */
    static boolean needsService(Settings s, int minute) {
        return s.on && (level(s, minute).visible() || minutesToNextChange(s, minute) > 0);
    }

    static String clock(int minute, boolean h24) {
        int h = minute / 60, m = minute % 60;
        if (h24) return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m);
        return String.format(java.util.Locale.ROOT, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
    }

    /** One press of ◀ or ▶ on a brightness slider: 5 % steps, 1 % steps below FINE_BELOW. */
    static int stepBrightness(int v, int direction) {
        boolean fine = v < FINE_BELOW || (v == FINE_BELOW && direction < 0);
        return clampBrightness(v + direction * (fine ? 1 : BRIGHTNESS_STEP));
    }

    /** One press on a TV picture slider, 0 to 100: 5 steps, 1 steps below FINE_BELOW. */
    static int stepPercent(int v, int direction) {
        boolean fine = v < FINE_BELOW || (v == FINE_BELOW && direction < 0);
        int next = v + direction * (fine ? 1 : BRIGHTNESS_STEP);
        return clamp(next < FINE_BELOW ? next : snap(next, BRIGHTNESS_STEP), 0, 100);
    }

    static int clampBrightness(int v) {
        return clamp(v < FINE_BELOW ? v : snap(v, BRIGHTNESS_STEP), MIN_BRIGHTNESS, MAX_BRIGHTNESS);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static int wrap(int minute) {
        return ((minute % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES;
    }

    private static int snap(int v, int step) {
        return Math.round(v / (float) step) * step;
    }
}
