package io.github.maxhayim.brightness;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Calendar;

/**
 * Read-only, for Clean Your TV's night_mode key (contract in docs/modules.md): whether it is night for Brightness
 * right now, the master switch, night mode and its schedule together, so Home can show a sun or a moon and pick its
 * night wallpapers. content://io.github.maxhayim.brightness.night/night answers one row with a "night" column, 1 or 0.
 * It reveals nothing else and changes nothing.
 */
public final class NightStateProvider extends ContentProvider {
    static final String[] COLUMNS = {"night"};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Dimming.Settings s = Prefs.load(getContext());
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        MatrixCursor c = new MatrixCursor(COLUMNS);
        c.addRow(new Object[] {s.on && Dimming.nightNow(s, minute) ? 1 : 0});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return null;
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
