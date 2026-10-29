package io.github.luyzfernando08.tvstatic;

import android.content.Context;
import android.content.SharedPreferences;

/** The choices made on the settings screen. */
final class Prefs {
    private Prefs() { }

    /** A box behind HDMI can take several seconds to show a picture after waking; 10 s covers that. */
    static final int[] SECONDS = {3, 5, 10};

    static final int STYLE_SNOW = 0, STYLE_BARS = 1, STYLE_NO_SIGNAL = 2, STYLE_RANDOM = 3;
    static final int STYLES = 4;

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context ctx) {
        return prefs(ctx).getBoolean("enabled", true);
    }

    static boolean sound(Context ctx) {
        return prefs(ctx).getBoolean("sound", true);
    }

    static int seconds(Context ctx) {
        return prefs(ctx).getInt("seconds", 3);
    }

    static int style(Context ctx) {
        int style = prefs(ctx).getInt("style", STYLE_SNOW);
        return style >= 0 && style < STYLES ? style : STYLE_SNOW;
    }

    static void setStyle(Context ctx, int value) {
        prefs(ctx).edit().putInt("style", value).apply();
    }

    static void setEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean("enabled", value).apply();
    }

    static void setSound(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean("sound", value).apply();
    }

    static void setSeconds(Context ctx, int value) {
        prefs(ctx).edit().putInt("seconds", value).apply();
    }
}
