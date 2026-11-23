package io.github.dpetrasgr.livetvmode;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** The settings, in the app's private preferences, and where Live TV and the home screens are found. */
final class Prefs {
    private Prefs() { }

    /** Live TV apps of the boards this was written for, tried in order when none has been picked. */
    private static final String[] KNOWN_LIVE_TV = {
            "com.mediatek.wwtv.tvcenter",   // MediaTek boards (Vestel, TCL, Hisense and others)
            "com.google.android.tv",        // Google's Live Channels
            "com.android.tv",               // AOSP Live TV
            "com.droidlogic.tvsource",      // Amlogic
            "com.realtek.tv",               // Realtek
    };
    /** HOME claimants that are not home screens: the settings fallback and the setup wizard. */
    private static final String[] NOT_HOME = {
            "com.android.tv.settings", "com.android.settings", "com.google.android.tungsten.setupwraith",
            "com.android.provision",
    };

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) { return p(c).getBoolean("enabled", true); }
    static void setEnabled(Context c, boolean v) { p(c).edit().putBoolean("enabled", v).apply(); }

    static int presses(Context c) {
        int v = p(c).getInt("presses", Mode.MAX_PRESSES);
        return v >= Mode.MIN_PRESSES && v <= Mode.MAX_PRESSES ? v : Mode.MAX_PRESSES;
    }
    static void setPresses(Context c, int v) { p(c).edit().putInt("presses", v).apply(); }

    static boolean wakeFromApps(Context c) { return p(c).getBoolean("wake_from_apps", true); }
    static void setWakeFromApps(Context c, boolean v) { p(c).edit().putBoolean("wake_from_apps", v).apply(); }

    /** The picked Live TV app, or the first known one installed, or "". */
    static String liveTv(Context c) {
        String picked = p(c).getString("live_tv", "");
        if (!picked.isEmpty() && launchIntent(c, picked) != null) return picked;
        for (String pkg : KNOWN_LIVE_TV) if (launchIntent(c, pkg) != null) return pkg;
        return "";
    }
    static void setLiveTv(Context c, String pkg) { p(c).edit().putString("live_tv", pkg).apply(); }

    /** The app's TV entry point, or its phone one; null when it is not installed or disabled. */
    static Intent launchIntent(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i == null ? null : i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** A package that answers HOME and is a home screen, not a settings fallback or a setup wizard. */
    static boolean isHomeScreen(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        for (String not : NOT_HOME) if (not.equals(pkg)) return false;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg);
        return !c.getPackageManager().queryIntentActivities(home, 0).isEmpty();
    }

    /** This app's helper is on the TV's list of enabled accessibility services. */
    static boolean helperOn(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        ComponentName me = new ComponentName(c, LiveTvService.class);
        for (String s : list.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (me.equals(cn)) return true;
        }
        return false;
    }
}
