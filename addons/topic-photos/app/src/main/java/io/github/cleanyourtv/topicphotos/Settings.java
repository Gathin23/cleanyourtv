package io.github.cleanyourtv.topicphotos;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The owner's choices, kept in the app's own storage: the topics, the pace and day-and-night photos. */
final class Settings {

    static final List<String> DEFAULT_TOPICS = Arrays.asList("mountain landscape", "ocean waves", "misty forest",
            "city skyline at night", "starry night sky", "waterfall", "desert dunes", "northern lights");
    /** Choices for how often the photo changes, in seconds. */
    static final int[] INTERVALS = {10, 20, 30, 60, 120, 300, 600, 1800};
    static final int DEFAULT_INTERVAL = 30;
    /** Daylight is 07:00 to 19:00 by the TV's clock: no location is asked for. */
    static final int DAY_START = 7 * 60, DAY_END = 19 * 60;

    private static final String TOPICS = "topics", INTERVAL = "interval", DAYNIGHT = "day_night";

    private Settings() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** The topics typed in settings, comma-separated, or a varied default when there are none. */
    static List<String> topics(Context c) {
        List<String> out = parseTopics(prefs(c).getString(TOPICS, ""));
        return out.isEmpty() ? DEFAULT_TOPICS : out;
    }

    static String typedTopics(Context c) {
        return prefs(c).getString(TOPICS, "");
    }

    static void setTopics(Context c, String text) {
        prefs(c).edit().putString(TOPICS, text.trim()).apply();
    }

    /** Up to eight topics of at most 80 characters, blanks and repeats dropped. */
    static List<String> parseTopics(String text) {
        List<String> out = new ArrayList<>();
        for (String t : text.split(",")) {
            String s = t.trim().replaceAll("\\s+", " ");
            if (s.length() > 80) s = s.substring(0, 80);
            if (!s.isEmpty() && !out.contains(s) && out.size() < 8) out.add(s);
        }
        return out;
    }

    static int interval(Context c) {
        return prefs(c).getInt(INTERVAL, DEFAULT_INTERVAL);
    }

    static void setInterval(Context c, int seconds) {
        prefs(c).edit().putInt(INTERVAL, seconds).apply();
    }

    static boolean dayNight(Context c) {
        return prefs(c).getBoolean(DAYNIGHT, false);
    }

    static void setDayNight(Context c, boolean on) {
        prefs(c).edit().putBoolean(DAYNIGHT, on).apply();
    }

    /** "day" or "night" now, or "" with day-and-night photos off. */
    static String daypart(Context c) {
        return dayNight(c) ? daypart(java.time.LocalTime.now().toSecondOfDay() / 60) : "";
    }

    static String daypart(int minuteOfDay) {
        return minuteOfDay >= DAY_START && minuteOfDay < DAY_END ? "day" : "night";
    }
}
