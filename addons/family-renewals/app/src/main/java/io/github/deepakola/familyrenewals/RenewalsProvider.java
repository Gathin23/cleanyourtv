package io.github.deepakola.familyrenewals;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class RenewalsProvider extends ContentProvider {
    public static final Uri TURNS_URI = Uri.parse(
            "content://io.github.deepakola.familyrenewals.games/turns");
    private static final String[] COLUMNS = {
            "top_line", "word", "start_ms", "end_ms", "colour", "calendar", "turn_seconds"
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (uri == null || !"turns".equals(uri.getLastPathSegment())) return null;
        Context context = getContext();
        if (context == null) return null;

        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        cursor.setNotificationUri(context.getContentResolver(), TURNS_URI);
        if (!RenewalStore.countdownEnabled(context)) return cursor;

        LocalDate today = LocalDate.now();
        LocalDate nearest = null;
        List<RenewalEntry> entries = RenewalStore.load(context);
        for (RenewalEntry entry : entries) {
            LocalDate due = DateRules.parse(entry.dueDate);
            if (due == null) continue;
            long days = DateRules.daysUntil(entry.dueDate, today);
            if (days >= 0 && days <= 7 && (nearest == null || due.isBefore(nearest))) {
                nearest = due;
            }
        }

        if (nearest != null) {
            ZoneId zone = ZoneId.systemDefault();
            long start = nearest.atStartOfDay(zone).toInstant().toEpochMilli();
            long end = nearest.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            // Only generic information crosses the exported provider boundary.
            cursor.addRow(new Object[] {
                    "Family renewal", "Due", start, end, 0xFF43B9A5, "Renewals", 30
            });
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.io.github.deepakola.familyrenewals.turn";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }
}
