package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * night_mode (config.xml or a theme): a night-mode add-on such as Brightness on Home. A moon button by day and a sun
 * at night switches it, and wallpaper_moods' night follows it rather than the clock. The launcher only reads one row
 * and starts the add-on's toggle; it changes no setting itself. Contract: docs/modules.md.
 */
final class NightMode {
    static final String META = "com.gathin.tvhome.night";
    static final String ACTION_TOGGLE = "com.gathin.tvhome.action.TOGGLE_NIGHT";

    private final Context ctx;
    private final Uri uri;
    private final Intent toggle;
    private final Handler main;
    private final Runnable onChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Null until the first read, and when the add-on does not answer. */
    private Boolean night;

    private NightMode(Context ctx, Uri uri, Intent toggle, Handler main, Runnable onChange) {
        this.ctx = ctx;
        this.uri = uri;
        this.toggle = toggle;
        this.main = main;
        this.onChange = onChange;
    }

    /**
     * Null when the key is empty, the package is not installed, it names no provider of its own in a {@value #META}
     * meta-data, or it has no activity for {@value #ACTION_TOGGLE}. Naming the package is the owner's opt-in.
     */
    static NightMode create(Context ctx, String pkg, Handler main, Runnable onChange) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        String name = pkg.trim();
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(name, PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            // The provider must be the named package's own, not another app's that claims the authority.
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !name.equals(provider.packageName)) return null;
            ResolveInfo r = pm.resolveActivity(new Intent(ACTION_TOGGLE).setPackage(name), 0);
            if (r == null || r.activityInfo == null) return null;
            ActivityInfo a = r.activityInfo;
            Intent toggle = new Intent(ACTION_TOGGLE).setClassName(a.packageName, a.name)
                    .putExtra("bar", false).putExtra("night", "flip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim()).appendPath("night").build();
            return new NightMode(ctx, uri, toggle, main, onChange);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** True at night, false by day, null when the add-on has not answered. */
    Boolean night() {
        return night;
    }

    /** The add-on's switch to the other side for now: day at night, night by day. */
    Intent toggle() {
        return toggle;
    }

    /** The add-on's own settings, or null when it has no launcher entry. */
    Intent settings() {
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(toggle.getComponent().getPackageName());
        if (i == null) i = pm.getLaunchIntentForPackage(toggle.getComponent().getPackageName());
        return i;
    }

    /** One query off the main thread (the provider lives in the add-on's process), then onChange when it changed. */
    void read() {
        try {
            io.execute(() -> {
                Boolean read = null;
                try (Cursor c = ctx.getContentResolver().query(uri, new String[] {"night"}, null, null, null)) {
                    if (c != null && c.moveToFirst() && !c.isNull(0)) read = c.getInt(0) == 1;
                } catch (RuntimeException gone) {
                    // Uninstalled or crashed mid-query: no answer.
                }
                Boolean result = read;
                main.post(() -> {
                    if (result == null ? night == null : result.equals(night)) return;
                    night = result;
                    onChange.run();
                });
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    void shutdown() {
        io.shutdownNow();
    }
}
