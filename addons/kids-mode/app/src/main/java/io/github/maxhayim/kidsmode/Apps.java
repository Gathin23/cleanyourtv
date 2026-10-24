package io.github.maxhayim.kidsmode;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The installed apps, the home apps and the keyboards, as the package manager reports them. */
final class Apps {
    static final class App {
        final String pkg;
        final String label;
        final ComponentName component;

        App(String pkg, String label, ComponentName component) {
            this.pkg = pkg;
            this.label = label;
            this.component = component;
        }
    }

    private Apps() {
    }

    /** Every app with a TV launcher entry except this one, by name. */
    static List<App> tvApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, App> out = new LinkedHashMap<>();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || out.containsKey(pkg)) continue;
            out.put(pkg, new App(pkg, String.valueOf(ri.loadLabel(pm)), new ComponentName(pkg, ri.activityInfo.name)));
        }
        List<App> list = new ArrayList<>(out.values());
        list.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }

    /** The apps that start on the kids' screen before the parent chooses: YouTube Kids and children's publishers. */
    static Set<String> defaults(Context c) {
        Set<String> out = new HashSet<>();
        for (App a : tvApps(c)) if (Kids.kidsApp(a.pkg, a.label)) out.add(a.pkg);
        return out;
    }

    /** Home apps other than this one: the normal home to hand over to. */
    static List<App> otherHomes(Context c) {
        PackageManager pm = c.getPackageManager();
        List<App> out = new ArrayList<>();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName())) continue;
            // The settings app's FallbackHome is only for the first boot.
            if (ri.activityInfo.name.endsWith("FallbackHome")) continue;
            out.add(new App(pkg, String.valueOf(ri.loadLabel(pm)), new ComponentName(pkg, ri.activityInfo.name)));
        }
        return out;
    }

    /** True when Home opens this app. */
    static boolean isHome(Context c) {
        ResolveInfo ri = c.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
    }

    /** True while the guard is switched on in the accessibility settings. */
    static boolean guardOn(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }

    static Set<String> keyboards(Context c) {
        Set<String> out = new HashSet<>();
        InputMethodManager imm = c.getSystemService(InputMethodManager.class);
        if (imm != null) for (InputMethodInfo i : imm.getEnabledInputMethodList()) out.add(i.getPackageName());
        return out;
    }

    static Drawable art(Context c, App a) {
        PackageManager pm = c.getPackageManager();
        try {
            Drawable d = pm.getActivityBanner(a.component);
            if (d == null) d = pm.getApplicationBanner(a.pkg);
            if (d == null) d = pm.getActivityIcon(a.component);
            return d;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
