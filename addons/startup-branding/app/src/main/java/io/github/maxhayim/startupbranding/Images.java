package io.github.maxhayim.startupbranding;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * The pictures: PNG, JPEG or WebP files in the app's own folder on the TV, put there from a computer
 * ({@code push-image.sh}) or copied in from the TV's file picker where it has a real one. No storage permission:
 * the folder is the app's own.
 */
final class Images {
    static final long MAX_BYTES = 15L * 1024 * 1024;
    /** Bigger pictures are refused rather than decoded: 8192 px a side is beyond any TV. */
    static final int MAX_SIDE = 8192;

    private Images() {
    }

    /** /sdcard/Android/data/io.github.maxhayim.startupbranding/files/pictures, which ADB can write to. */
    static File dir(Context c) {
        File d = new File(c.getExternalFilesDir(null), "pictures");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** Usable pictures by name. */
    static List<String> list(Context c) {
        List<String> out = new ArrayList<>();
        File[] files = dir(c).listFiles();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : files) if (f.isFile() && !Look.fileName(f.getName()).isEmpty() && usable(f)) out.add(f.getName());
        return out;
    }

    static boolean usable(File f) {
        if (f.length() <= 0 || f.length() > MAX_BYTES) return false;
        byte[] head = new byte[12];
        try (InputStream in = new FileInputStream(f)) {
            int n = in.read(head);
            if (n < 12 || Fit.kind(head) == null) return false;
        } catch (IOException e) {
            return false;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        return o.outWidth > 0 && o.outHeight > 0 && o.outWidth <= MAX_SIDE && o.outHeight <= MAX_SIDE;
    }

    /** The picture decoded no bigger than {@code maxW} by {@code maxH} needs, or null. */
    static Bitmap load(Context c, String name, int maxW, int maxH) {
        if (Look.fileName(name).isEmpty()) return null;
        File f = new File(dir(c), name);
        if (!f.isFile() || !usable(f)) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = Fit.sampleSize(o.outWidth, o.outHeight, Math.max(1, maxW), Math.max(1, maxH));
        try {
            return BitmapFactory.decodeFile(f.getPath(), d);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    static boolean deleteAll(Context c) {
        boolean ok = true;
        File[] files = dir(c).listFiles();
        if (files != null) for (File f : files) if (f.isFile()) ok &= f.delete();
        return ok;
    }

    /** The picker intent, when this TV has a real app for it (Android TV often has only a stub that does nothing). */
    static Intent picker(Context c) {
        for (Intent i : new Intent[]{
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),
                new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")}) {
            List<ResolveInfo> found = c.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo r : found) {
                if (!r.activityInfo.packageName.equals("com.android.tv.frameworkpackagestubs")) return i;
            }
        }
        return null;
    }

    /** Copies a picked picture into the folder; its file name, or null if it isn't a usable picture. */
    static String copyIn(Context c, Uri uri) {
        String name = "picked-" + System.currentTimeMillis();
        try (Cursor q = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (q != null && q.moveToFirst()) {
                String shown = q.getString(0);
                if (shown != null) name = shown.replaceAll("[^A-Za-z0-9._-]", "_");
            }
        } catch (RuntimeException ignored) {
            // A provider without names: the made-up one will do.
        }
        if (Look.fileName(name).isEmpty()) name = "picked-" + System.currentTimeMillis();
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            return in != null && replace(in, dir(c), name, Images::usable) ? name : null;
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    /**
     * Copies into a hidden file next to the picture and renames it over the picture only once it's whole and
     * usable, so a failed import never touches a picture of the same name. The hidden file never shows in
     * {@link #list} ({@link Look#fileName} refuses names starting with a dot).
     */
    static boolean replace(InputStream in, File dir, String name, Predicate<File> usable) throws IOException {
        File part = File.createTempFile(".import-", ".part", dir);
        boolean done = false;
        try {
            long total = 0;
            try (OutputStream o = new FileOutputStream(part)) {
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) {
                    total += n;
                    if (total > MAX_BYTES) return false;
                    o.write(buf, 0, n);
                }
            }
            done = usable.test(part) && part.renameTo(new File(dir, name));
            return done;
        } finally {
            if (!done) part.delete();
        }
    }
}
