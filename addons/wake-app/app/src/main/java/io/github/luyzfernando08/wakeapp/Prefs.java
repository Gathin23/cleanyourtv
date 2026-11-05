package io.github.luyzfernando08.wakeapp;

import android.content.Context;
import android.content.SharedPreferences;

/** The choices made on the settings screen. */
final class Prefs {
    private Prefs() { }

    /** How long to wait after the screen turns on. A box behind HDMI shows a picture several seconds later. */
    static final int[] DELAYS = {0, 3, 5, 10, 15};

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context ctx) {
        return prefs(ctx).getBoolean("enabled", true);
    }

    static void setEnabled(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean("enabled", value).apply();
    }

    /** The package to open, or "" when none has been chosen yet. */
    static String app(Context ctx) {
        return prefs(ctx).getString("app", "");
    }

    /** Changing the app drops the link: a link belongs to the app it was meant for. */
    static void setApp(Context ctx, String value) {
        prefs(ctx).edit().putString("app", value).putString("link", "").apply();
    }

    /** An optional address for the app to open (a video, a channel), or "" to open the app itself. */
    static String link(Context ctx) {
        return prefs(ctx).getString("link", "");
    }

    static void setLink(Context ctx, String value) {
        prefs(ctx).edit().putString("link", value).apply();
    }

    static int delay(Context ctx) {
        return prefs(ctx).getInt("delay", 0);
    }

    static void setDelay(Context ctx, int value) {
        prefs(ctx).edit().putInt("delay", value).apply();
    }
}
