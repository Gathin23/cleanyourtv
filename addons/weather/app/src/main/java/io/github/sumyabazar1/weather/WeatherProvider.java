package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Publishes the most-recently-fetched weather to the launcher over IPC (no network in the launcher).
 * The launcher discovers this provider via the meta-data key "com.gathin.tvhome.weather" on
 * WeatherActivity. WeatherActivity writes to shared prefs; this provider reads them back.
 */
public final class WeatherProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.sumyabazar1.weather.now";
    static final Uri URI = Uri.parse("content://" + AUTHORITY + "/now");

    private static final String[] COLUMNS = {"temp", "sky", "unit"};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        SharedPreferences prefs = getContext().getSharedPreferences("place", Context.MODE_PRIVATE);
        if (prefs.contains("weather_temp")) {
            cursor.addRow(new Object[]{
                    prefs.getInt("weather_temp", 0),
                    prefs.getString("weather_sky", ""),
                    prefs.getString("weather_unit", "c")
            });
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.io.github.sumyabazar1.weather.now";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
