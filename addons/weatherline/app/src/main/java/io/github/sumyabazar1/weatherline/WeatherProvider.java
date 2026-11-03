package io.github.sumyabazar1.weatherline;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * content://io.github.sumyabazar1.weatherline/today, one row with a "line" column.
 *
 * It never goes online itself: the answer is the cached reading, returned at once, and a stale cache
 * starts a background refresh for the next read. That keeps the launcher's first frame fast and lets the
 * line survive a reboot before the network is back.
 */
public final class WeatherProvider extends ContentProvider {

    static final String AUTHORITY = "io.github.sumyabazar1.weatherline";
    private static final String[] COLUMNS = {"line"};

    @Override
    public boolean onCreate() {
        WeatherRefreshJob.schedule(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        MatrixCursor cursor = new MatrixCursor(COLUMNS, 1);
        String line = ctx == null ? "" : WeatherCache.line(ctx);
        cursor.addRow(new Object[]{line == null ? "" : line});
        if (ctx != null && !WeatherCache.isFresh(ctx)) WeatherRefreshJob.runNow(ctx);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/weather-line";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
