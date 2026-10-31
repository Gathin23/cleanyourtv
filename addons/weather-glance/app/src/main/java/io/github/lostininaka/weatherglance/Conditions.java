package io.github.lostininaka.weatherglance;

import java.util.Locale;

/** The weather codes Open-Meteo reports (WMO 4677) as the glyph and wording the launcher shows. */
final class Conditions {
    private Conditions() {}

    /**
     * A WMO weather code as {glyph, wording}. Text glyphs rather than emoji: a TV's system font may carry no
     * colour emoji at all, and a tofu box under the clock is worse than no line.
     */
    static String[] describe(int code) {
        switch (code) {
            case 0: return new String[]{"\u2600", "Clear"};
            case 1: return new String[]{"\u2600", "Mostly clear"};
            case 2: return new String[]{"\u2601", "Partly cloudy"};
            case 3: return new String[]{"\u2601", "Overcast"};
            case 45: case 48: return new String[]{"\u224B", "Fog"};
            case 51: case 53: case 55: case 56: case 57: return new String[]{"\u2602", "Drizzle"};
            case 61: case 63: case 65: case 66: case 67: return new String[]{"\u2602", "Rain"};
            case 71: case 73: case 75: case 77: return new String[]{"\u2744", "Snow"};
            case 80: case 81: case 82: return new String[]{"\u2602", "Showers"};
            case 85: case 86: return new String[]{"\u2744", "Snow showers"};
            case 95: case 96: case 99: return new String[]{"\u26A1", "Thunderstorm"};
            default: return new String[]{"\u00B7", "Unknown"};
        }
    }

    /**
     * The line under the clock, in the launcher's small-caps style: "14\u00B0 DRIZZLE \u00B7 TAKIZAWA". The place is
     * optional; a line without one is just the temperature and the wording.
     */
    static String line(int celsius, String wording, String place) {
        String text = (celsius + "\u00B0 " + wording).trim();
        if (place != null && !place.trim().isEmpty()) {
            text = text + " \u00B7 " + place.trim().toUpperCase(Locale.ROOT);
        }
        return text;
    }
}
