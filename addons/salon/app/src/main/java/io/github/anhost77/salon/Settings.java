package io.github.anhost77.salon;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * What the household chose in Réglages: the TMDB token, the platforms, guide channels and radios switched off, and the
 * home photo's version. Kept in the app's private preferences, never in the sources or the repo.
 */
final class Settings {
    static final String PREFS = "settings";
    static final String PLATFORMS = "off_platforms", CHANNELS = "off_channels", RADIOS = "off_radios";

    private static volatile Context app;

    private Settings() { }

    /** Called from {@link App#attachBaseContext}, before any provider or screen runs. */
    static void init(Context ctx) {
        app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
    }

    private static SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The token typed in Réglages, else the one the build carried (work/tmdb.properties), else "". */
    static String tmdbToken() {
        String typed = app == null ? "" : prefs().getString("tmdb_token", "");
        return typed.isEmpty() ? BuildConfig.TMDB_TOKEN : typed;
    }

    static void setTmdbToken(String token) {
        prefs().edit().putString("tmdb_token", token == null ? "" : token.trim()).apply();
    }

    static boolean off(String list, String id) {
        return app != null && prefs().getStringSet(list, new HashSet<>()).contains(id);
    }

    static void setOff(String list, String id, boolean off) {
        Set<String> s = new HashSet<>(prefs().getStringSet(list, new HashSet<>()));
        if (off) s.add(id);
        else s.remove(id);
        prefs().edit().putStringSet(list, s).apply();
    }

    static long photoVersion() {
        return prefs().getLong("photo_version", 0);
    }

    static void setPhotoVersion(long v) {
        prefs().edit().putLong("photo_version", v).apply();
    }

    /**
     * Direct links to the platforms' title pages through JustWatch's own (unofficial) API. Off by default: without it
     * only Wikidata (open data) is asked, Netflix still opens its title pages, the others open on their home.
     */
    static boolean justWatch() {
        return app != null && prefs().getBoolean("justwatch", false);
    }

    static void setJustWatch(boolean on) {
        prefs().edit().putBoolean("justwatch", on).apply();
    }
}
