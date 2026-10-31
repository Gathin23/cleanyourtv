package io.github.lostininaka.weatherglance;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * What the launcher reads: content://io.github.lostininaka.weatherglance.now/now, one row, or none until a
 * city is picked. The launcher never writes here and never goes online; it names this package in its
 * header_weather key. Contract: docs/modules.md in the Clean Your TV repo.
 *
 * <p>The provider is exported without a permission, so it holds nothing private: a glyph, a line of weather
 * and when it was fetched. A read whose row is older than {@link Store#STALE_MS} also starts a refresh,
 * which calls notifyChange when the new line is stored.
 */
public final class WeatherProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.lostininaka.weatherglance.now";
    static final Uri NOW = Uri.parse("content://" + AUTHORITY + "/now");
    private static final String[] COLUMNS = {"_id", "icon", "text", "updated_ms"};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        Context ctx = getContext();
        // Answer with the columns the reader asked for, in its order, plus the row id it needs.
        String[] columns = projection == null ? COLUMNS : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        if (ctx == null) return cursor;
        Store store = new Store(ctx);
        String text = store.text();
        if (!text.isEmpty()) {
            MatrixCursor.RowBuilder row = cursor.newRow();
            for (String column : columns) {
                if ("_id".equals(column)) row.add(1);
                else if ("icon".equals(column)) row.add(store.icon());
                else if ("text".equals(column)) row.add(text);
                else if ("updated_ms".equals(column)) row.add(store.updated());
                else row.add(null);
            }
        }
        Refresh.ifStale(ctx);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.io.github.lostininaka.weatherglance.now";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only: the launcher never writes here");
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("read-only: the launcher never writes here");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("read-only: the launcher never writes here");
    }
}
