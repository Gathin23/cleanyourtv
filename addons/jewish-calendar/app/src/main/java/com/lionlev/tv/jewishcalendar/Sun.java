package com.lionlev.tv.jewishcalendar;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Sunrise and sunset for a place, computed offline with NOAA's general solar position equations
 * (accurate to a few minutes, which is plenty for a backdrop). Pure maths, no Android types, so
 * it runs in JVM tests; no network and no location permission, the place comes from config.xml.
 */
final class Sun {

    private Sun() {
    }

    /**
     * Local minutes of the day of sunrise and sunset, as {@code {sunrise, sunset}}, or null when the
     * sun does not rise or does not set that day (polar night or midnight sun).
     */
    static int[] riseAndSet(LocalDate date, double latitude, double longitude, ZoneId zone) {
        double g = 2 * Math.PI / 365 * (date.getDayOfYear() - 1);
        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g)
                - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g)
                - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double lat = Math.toRadians(latitude);
        // 90.833 degrees: the sun's upper edge on the horizon, with refraction.
        double cosHa = Math.cos(Math.toRadians(90.833)) / (Math.cos(lat) * Math.cos(decl))
                - Math.tan(lat) * Math.tan(decl);
        if (cosHa > 1 || cosHa < -1) return null;
        double ha = Math.toDegrees(Math.acos(cosHa));
        int offset = zone.getRules().getOffset(date.atTime(12, 0)).getTotalSeconds() / 60;
        return new int[]{
                (int) Math.round(720 - 4 * (longitude + ha) - eqTime + offset),
                (int) Math.round(720 - 4 * (longitude - ha) - eqTime + offset)};
    }
}
