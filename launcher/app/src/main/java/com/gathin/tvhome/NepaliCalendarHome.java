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
import android.util.Log;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Reads the optional Nepali calendar add-on's local cache; Home never accesses the network. */
final class NepaliCalendarHome {
    private static final String TAG = "TvHomeNepaliCalendar";
    private static final String META = "com.gathin.tvhome.nepali_calendar";
    private static final String[] COLUMNS = {"bs_date", "tithi"};
    private static final long REFRESH_INTERVAL_MS = 30 * 60_000L;

    private final Context context;
    private final Uri uri;
    private final Handler main;
    private final Runnable onChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private final Runnable refreshTick = new Runnable() {
        @Override public void run() {
            read();
            scheduleRefresh();
        }
    };
    private boolean watching;
    private String date = "";
    private String tithi = "";

    private NepaliCalendarHome(Context context, Uri uri, Handler main, Runnable onChange) {
        this.context = context;
        this.uri = uri;
        this.main = main;
        this.onChange = onChange;
        observer = new ContentObserver(main) {
            @Override public void onChange(boolean selfChange) { read(); }
        };
    }

    /** Null unless the opted-in package owns the provider it declares. */
    static NepaliCalendarHome create(Context context, String packageName, Handler main, Runnable onChange) {
        if (packageName == null || packageName.trim().isEmpty()) return null;
        String pkg = packageName.trim();
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo app = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            String authority = app.metaData == null ? null : app.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) {
                Log.w(TAG, pkg + " does not declare " + META);
                return null;
            }
            authority = authority.trim();
            ProviderInfo provider = pm.resolveContentProvider(authority, 0);
            if (provider == null || !pkg.equals(provider.packageName) || !provider.exported) {
                Log.w(TAG, "Ignoring unowned or non-exported provider " + authority);
                return null;
            }
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority)
                    .appendPath("today").build();
            return new NepaliCalendarHome(context, uri, main, onChange);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "Nepali calendar add-on is not installed: " + pkg);
            return null;
        }
    }

    void start() {
        if (!watching) {
            try {
                context.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                Log.w(TAG, "Could not watch the calendar provider: " + e.getMessage());
            }
        }
        read();
        scheduleRefresh();
    }

    void stop() {
        main.removeCallbacks(refreshTick);
        if (watching) context.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    String date() { return date; }
    String tithi() { return tithi; }

    private void scheduleRefresh() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone());
        long untilMidnight = Duration.between(now, midnight).toMillis() + 1_000L;
        long delay = Math.max(1_000L, Math.min(REFRESH_INTERVAL_MS, untilMidnight));
        main.removeCallbacks(this.refreshTick);
        main.postDelayed(this.refreshTick, delay);
    }

    private void read() {
        try {
            io.execute(() -> {
                String newDate;
                String newTithi;
                try {
                    context.getContentResolver().call(uri.getAuthority(), "refresh", null, null);
                    try (Cursor cursor = context.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                        if (cursor == null || !cursor.moveToFirst()) {
                            throw new IllegalStateException("Calendar provider returned no row");
                        }
                        newDate = cursor.getString(cursor.getColumnIndexOrThrow("bs_date"));
                        newTithi = cursor.getString(cursor.getColumnIndexOrThrow("tithi"));
                    }
                } catch (RuntimeException e) {
                    Log.w(TAG, "Could not read the calendar provider: " + e.getMessage());
                    return;
                }
                String safeDate = newDate == null ? "" : newDate.trim();
                String safeTithi = newTithi == null ? "" : newTithi.trim();
                main.post(() -> {
                    date = safeDate;
                    tithi = safeTithi;
                    onChange.run();
                });
            });
        } catch (RejectedExecutionException e) {
            Log.i(TAG, "Home is shutting down; skipping calendar refresh");
        }
    }
}
