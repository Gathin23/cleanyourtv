package com.gathin.tvhome;

import android.graphics.Color;

/**
 * The screensaver glow's colour, from the weather reading and the time of day.
 *
 * Each condition has two colours, one for day and one for night, and the hour picks or mixes them: morning is
 * nearly full day (the sun is up), evening is halfway into night, night is the night colour. Shifting the hue of a
 * single colour could not do this: a stone-grey "partly cloudy" turned into dim brown at night, which read as no
 * glow at all. Two colours per condition keep both ends believable.
 *
 * The condition is read from the line the weather helper publishes (addons/weatherline,
 * {@code OpenMeteo.describe}), so there is no second source and no network: the phrases below are that helper's.
 */
final class ClockGlow {

    private ClockGlow() {
    }

    /** The helper's phrases, longest first: "snow showers" must win over "showers", "mostly clear" over "clear". */
    private static final String[] PHRASES = {
            "snow showers", "partly cloudy", "mostly clear", "overcast", "drizzle",
            "showers", "storms", "clear", "rain", "snow", "fog"};

    /** Daytime and night-time glow for each phrase: {day, night}. */
    private static final String[] CONDITIONS = {
            "clear", "mostly clear", "partly cloudy", "overcast", "fog", "drizzle",
            "showers", "rain", "snow", "snow showers", "storms"};
    private static final int[][] COLOURS = {
            {0xFFE8A24A, 0xFF2E4A6B},   // clear: warm amber by day, blue steel at night
            {0xFFE0B06A, 0xFF35506B},   // mostly clear
            {0xFFA9A281, 0xFF3A5468},   // partly cloudy: stone by day, steel at night
            {0xFF7D8A99, 0xFF43596B},   // overcast
            {0xFF9AA3AB, 0xFF475D6D},   // fog
            {0xFF5E93A3, 0xFF35576B},   // drizzle
            {0xFF2F7F9E, 0xFF2B4A63},   // showers
            {0xFF2F6FB0, 0xFF27405E},   // rain: deep navy steel
            {0xFF8FD3E8, 0xFF47708A},   // snow: the one clearly brighter night colour
            {0xFF8FD3E8, 0xFF47708A},   // snow showers
            {0xFF7A5BD6, 0xFF4A4270},   // storms: indigo
    };

    /** The phrase in a reading ("Brisbane 22° Mostly clear"), or "" when it names no weather we know. */
    static String condition(String line) {
        if (line == null) return "";
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        for (String phrase : PHRASES) if (lower.contains(phrase)) return phrase;
        return "";
    }

    /** The phrase as an emoji for the reading; "" when it names no weather we know. */
    static String emoji(String condition) {
        switch (condition) {
            case "clear":
                return "\u2600\uFE0F";
            case "mostly clear":
                return "\uD83C\uDF24\uFE0F";
            case "partly cloudy":
                return "\u26C5";
            case "overcast":
                return "\u2601\uFE0F";
            case "fog":
                return "\uD83C\uDF2B\uFE0F";
            case "drizzle":
            case "showers":
                return "\uD83C\uDF26\uFE0F";
            case "rain":
                return "\uD83C\uDF27\uFE0F";
            case "snow":
            case "snow showers":
                return "\uD83C\uDF28\uFE0F";
            case "storms":
                return "\u26C8\uFE0F";
            default:
                return "";
        }
    }

    /**
     * The glow colour for that phrase at that time of day, brightened by {@code wobble} and scaled by
     * {@code depth} (1 is the graded look, less is the quieter one). 0 when the phrase is unknown, which leaves the
     * scene on its own palette.
     */
    static int colour(String condition, String timeOfDay, float wobble, float depth) {
        int index = -1;
        for (int i = 0; i < CONDITIONS.length; i++) {
            if (CONDITIONS[i].equals(condition)) {
                index = i;
                break;
            }
        }
        if (index < 0) return 0;
        int day = COLOURS[index][0];
        int night = COLOURS[index][1];
        float intoNight;
        switch (timeOfDay) {
            case WallpaperMood.MORNING:
                intoNight = 0.15f;   // the sun is up: close to the day colour
                break;
            case WallpaperMood.EVENING:
                intoNight = 0.55f;   // halfway into dusk
                break;
            default:
                intoNight = 1f;
                break;
        }
        return scale(blend(day, night, intoNight), wobble * depth);
    }

    private static int blend(int from, int to, float f) {
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * f),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * f),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f));
    }

    /** The same colour at {@code factor} of its brightness, never below a floor that would read as black. */
    private static int scale(int colour, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(colour, hsv);
        hsv[2] = Math.max(0.18f, Math.min(1f, hsv[2] * factor));
        return Color.HSVToColor(hsv);
    }
}
