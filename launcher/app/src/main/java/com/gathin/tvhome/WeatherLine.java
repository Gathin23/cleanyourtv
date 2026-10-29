package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * weather_package (config.xml): reads temperature and sky conditions from the Weather add-on's
 * ContentProvider and surfaces them as a one-line string for the home screen header. The launcher
 * stays offline; only the add-on uses the network. Contract: docs/modules.md.
 */
final class WeatherLine {
    static final String META = "com.gathin.tvhome.weather";
    private static final String[] COLUMNS = {"temp", "sky", "unit"};

    private final Context ctx;
    private final Uri uri;
    private final Handler main;
    private final Runnable onChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private boolean active;    // true between start() and stop()
    private int generation;    // incremented each start(); guards late deliveries after stop→start
    private boolean watching;
    private String text;

    private WeatherLine(Context ctx, Uri uri, Handler main, Runnable onChange) {
        this.ctx = ctx;
        this.uri = uri;
        this.main = main;
        this.onChange = onChange;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                read();
            }
        };
    }

    /**
     * Null when the key is empty, the package is not installed, or it names no provider of its own
     * in a {@value #META} meta-data. Naming the package is the add-on's opt-in.
     */
    static WeatherLine create(Context ctx, String pkg, Handler main, Runnable onChange) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg.trim(), PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(authority.trim()).appendPath("now").build();
            return new WeatherLine(ctx, uri, main, onChange);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    void start() {
        active = true;
        generation++;
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on's provider went away; active stays true so the initial read proceeds.
            }
        }
        read();
    }

    void stop() {
        // Set inactive first so any observer onChange() queued on the main handler calls read()
        // but read() returns immediately, and any in-flight lambda is discarded on delivery.
        active = false;
        if (watching) ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    void read() {
        if (!active) return;
        final int gen = generation;
        try {
            io.execute(() -> {
                String result = null;
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        int temp = c.getInt(0);
                        String sky = c.getString(1);
                        if (sky != null && !sky.isEmpty()) {
                            result = temp + "° · " + sky;
                        }
                    }
                } catch (RuntimeException ignored) {
                    // Uninstalled or crashed mid-query: keep what is on screen.
                }
                String finalResult = result;
                main.post(() -> {
                    if (!active || generation != gen) return;
                    text = finalResult;
                    onChange.run();
                });
            });
        } catch (RejectedExecutionException ignored) {
            // Home is being destroyed.
        }
    }

    /** The formatted weather string, e.g. "18° · Partly Cloudy", or null if no data yet. */
    String text() {
        return text;
    }
}
