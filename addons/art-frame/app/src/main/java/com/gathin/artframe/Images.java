package com.gathin.artframe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageDecoder;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Decoding, fitting and colour adjustment. Stored pictures are at most 3840x2160, so a 4K panel gets every pixel. */
final class Images {
    static final int STORE_W = 3840;
    static final int STORE_H = 2160;
    static final int THUMB_W = 480;
    static final int THUMB_H = 360;

    private Images() {}

    /** The panel's width/height ratio (16:9 = 1.78), from the display's own mode. */
    static double screenRatio(android.content.Context c) {
        try {
            android.view.Display d = c.getSystemService(android.hardware.display.DisplayManager.class)
                    .getDisplay(android.view.Display.DEFAULT_DISPLAY);
            android.view.Display.Mode m = d.getMode();
            int w = Math.max(m.getPhysicalWidth(), m.getPhysicalHeight());
            int h = Math.min(m.getPhysicalWidth(), m.getPhysicalHeight());
            if (w > 0 && h > 0) return w / (double) h;
        } catch (RuntimeException ignored) {
            // Fall back to 16:9.
        }
        return 16 / 9.0;
    }

    /** Scale that fits w x h inside maxW x maxH without enlarging. */
    static float fitScale(int w, int h, int maxW, int maxH) {
        if (w <= 0 || h <= 0) return 1f;
        return Math.min(1f, Math.min(maxW / (float) w, maxH / (float) h));
    }

    /** Largest power-of-two sample size that keeps the decoded picture at least maxW x maxH (or the original). */
    static int sampleSize(int w, int h, int maxW, int maxH) {
        int s = 1;
        while (w / (s * 2) >= maxW && h / (s * 2) >= maxH) s *= 2;
        // A very tall or wide picture can still be huge on its long side; keep the long side under 2x the box.
        while (Math.max(w / s, h / s) > 2 * Math.max(maxW, maxH)) s *= 2;
        return s;
    }

    /** Decoded bounds, or null when the file is not a picture Android can read. */
    static int[] bounds(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth > 0 && o.outHeight > 0) return new int[]{o.outWidth, o.outHeight};
        return null;
    }

    /** The picture scaled to fit maxW x maxH; software config, so it can be re-encoded. */
    static Bitmap decodeFit(File f, int maxW, int maxH) throws IOException {
        Bitmap raw = null;
        int[] b = bounds(f);
        if (b != null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sampleSize(b[0], b[1], maxW, maxH);
            raw = BitmapFactory.decodeFile(f.getPath(), o);
        } else if (Build.VERSION.SDK_INT >= 28) {
            // HEIF/AVIF and other formats BitmapFactory cannot read.
            raw = ImageDecoder.decodeBitmap(ImageDecoder.createSource(f), (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                android.util.Size s = info.getSize();
                float k = fitScale(s.getWidth(), s.getHeight(), maxW, maxH);
                decoder.setTargetSize(Math.max(1, Math.round(s.getWidth() * k)), Math.max(1, Math.round(s.getHeight() * k)));
            });
        }
        if (raw == null) throw new IOException("not a picture");
        float k = fitScale(raw.getWidth(), raw.getHeight(), maxW, maxH);
        if (k >= 1f) return raw;
        Bitmap scaled = Bitmap.createScaledBitmap(raw, Math.max(1, Math.round(raw.getWidth() * k)),
                Math.max(1, Math.round(raw.getHeight() * k)), true);
        if (scaled != raw) raw.recycle();
        return scaled;
    }

    /** For the frame: the picture fitted to the drawing surface, as a GPU bitmap where the TV allows it. */
    static Bitmap decodeForScreen(File f, int maxW, int maxH, boolean hardware) throws IOException {
        int[] b = bounds(f);
        if (b == null) return decodeFit(f, maxW, maxH);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sampleSize(b[0], b[1], maxW, maxH);
        if (hardware) o.inPreferredConfig = Bitmap.Config.HARDWARE;
        Bitmap bm = BitmapFactory.decodeFile(f.getPath(), o);
        if (bm == null) {
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bm = BitmapFactory.decodeFile(f.getPath(), o);
        }
        if (bm == null) throw new IOException("cannot decode " + f.getName());
        return bm;
    }

    /** Writes the picture fitted to 4K as a JPEG, and its thumbnail. Returns {width, height}. */
    static int[] store(File src, File dst, File thumb) throws IOException {
        Bitmap bm = decodeFit(src, STORE_W, STORE_H);
        try {
            writeJpeg(bm, dst, 92);
            float k = fitScale(bm.getWidth(), bm.getHeight(), THUMB_W, THUMB_H);
            Bitmap t = Bitmap.createScaledBitmap(bm, Math.max(1, Math.round(bm.getWidth() * k)),
                    Math.max(1, Math.round(bm.getHeight() * k)), true);
            writeJpeg(t, thumb, 85);
            if (t != bm) t.recycle();
            return new int[]{bm.getWidth(), bm.getHeight()};
        } finally {
            bm.recycle();
        }
    }

    private static void writeJpeg(Bitmap bm, File dst, int quality) throws IOException {
        File tmp = new File(dst.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            if (!bm.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw new IOException("encode failed");
        }
        if (!tmp.renameTo(dst)) throw new IOException("rename failed");
    }

    /**
     * Brightness and contrast, each -50..50, and a dimming factor (1 = none). Contrast scales around mid-grey
     * (0.5x to 1.5x); brightness shifts by up to a quarter of the range. Null when nothing changes, so an untouched
     * piece draws with no filter at all.
     */
    static ColorMatrixColorFilter filter(int brightness, int contrast, float dim) {
        if (brightness == 0 && contrast == 0 && dim >= 1f) return null;
        float c = 1f + contrast / 100f;
        float t = brightness * 1.275f + 128f * (1f - c);
        float d = Math.max(0f, Math.min(1f, dim));
        ColorMatrix m = new ColorMatrix(new float[]{
                c * d, 0, 0, 0, t * d,
                0, c * d, 0, 0, t * d,
                0, 0, c * d, 0, t * d,
                0, 0, 0, 1, 0});
        return new ColorMatrixColorFilter(m);
    }
}
