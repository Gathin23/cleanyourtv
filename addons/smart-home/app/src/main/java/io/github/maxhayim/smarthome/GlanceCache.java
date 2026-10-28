package io.github.maxhayim.smarthome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

import java.util.List;

/**
 * Last glance line for the Home content provider: text and icon only, refreshed when something asks
 * and the copy is older than {@link #STALE_MS}. No token or address leaves this app.
 */
final class GlanceCache {
    static final long STALE_MS = 15 * 60_000L;

    final String text;
    final String icon;
    final long fetchedAt;

    GlanceCache(String text, String icon, long fetchedAt) {
        this.text = text == null ? "" : text;
        this.icon = icon == null ? "" : icon;
        this.fetchedAt = fetchedAt;
    }

    boolean stale(long nowMs) {
        return nowMs - fetchedAt >= STALE_MS;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("glance", Context.MODE_PRIVATE);
    }

    static GlanceCache load(Context c) {
        SharedPreferences p = prefs(c);
        return new GlanceCache(p.getString("text", ""), p.getString("icon", ""), p.getLong("at", 0));
    }

    static void save(Context c, Ha.Glance g) {
        prefs(c).edit()
                .putString("text", g.text)
                .putString("icon", g.icon)
                .putLong("at", System.currentTimeMillis())
                .apply();
        GlanceProvider.changed(c);
    }

    /** Rebuild from a states reply already in hand (the open screen), and publish for Home. */
    static void fromStates(Context c, String body, List<String> glanceIds) throws JSONException {
        save(c, Ha.glance(Ha.parse(body), glanceIds));
    }

    /**
     * When Home (or the provider) asks: return the cache at once, and fetch Home Assistant only when
     * the copy is stale and the server is configured. Returns whether a network refresh was started.
     */
    static boolean refreshIfStale(Context c, long nowMs) {
        GlanceCache cached = load(c);
        if (!cached.stale(nowMs) && !cached.text.isEmpty()) return false;
        Config config = Config.load(c);
        if (!config.complete()) return false;
        Background.fetch(c, config);
        return true;
    }
}
