package io.github.savioronezero.worldclock;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

/** Same city/format keys as 0.1; screensaver options never change the TV's system settings. */
final class ClockPreferences {
    final List<String> ids;
    final int format, brightness, background;
    final boolean motion;

    static SharedPreferences store(Context context) {
        return context.getSharedPreferences("world_clock", Context.MODE_PRIVATE);
    }

    ClockPreferences(SharedPreferences preferences) {
        List<String> saved = new ArrayList<>();
        try {
            String raw = preferences.getString("zones_v1", "[]");
            if (raw != null && raw.length() < 8192) {
                JSONArray values = new JSONArray(raw);
                for (int i = 0; i < Math.min(values.length(), 100); i++) {
                    Object id = values.get(i);
                    if (id instanceof String) saved.add((String) id);
                }
            }
        } catch (JSONException | ClassCastException ignored) {
            // Keep documented defaults when local data is damaged.
        }
        ids = ClockModel.restore(saved);
        format = integer(preferences, "format_v1", 0, 2, 0);
        brightness = integer(preferences, "dream_brightness_v1", 0, 2, 1);
        background = integer(preferences, "dream_background_v1", 0, 3, 2);
        motion = bool(preferences, "dream_motion_v1", true);
    }

    private static int integer(SharedPreferences p, String key, int min, int max, int fallback) {
        try {
            int value = p.getInt(key, fallback);
            return value >= min && value <= max ? value : fallback;
        } catch (ClassCastException ignored) { return fallback; }
    }

    private static boolean bool(SharedPreferences p, String key, boolean fallback) {
        try { return p.getBoolean(key, fallback); }
        catch (ClassCastException ignored) { return fallback; }
    }
}
