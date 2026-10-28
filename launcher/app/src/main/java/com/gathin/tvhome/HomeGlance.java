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
 * home_glance (config.xml or a theme): one short line under the clock from an add-on such as Smart Home
 * (temperature and fuel prices). The launcher only reads what the add-on publishes and stays offline.
 * Shown only while the race / Game Day panel is hidden. Contract: docs/modules.md.
 */
final class HomeGlance {
    static final String META = "com.gathin.tvhome.glance";
    /** Closed vocabulary: the launcher draws these; anything else is treated as cloudy. */
    static final String[] ICONS = {"clear", "cloudy", "rain", "snow", "storm", "fog", "night"};
    private static final String[] COLUMNS = {"text", "icon"};

    private final Context ctx;
    private final Uri uri;
    private final Handler main;
    private final Runnable onChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private boolean watching;
    private Line line = Line.EMPTY;

    private HomeGlance(Context ctx, Uri uri, Handler main, Runnable onChange) {
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
     * {@value #META} meta-data. Naming the package is the owner's opt-in.
     */
    static HomeGlance create(Context ctx, String pkg, Handler main, Runnable onChange) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg.trim(), PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim())
                    .appendPath("line").build();
            return new HomeGlance(ctx, uri, main, onChange);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on's provider went away; keep what is on screen.
            }
        }
        read();
    }

    void stop() {
        if (watching) ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    void read() {
        try {
            io.execute(() -> {
                Line next = Line.EMPTY;
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        next = fromRow(c.getString(0), c.getString(1));
                    }
                } catch (RuntimeException gone) {
                    return;
                }
                Line parsed = next;
                main.post(() -> {
                    line = parsed;
                    onChange.run();
                });
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    Line line() {
        return line;
    }

    /** One row from the add-on; empty text hides the line. Icon is normalised to the closed vocabulary. */
    static Line fromRow(String text, String icon) {
        if (text == null || text.trim().isEmpty()) return Line.EMPTY;
        String t = text.trim();
        if (t.length() > 64) t = t.substring(0, 64);
        return new Line(t, normaliseIcon(icon));
    }

    /** Empty when the add-on sent none; unknown names become cloudy. */
    static String normaliseIcon(String icon) {
        if (icon == null || icon.trim().isEmpty()) return "";
        String i = icon.trim().toLowerCase();
        for (String known : ICONS) {
            if (known.equals(i)) return known;
        }
        return "cloudy";
    }

    static final class Line {
        static final Line EMPTY = new Line("", "");
        final String text;
        final String icon;

        Line(String text, String icon) {
            this.text = text;
            this.icon = icon;
        }

        boolean empty() {
            return text.isEmpty();
        }
    }
}
