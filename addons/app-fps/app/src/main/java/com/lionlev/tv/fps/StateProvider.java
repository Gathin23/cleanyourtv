package com.lionlev.tv.fps;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/** Shell-only, fixed-schema status endpoint. No commands or writable settings. */
public final class StateProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        if (!"/state".equals(uri.getPath()) || uri.getQuery() != null
                || projection != null || selection != null || args != null || order != null) {
            throw new IllegalArgumentException("Only the fixed state query is supported");
        }
        HelperState.probe();
        MatrixCursor cursor = new MatrixCursor(new String[]{"enabled"});
        cursor.addRow(new Object[]{FpsService.current == null || LocalSampler.available(getContext()) ? 0 : 1});
        return cursor;
    }
    public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.app-fps.state"; }
    public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
