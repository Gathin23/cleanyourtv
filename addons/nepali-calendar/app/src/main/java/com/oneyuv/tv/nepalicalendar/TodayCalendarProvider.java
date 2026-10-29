package com.oneyuv.tv.nepalicalendar;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.time.LocalDate;

/** Publishes today's cached Nepali date and tithi to Home; only this add-on talks to the network. */
public final class TodayCalendarProvider extends ContentProvider {
    static final String AUTHORITY = "com.oneyuv.tv.nepalicalendar.today";
    static final Uri TODAY = Uri.parse("content://" + AUTHORITY + "/today");
    static final String META = "com.gathin.tvhome.nepali_calendar";
    static final String[] COLUMNS = {"bs_date", "tithi"};
    private static final String TAG = "NepaliCalendarData";
    private static final String PREFS = "today_calendar";
    private static final String KEY_DATE = "gregorian_date";
    private static final String KEY_TITHI = "tithi";
    private static final String KEY_ATTEMPT_DATE = "attempt_date";
    private static final String KEY_LAST_ATTEMPT = "last_attempt";
    private static final long RETRY_INTERVAL_MS = 30 * 60_000L;
    private final Object refreshLock = new Object();
    private boolean refreshing;

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        requireToday(uri);
        Context context = getContext();
        if (context == null) throw new IllegalStateException("Provider is not attached to a context");
        LocalDate date = LocalDate.now();
        BikramSambat bs = BikramSambat.of(date);
        String bsDate = bs == null ? "" : NepaliDate.homeText(bs);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String tithi = date.toString().equals(prefs.getString(KEY_DATE, ""))
                ? prefs.getString(KEY_TITHI, "") : "";
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(new Object[]{bsDate, tithi});
        cursor.setNotificationUri(context.getContentResolver(), TODAY);
        return cursor;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"refresh".equals(method)) throw new IllegalArgumentException("Unsupported method: " + method);
        requestRefresh();
        return Bundle.EMPTY;
    }

    private void requestRefresh() {
        Context context = getContext();
        if (context == null) throw new IllegalStateException("Provider is not attached to a context");
        LocalDate date = LocalDate.now();
        BikramSambat bs = BikramSambat.of(date);
        if (bs == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (date.toString().equals(prefs.getString(KEY_DATE, ""))
                && !prefs.getString(KEY_TITHI, "").isEmpty()) return;
        long lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0);
        if (date.toString().equals(prefs.getString(KEY_ATTEMPT_DATE, ""))
                && now >= lastAttempt && now - lastAttempt < RETRY_INTERVAL_MS) return;
        synchronized (refreshLock) {
            if (refreshing) return;
            refreshing = true;
        }
        prefs.edit().putString(KEY_ATTEMPT_DATE, date.toString()).putLong(KEY_LAST_ATTEMPT, now).apply();
        Thread worker = new Thread(() -> {
            try {
                String tithi = NepaliCalendarApi.fetch(bs.year, bs.month, bs.day);
                prefs.edit().putString(KEY_DATE, date.toString()).putString(KEY_TITHI, tithi).apply();
                context.getContentResolver().notifyChange(TODAY, null);
            } catch (IOException | JSONException e) {
                Log.w(TAG, "Could not refresh today's tithi: " + e.getMessage());
            } finally {
                synchronized (refreshLock) { refreshing = false; }
                if (!date.equals(LocalDate.now())) requestRefresh();
            }
        }, "nepali-calendar-api");
        worker.setDaemon(true);
        worker.start();
    }

    private static void requireToday(Uri uri) {
        if (!"today".equals(uri.getLastPathSegment())) {
            throw new IllegalArgumentException("Unsupported calendar URI: " + uri);
        }
    }

    @Override public String getType(Uri uri) {
        requireToday(uri);
        return "vnd.android.cursor.item/vnd." + AUTHORITY + ".today";
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
