package io.github.sumyabazar1.weatherline;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The last reading, kept on disk so the line is on screen the moment Home comes back.
 *
 * A TV that has never had a network shows the placeholder until the first successful fetch.
 */
final class WeatherCache {

    private static final String PREFS = "weather-line";
    private static final String KEY_LINE = "line";
    private static final String KEY_AT = "at";
    /** 30 minutes: Open-Meteo's own model updates about that often, and the launcher polls every 15. */
    static final long FRESH_MS = 30 * 60 * 1000L;

    private WeatherCache() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String line(Context ctx) {
        return prefs(ctx).getString(KEY_LINE, "");
    }

    static boolean isFresh(Context ctx) {
        long at = prefs(ctx).getLong(KEY_AT, 0L);
        return at > 0 && System.currentTimeMillis() - at < FRESH_MS;
    }

    static void save(Context ctx, String line) {
        prefs(ctx).edit().putString(KEY_LINE, line)
                .putLong(KEY_AT, System.currentTimeMillis()).apply();
    }
}
