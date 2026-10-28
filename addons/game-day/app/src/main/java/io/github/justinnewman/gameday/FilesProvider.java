package io.github.justinnewman.gameday;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * The setup script's way in (setup.sh / setup.ps1, over ADB). It requires WRITE_SECURE_SETTINGS, which only the
 * ADB shell holds, so no app on the TV can read or change the calendar links through it.
 * <ul>
 * <li>write {@code calendars.txt}: the new list ({@code sport | name | link}); applied when the file is closed.</li>
 * <li>read {@code result.txt}: the report, one line per calendar, ending with {@code done}. Never a link.</li>
 * </ul>
 */
public final class FilesProvider extends ContentProvider {
    static final String CALENDARS = "calendars.txt";
    static final String RESULT = "result.txt";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context ctx = getContext();
        String name = uri.getLastPathSegment();
        if (ctx == null || name == null) throw new FileNotFoundException();
        File result = new File(ctx.getFilesDir(), RESULT);
        if (name.equals(CALENDARS) && mode.contains("w")) {
            // The script waits for "done": a report from an earlier run must not answer for this one.
            Repository.write(result, "working\n");
            File received = new File(ctx.getCacheDir(), "setup.txt");
            try {
                return ParcelFileDescriptor.open(received, ParcelFileDescriptor.MODE_WRITE_ONLY
                                | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE,
                        new Handler(Looper.getMainLooper()), e -> Background.applySetup(ctx, received));
            } catch (IOException e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
        if (name.equals(RESULT) && !mode.contains("w")) {
            return ParcelFileDescriptor.open(result, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        throw new FileNotFoundException(name);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
