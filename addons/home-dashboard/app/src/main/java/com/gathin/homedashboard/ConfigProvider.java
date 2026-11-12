package com.gathin.homedashboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;

/** Writes directly to app-private storage; caller must hold WRITE_SECURE_SETTINGS. */
public class ConfigProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"config.json".equals(uri.getLastPathSegment()) || !"w".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(Config.file(getContext()), ParcelFileDescriptor.MODE_CREATE |
            ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY);
    }
    @Override public String getType(Uri u) { return null; }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o) { return null; }
    @Override public Uri insert(Uri u,ContentValues v) { return null; }
    @Override public int update(Uri u,ContentValues v,String s,String[] a) { return 0; }
    @Override public int delete(Uri u,String s,String[] a) { return 0; }
}
