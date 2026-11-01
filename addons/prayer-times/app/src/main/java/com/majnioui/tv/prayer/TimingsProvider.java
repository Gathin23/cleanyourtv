package com.majnioui.tv.prayer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/** Read-only hand-off of the cached timings to other apps and launcher widgets. */
public class TimingsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.majnioui.tv.prayer.timings";
    private static final String[] COLUMNS = {
            "date", "place", "hijri", "fajr", "dhuhr", "asr", "maghrib", "isha"
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SharedPreferences prefs = getContext().getSharedPreferences(PrayerScheduler.PREFS_NAME, 0);
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.addRow(new Object[]{
                prefs.getString(PrayerScheduler.KEY_DATE, ""),
                prefs.getString(PrayerScheduler.KEY_PLACE, ""),
                prefs.getString(PrayerScheduler.KEY_HIJRI, ""),
                prefs.getString("time_Fajr", ""),
                prefs.getString("time_Dhuhr", ""),
                prefs.getString("time_Asr", ""),
                prefs.getString("time_Maghrib", ""),
                prefs.getString("time_Isha", "")
        });
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.com.majnioui.tv.prayer.timings";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
