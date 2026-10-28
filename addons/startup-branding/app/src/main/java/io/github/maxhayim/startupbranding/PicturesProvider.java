package io.github.maxhayim.startupbranding;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Lets push-image.sh write pictures into the app's own folder, so the app owns them (a file the shell copies into
 * Android/data can belong to the shell, and the app then can't read it). Write and delete only, picture names only,
 * and only for callers holding WRITE_SECURE_SETTINGS: the adb shell has it, ordinary apps don't.
 *
 *   content write  --uri content://io.github.maxhayim.startupbranding.pictures/logo.png < /data/local/tmp/logo.png
 *   content delete --uri content://io.github.maxhayim.startupbranding.pictures/logo.png
 */
public class PicturesProvider extends ContentProvider {
    /** A picture's file name: a bare name with a picture extension. */
    static boolean pictureName(String name) {
        if (Look.fileName(name).isEmpty() || !name.matches("[A-Za-z0-9._-]+")) return false;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
    }

    private File file(Uri uri) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (getContext() == null || seg.size() != 1 || !pictureName(seg.get(0))) throw new FileNotFoundException(String.valueOf(uri));
        return new File(Images.dir(getContext()), seg.get(0));
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!mode.contains("w")) throw new FileNotFoundException("write only");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_WRITE_ONLY
                | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            return file(uri).delete() ? 1 : 0;
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
