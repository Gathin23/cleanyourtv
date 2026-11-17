package io.github.smartgenius2.neondream;

import android.content.Context;
import android.content.SharedPreferences;

/** The three options, kept in this app's own preferences. */
final class Prefs {

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences("neon_dream", Context.MODE_PRIVATE);
    }

    /** "rain_city" or "neon_grid". */
    String scene() {
        return "neon_grid".equals(sp.getString("scene", "")) ? "neon_grid" : "rain_city";
    }

    /** "magenta", "amber" or "green". */
    String palette() {
        String p = sp.getString("palette", "magenta");
        return "amber".equals(p) || "green".equals(p) ? p : "magenta";
    }

    boolean clock() {
        return sp.getBoolean("clock", true);
    }

    void cycleScene() {
        sp.edit().putString("scene", "rain_city".equals(scene()) ? "neon_grid" : "rain_city").apply();
    }

    void cyclePalette() {
        String p = palette();
        sp.edit().putString("palette", "magenta".equals(p) ? "amber" : "amber".equals(p) ? "green" : "magenta").apply();
    }

    void toggleClock() {
        sp.edit().putBoolean("clock", !clock()).apply();
    }
}
