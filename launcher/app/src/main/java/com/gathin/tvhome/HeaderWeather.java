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
 * header_weather (config.xml or a theme): the weather line under the clock, from an add-on such as Weather
 * Glance. The launcher only reads what the add-on publishes and stays offline - the add-on does the fetching,
 * exactly as Game Day does for game_day. Contract: docs/modules.md.
 */
final class HeaderWeather {
    static final String META = "com.gathin.tvhome.weather";
    private static final String[] COLUMNS = {"icon", "text"};

    private final Context ctx;
    private final Uri uri;
    private final Handler main;
    private final Runnable onChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private boolean watching;
    private Line line;

    private HeaderWeather(Context ctx, Uri uri, Handler main, Runnable onChange) {
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
     * Null when the key is empty, the package is not installed, or it names no provider of its own in a
     * {@value #META} meta-data. Naming the package is the owner's opt-in, as for game_day.
     */
    static HeaderWeather create(Context ctx, String pkg, Handler main, Runnable onChange) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg.trim(), PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            // The provider must be the named package's own, not another app's that claims the authority.
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim())
                    .appendPath("now").build();
            return new HeaderWeather(ctx, uri, main, onChange);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Home is on screen: read now and watch for changes. */
    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on's provider went away; the line keeps what it had.
            }
        }
        read();
    }

    /** Home is off screen: nothing runs until it is back. */
    void stop() {
        if (watching) ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    /** One query off the main thread (the provider lives in the add-on's process), then onChange on the main thread. */
    void read() {
        try {
            io.execute(() -> {
                Line found = null;
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    // By name, not by position: a provider may answer with the columns in its own order.
                    if (c != null && c.moveToNext()) {
                        int textAt = c.getColumnIndex("text");
                        int iconAt = c.getColumnIndex("icon");
                        String text = textAt < 0 ? null : c.getString(textAt);
                        if (text != null && !text.isEmpty()) {
                            String icon = iconAt < 0 ? null : c.getString(iconAt);
                            found = new Line(icon == null ? "" : icon, text);
                        }
                    }
                } catch (RuntimeException gone) {
                    // Uninstalled or crashed mid-query: keep what is on screen.
                    return;
                }
                Line parsed = found;
                main.post(() -> {
                    line = parsed;
                    onChange.run();
                });
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    /** The line to show, or null for none: no key, no add-on, or nothing published yet. */
    Line line() {
        return line;
    }

    /** One row of the add-on's cursor: an optional glyph and the text under the clock. */
    static final class Line {
        final String icon;
        final String text;

        Line(String icon, String text) {
            this.icon = icon;
            this.text = text;
        }
    }
}
