package com.gathin.photodream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormatSymbols;

/**
 * Reading the pictures: the month and year a picture was taken, its upright size and a decode near a given size.
 * Adapted from Photo Prints by @shivamsingh262 (#936).
 */
final class Photos {

    private Photos() {}

    /** A small text file (seconds.txt), or null. */
    static String read(File file) {
        try {
            return file != null && file.isFile() && file.length() < 64
                    ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** "Dec 2023": from the file name (photo-20231202.jpg), else from the camera's own date; else "". */
    static String dateOf(File file) {
        int[] ym = Media.dateFromName(file.getName());
        if (ym == null) {
            try {
                String taken = new ExifInterface(file.getPath()).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
                if (taken != null && taken.length() >= 7) {   // "2023:12:02 09:22:30"
                    int year = Integer.parseInt(taken.substring(0, 4));
                    int month = Integer.parseInt(taken.substring(5, 7));
                    if (year >= 1900 && month >= 1 && month <= 12) ym = new int[]{year, month};
                }
            } catch (Exception ignored) {
                // No date: none is shown.
            }
        }
        if (ym == null) return "";
        return new DateFormatSymbols().getShortMonths()[ym[1] - 1] + " " + ym[0];
    }

    /** The EXIF rotation that turns the photo upright: 0, 90, 180 or 270. */
    private static int rotation(File file) {
        try {
            int o = new ExifInterface(file.getPath()).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (o == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (o == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) {
            // PNG and WebP carry no EXIF here; show them as they are.
        }
        return 0;
    }

    /** The photo's {width, height} once upright, read from its header only; null if it is not a usable image. */
    static int[] size(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        return rotation(file) % 180 == 0 ? new int[]{bounds.outWidth, bounds.outHeight}
                : new int[]{bounds.outHeight, bounds.outWidth};
    }

    /** Decodes at the smallest power-of-two size that still covers w x h, upright per EXIF; null on failure. */
    static Bitmap decode(File file, int w, int h) {
        try {
            int rotation = rotation(file);
            int[] size = size(file);
            if (size == null) return null;
            int sample = Media.sampleSize(size[0], size[1], w, h);
            if (!Media.accepted(size[0] / sample, size[1] / sample)) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), opts);
            if (bitmap == null || rotation == 0) return bitmap;
            Matrix m = new Matrix();
            m.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }
}
