package com.gathin.tvhome;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * wallpaper_moods: which group of a rotating wallpaper collection fits the time of day. Morning from sunrise to noon,
 * afternoon until an hour and a half before sunset, evening until half an hour after it, night the rest. The sun is
 * the one at config.xml's latitude and longitude, worked out offline by {@link Sun} as the sky does, or 07:00 and
 * 18:30 without a place. A theme with no afternoon group keeps its evening group from noon, as before afternoon
 * existed. A group that is missing, or has none of the collection's pictures, falls back to the whole collection.
 */
final class WallpaperMood {
    static final String MORNING = "morning", AFTERNOON = "afternoon", EVENING = "evening", NIGHT = "night";
    static final String[] ALL = {MORNING, AFTERNOON, EVENING, NIGHT};
    /** Sunrise and sunset in minutes of the day, without a place (or where the sun does not rise or set that day). */
    static final int DEFAULT_SUNRISE = 7 * 60, DEFAULT_SUNSET = 18 * 60 + 30;
    private static final int NOON = 12 * 60;
    /** Evening starts this long before sunset, as the light turns. */
    private static final int GOLDEN_MINUTES = 90;
    /** Evening lasts this long after sunset, while the sky still glows. */
    private static final int DUSK_MINUTES = 30;

    private WallpaperMood() {
    }

    /** The mood at this minute of the day, for a sunrise and sunset in minutes of the day. */
    static String timeOfDay(int minute, int sunrise, int sunset) {
        if (minute >= sunrise && minute < NOON) return MORNING;
        if (minute >= NOON && minute < sunset - GOLDEN_MINUTES) return AFTERNOON;
        if (minute >= NOON && minute < sunset + DUSK_MINUTES) return EVENING;
        return NIGHT;
    }

    /** That day's {sunrise, sunset} at config.xml's latitude and longitude; the defaults when either is unusable. */
    static int[] sun(LocalDate day, String latitude, String longitude, ZoneId zone) {
        int[] times = null;
        try {
            if (!latitude.trim().isEmpty() && !longitude.trim().isEmpty()) {
                times = Sun.riseAndSet(day, Double.parseDouble(latitude.trim()), Double.parseDouble(longitude.trim()), zone);
            }
        } catch (NumberFormatException ignored) {
            // not a number: the defaults
        }
        return times != null ? times : new int[]{DEFAULT_SUNRISE, DEFAULT_SUNSET};
    }

    /** The pictures in the mood's group, in the collection's order; all of them when the group has none. */
    static List<File> select(List<File> all, Map<String, Set<String>> groups, String mood) {
        Set<String> names = groups.get(mood);
        if (names == null && AFTERNOON.equals(mood)) names = groups.get(EVENING);   // themes written before afternoon
        if (names == null) return all;
        List<File> group = new ArrayList<>();
        for (File file : all) if (names.contains(file.getName())) group.add(file);
        return group.isEmpty() ? all : group;
    }
}
