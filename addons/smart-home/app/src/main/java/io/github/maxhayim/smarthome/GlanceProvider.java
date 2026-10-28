package io.github.maxhayim.smarthome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Temperature, weather icon name and fuel prices for the Clean Your TV launcher's home_glance line
 * (docs/modules.md). Read-only and exported without a permission: it holds only what Home shows
 * (one short line and an icon name), never a token, address or entity id.
 */
public final class GlanceProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.maxhayim.smarthome.glance";
    static final Uri LINE = Uri.parse("content://" + AUTHORITY + "/line");
    static final String[] COLUMNS = {"text", "icon"};

    static void changed(Context ctx) {
        ctx.getContentResolver().notifyChange(LINE, null);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!"line".equals(uri.getLastPathSegment())) return null;
        Context ctx = getContext();
        if (ctx == null) return null;
        long now = System.currentTimeMillis();
        GlanceCache.refreshIfStale(ctx, now);
        GlanceCache cached = GlanceCache.load(ctx);
        MatrixCursor c = new MatrixCursor(COLUMNS);
        if (!cached.text.isEmpty()) {
            c.addRow(new Object[] {cached.text, cached.icon});
        }
        c.setNotificationUri(ctx.getContentResolver(), LINE);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY + ".line";
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
