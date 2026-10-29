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
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * weather_now (config.xml or a theme): the weather now under the clock, a sky glyph, the temperature and the place
 * ("☁  14°  Vigo"), from an add-on such as Weather. It takes the stats line's place, so it shows only without
 * show_stats. The launcher only reads what the add-on publishes and stays offline. Contract: docs/modules.md.
 */
final class WeatherNow {
    static final String META = "com.gathin.tvhome.weather";
    /** A reading older than this is not the weather now any more: the line hides until a newer one. */
    static final long STALE_MS = 3 * 60 * 60_000L;
    private static final String[] COLUMNS = {"place", "temperature", "unit", "code", "is_day", "updated_ms"};

    /** One reading, as the add-on publishes it. */
    static final class Reading {
        final String place;
        final int temperature;
        final int code;
        final boolean day;
        final long updatedMs;

        Reading(String place, int temperature, int code, boolean day, long updatedMs) {
            this.place = place;
            this.temperature = temperature;
            this.code = code;
            this.day = day;
            this.updatedMs = updatedMs;
        }

        /** "14°  Vigo"; the unit is the add-on's, and the place may be empty. */
        String text() {
            return place.isEmpty() ? temperature + "°" : temperature + "°  " + place;
        }
    }

    private final Context ctx;
    private final TextView view;
    private final Uri uri;
    private final Handler main;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private boolean watching;
    private Reading reading;

    private WeatherNow(Context ctx, TextView view, Uri uri, Handler main) {
        this.ctx = ctx;
        this.view = view;
        this.uri = uri;
        this.main = main;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                read();
            }
        };
    }

    /**
     * Null when the key is empty, the package is not installed, or it names no provider of its own in a
     * {@value #META} meta-data. Naming the package is the owner's opt-in.
     */
    static WeatherNow create(Context ctx, String pkg, TextView view, Handler main) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg.trim(), PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            // The provider must be the named package's own, not another app's that claims the authority.
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim()).appendPath("now").build();
            float dp = ctx.getResources().getDisplayMetrics().density;
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            view.setPadding(0, Math.round(4 * dp), 0, 0);
            return new WeatherNow(ctx, view, uri, main);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Home is on screen: show the last reading, ask for a new one and watch for changes. */
    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on's provider went away; the line keeps what it had.
            }
        }
        show();
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

    /** One query off the main thread (the provider lives in the add-on's process), then the line on the main thread. */
    private void read() {
        try {
            io.execute(() -> {
                Object[] row = null;
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        row = new Object[] {c.getString(0), c.getInt(1), c.getString(2), c.getInt(3), c.getInt(4), c.getLong(5)};
                    }
                } catch (RuntimeException gone) {
                    // Uninstalled or crashed mid-query: keep what is on screen.
                    return;
                }
                Reading parsed = fromRow(row);
                main.post(() -> {
                    reading = parsed;
                    show();
                });
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    private void show() {
        Reading r = reading;
        if (r == null || System.currentTimeMillis() - r.updatedMs > STALE_MS) {
            view.setVisibility(View.GONE);
            return;
        }
        String text = r.text();
        view.setText(sky(r.code, r.day) + "  " + text);
        view.setContentDescription(text);
        view.setVisibility(View.VISIBLE);
    }

    /** The add-on's row as a reading; null for no row, no temperature or no time. */
    static Reading fromRow(Object[] r) {
        if (r == null || !(r[1] instanceof Number) || !(r[5] instanceof Number)) return null;
        long updated = ((Number) r[5]).longValue();
        if (updated <= 0) return null;
        String place = r[0] instanceof String ? ((String) r[0]).trim() : "";
        int code = r[3] instanceof Number ? ((Number) r[3]).intValue() : 3;
        boolean day = !(r[4] instanceof Number) || ((Number) r[4]).intValue() != 0;
        return new Reading(place, ((Number) r[1]).intValue(), code, day, updated);
    }

    /** WMO weather code to a sky symbol, drawn as text (U+FE0E asks for the plain glyph, not a colour emoji). */
    static String sky(int code, boolean day) {
        if (code == 0 || code == 1) return day ? "\u2600\uFE0E" : "\u263E";
        if (code == 2 && day) return "\u26C5\uFE0E";
        if (code == 45 || code == 48) return "\u2248";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "\u2744\uFE0E";
        if (code >= 95) return "\u26A1\uFE0E";
        if (code >= 51) return "\u2602\uFE0E";
        return "\u2601\uFE0E";
    }
}
