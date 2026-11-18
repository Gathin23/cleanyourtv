package com.cleanyourtv.masjidtimes;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import org.json.JSONObject;

import java.time.ZonedDateTime;

/**
 * The next prayer for the Clean Your TV launcher's panel under the clock (game_day, docs/modules.md). One row:
 * the next start or jamaat. When it passes, Home asks again and gets the one after.
 */
public final class PrayerProvider extends ContentProvider {
    static final String AUTHORITY = "com.cleanyourtv.masjidtimes.turns";
    static final Uri TURNS = Uri.parse("content://" + AUTHORITY + "/turns");
    private static final String[] COLUMNS = {"top_line", "word", "start_ms", "end_ms", "colour", "calendar", "turn_seconds"};
    private static final int GREEN = 0xFF3CBE78;

    static void changed(Context ctx) {
        ctx.getContentResolver().notifyChange(TURNS, null);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!"turns".equals(uri.getLastPathSegment())) return null;
        Context ctx = getContext();
        if (ctx == null) return null;
        MatrixCursor c = new MatrixCursor(COLUMNS);
        c.setNotificationUri(ctx.getContentResolver(), TURNS);
        Timetable.refreshIfStale(ctx, false);
        JSONObject model = Timetable.load(ctx);
        if (model == null) return c;
        Prayers.Event e = Timetable.next(model, ZonedDateTime.now());
        if (e == null) return c;
        // A one-minute window: the panel says "Live now" only briefly, then Home reads the next one.
        c.addRow(new Object[] {e.topLine, e.word, e.atMs, e.atMs + 60_000L, GREEN, "masjid", 30});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".turn";
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
