package com.gathin.tvhome;

/** Device-independent rules for the personal home. Times follow the device clock, not location. */
final class PersonalHomeModel {
    static String time(int hour, int minute) {
        hour = Math.floorMod(hour, 24);
        return String.format(java.util.Locale.ENGLISH, "%02d:%02d %s",
                hour % 12 == 0 ? 12 : hour % 12, Math.floorMod(minute, 60), hour < 12 ? "AM" : "PM");
    }

    static float daylight(int minute) {
        minute = Math.floorMod(minute, 1440);
        if (minute < 330 || minute >= 1170) return 0f;
        if (minute < 450) return (minute - 330) / 120f;
        if (minute > 1050) return (1170 - minute) / 120f;
        return 1f;
    }

    static int columns(int widthDp, int preferred) {
        int fit = Math.max(2, Math.min(6, widthDp / 112));
        return preferred >= 2 ? Math.min(fit, preferred) : fit;
    }

    static String name(String raw) {
        String name = raw == null ? "" : raw.trim();
        int count = name.codePointCount(0, name.length());
        return count > 32 ? name.substring(0, name.offsetByCodePoints(0, 32)) : name;
    }
}
