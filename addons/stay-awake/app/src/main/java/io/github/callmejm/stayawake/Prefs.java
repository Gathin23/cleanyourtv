package io.github.callmejm.stayawake;

import android.content.Context;

/** The owner's on/off choice. Off until they turn it on. */
final class Prefs {
    private static final String FILE = "stay";
    private static final String ON = "on";

    private Prefs() {}

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(ON, false);
    }

    static void setEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(ON, on).apply();
    }
}
