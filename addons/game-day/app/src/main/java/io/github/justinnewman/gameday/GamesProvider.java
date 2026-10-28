package io.github.justinnewman.gameday;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Each team's next game, for the Clean Your TV launcher's game_day panel (docs/modules.md). Read-only and
 * exported without a permission: it holds only what Home shows anyway (who plays, when, the colour), never a link.
 * One row per calendar, sorted by start; an empty cursor means nothing within a week.
 */
public final class GamesProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.justinnewman.gameday.games";
    static final Uri TURNS = Uri.parse("content://" + AUTHORITY + "/turns");
    static final String[] COLUMNS = {"top_line", "word", "start_ms", "end_ms", "colour", "calendar", "turn_seconds"};

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
        Repository repo = new Repository(ctx);
        long now = System.currentTimeMillis();
        int seconds = repo.turnSeconds();
        MatrixCursor c = new MatrixCursor(COLUMNS);
        for (Schedule.Turn t : repo.turns(now)) {
            c.addRow(new Object[] {t.topLine, t.word, t.startMs, t.endMs, t.colour, t.calendar, seconds});
        }
        c.setNotificationUri(ctx.getContentResolver(), TURNS);
        Background.refresh(ctx, now);
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
