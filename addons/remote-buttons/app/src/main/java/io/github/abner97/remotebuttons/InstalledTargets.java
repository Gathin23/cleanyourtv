package io.github.abner97.remotebuttons;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** What a button can open (TV Settings, Home and each installed TV app), named as the owner reads them. */
final class InstalledTargets {
    /** One choice in the card's app list. */
    static final class Target {
        /** What keys.txt stores: a package name, {@link KeyMap#SETTINGS_TARGET} or {@link KeyMap#HOME_TARGET}. */
        final String value;
        /** What the owner sees: the app's name, "TV Settings" or "Home". */
        final String label;

        /**
         * Creates one choice.
         *
         * @param value what keys.txt stores for this choice
         * @param label what the owner sees in the list
         */
        Target(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    /** Static helpers only; not instantiated. */
    private InstalledTargets() { }

    /**
     * Every target a button can be given: TV Settings and Home first, then the TV apps (those with a leanback
     * launcher entry, as the TV's own app row shows them) sorted by name. This add-on itself is left out.
     *
     * @param context any context of this app
     * @return the choices, in the order the list shows them
     */
    static List<Target> all(Context context) {
        List<Target> targets = new ArrayList<>();
        targets.add(new Target(KeyMap.SETTINGS_TARGET, context.getString(R.string.target_settings)));
        targets.add(new Target(KeyMap.HOME_TARGET, context.getString(R.string.target_home)));
        targets.addAll(tvAppsByName(context));
        return targets;
    }

    /**
     * A target as the owner reads it.
     *
     * @param context any context of this app
     * @param target a package name, {@link KeyMap#SETTINGS_TARGET} or {@link KeyMap#HOME_TARGET}
     * @return the app's name (marked when disabled or not installed), or what the named target opens
     */
    static String describe(Context context, String target) {
        if (target.equals(KeyMap.SETTINGS_TARGET)) return context.getString(R.string.target_settings);
        if (target.equals(KeyMap.HOME_TARGET)) return context.getString(R.string.target_home);
        PackageManager packageManager = context.getPackageManager();
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(target, 0);
            String appName = packageManager.getApplicationLabel(appInfo).toString();
            return appInfo.enabled ? appName : appName + context.getString(R.string.disabled);
        } catch (PackageManager.NameNotFoundException notInstalled) {
            return target + context.getString(R.string.not_installed);
        }
    }

    /**
     * The installed, enabled TV apps, one entry per package, sorted by name.
     *
     * @param context any context of this app
     * @return one target per TV app
     */
    private static List<Target> tvAppsByName(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent tvAppEntries = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Set<String> packagesSeen = new HashSet<>();
        List<Target> tvApps = new ArrayList<>();
        for (ResolveInfo appEntry : packageManager.queryIntentActivities(tvAppEntries, 0)) {
            String packageName = appEntry.activityInfo.packageName;
            if (packageName.equals(context.getPackageName()) || !packagesSeen.add(packageName)) continue;
            tvApps.add(new Target(packageName, appEntry.loadLabel(packageManager).toString()));
        }
        tvApps.sort(Comparator.comparing(app -> app.label.toLowerCase(Locale.ROOT)));
        return tvApps;
    }
}
