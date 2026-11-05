package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** open_on_start_choice: the app Home opens once after each boot, picked in Launcher settings. */
final class OpenOnStart {
    /** The picked package in Config.SETTINGS_NAME; absent or empty means Off. */
    static final String KEY = "open_on_start";
    /** The boot whose first Home has been handled, shared with boot_to_live_tv. */
    static final String BOOT_PREFS = "boot";
    static final String BOOT_KEY = "live_tv_boot";

    private OpenOnStart() {
    }

    /** Whether the first Home of this boot opens {@code pkg}: a known boot not handled yet, and an app that is not
     *  the launcher itself (which would only reopen Home). */
    static boolean due(int boot, int handledBoot, String pkg, String self) {
        return boot >= 0 && boot != handledBoot && pkg != null && !pkg.isEmpty() && !pkg.equals(self);
    }

    static int boot(Context ctx) {
        return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    /** A pick takes effect from the next boot: the Home that follows the pick stays Home. */
    static void skipThisBoot(Context ctx) {
        ctx.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE).edit().putInt(BOOT_KEY, boot(ctx)).commit();
    }

    /** The app's TV entry point, or its phone one; null when it is uninstalled or disabled, so Home stays. */
    static Intent intent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }
}
