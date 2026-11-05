package io.github.shivamsingh262.photoprints;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.File;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The owner's photos: the files in this app's own folder on the TV, where photos.sh (or adb push) puts them,
 * /sdcard/Android/data/io.github.shivamsingh262.photoprints/files/photos. Writing there needs no permission, and
 * the app reads its own folder without one.
 */
final class Photos {

    static final String DIR = "photos";

    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_. -]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);
    /**
     * The date photos.sh writes into the name: photo-0007-20231202.jpg, or only the month, photo-0007-202312.jpg.
     */
    private static final Pattern NAME_DATE = Pattern.compile("-(\\d{4})(\\d{2})(\\d{2})?\\.[A-Za-z]+$");

    private Photos() {
    }

    static File folder(Context c) {
        return new File(c.getExternalFilesDir(null), DIR);
    }

    /** The photo files, read again on every call so photos pushed while the TV is on show up. */
    static List<File> list(Context c) {
        List<File> out = new ArrayList<>();
        File[] files = folder(c).listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (f.isFile() && isImage(f.getName())) out.add(f);
        }
        return out;
    }

    static boolean isImage(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }

    /** {year, month} from a name photos.sh gave, or null. */
    static int[] dateFromName(String name) {
        Matcher m = NAME_DATE.matcher(name);
        if (!m.find()) return null;
        int year = Integer.parseInt(m.group(1)), month = Integer.parseInt(m.group(2));
        return year >= 1900 && month >= 1 && month <= 12 ? new int[]{year, month} : null;
    }

    /** "Dec 2023": from the file name, else from when the camera says it was taken; else "". */
    static String dateOf(File file) {
        int[] ym = dateFromName(file.getName());
        if (ym == null) {
            try {
                String taken = new ExifInterface(file.getPath()).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
                if (taken != null && taken.length() >= 7) {   // "2023:12:02 09:22:30"
                    int year = Integer.parseInt(taken.substring(0, 4)), month = Integer.parseInt(taken.substring(5, 7));
                    if (year >= 1900 && month >= 1 && month <= 12) ym = new int[]{year, month};
                }
            } catch (Exception ignored) {
                // No date: the print shows none.
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

    /** The photo's {width, height} once upright, read from its header only; null if it is not an image. */
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
            int sample = 1;
            while (size[0] / (sample * 2) >= w && size[1] / (sample * 2) >= h) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;   // half the memory of ARGB_8888; photos have no alpha
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
