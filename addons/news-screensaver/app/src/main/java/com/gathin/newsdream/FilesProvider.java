package com.gathin.newsdream;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lets setup.sh write the two settings files into the app's own files dir. Only those two names, and only for
 * callers holding WRITE_SECURE_SETTINGS (the adb shell).
 *
 *   content write  --uri content://com.gathin.newsdream.files/feeds.txt < /data/local/tmp/feeds.txt
 *   content write  --uri content://com.gathin.newsdream.files/seconds.txt < /data/local/tmp/seconds.txt
 *   content read   --uri content://com.gathin.newsdream.files/feeds.txt
 *   content delete --uri content://com.gathin.newsdream.files/feeds.txt        (back to the default feed)
 */
public class FilesProvider extends ContentProvider {

    private File file(Uri uri) throws FileNotFoundException {
        String name = Config.path(uri.getPathSegments());
        if (getContext() == null || name == null) throw new FileNotFoundException(String.valueOf(uri));
        return new File(getContext().getFilesDir(), name);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        if (mode.contains("w")) {
            // New feeds: the old headlines go, so the next start fetches at once.
            if (Config.FEEDS.equals(f.getName())) News.clearCache(f.getParentFile());
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            File f = file(uri);
            if (Config.FEEDS.equals(f.getName())) News.clearCache(f.getParentFile());
            return f.delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
