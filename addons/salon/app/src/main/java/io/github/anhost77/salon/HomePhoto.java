package io.github.anhost77.salon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * The home photo: a picture sent from the phone becomes the launcher's 1920 × 1080 wallpaper, the same picture
 * blurred behind and sharp in the window between the TV card and the weather (where the launcher leaves a gap).
 * The launcher reads it through content://&lt;pkg&gt;.now/photo; it never leaves the TV.
 */
final class HomePhoto {
    static final int W = 1920, H = 1080;
    /** The gap between the home cards at 1080p, measured on the TV (TV card 29 % wide, 114 px margins). */
    private static final RectF WINDOW = new RectF(681, 250, 1240, 812);

    private HomePhoto() { }

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "home-photo.jpg");
    }

    /** Builds the wallpaper from the sent bytes; false if they are not a picture Android can read. */
    static boolean save(Context ctx, byte[] jpeg) {
        Bitmap src = decode(jpeg, 2400);
        if (src == null) return false;
        src = rotate(src, jpeg);
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

        // Behind: the picture over the whole screen, blurred by shrinking it hard and growing it back by doubling
        // steps (one big jump would leave squares).
        Bitmap soft = cover(src, W, H, null);
        for (int w = W / 2; w >= W / 32; w /= 2) soft = scaled(soft, w, w * H / W);
        for (int w = soft.getWidth() * 2; w < W; w *= 2) soft = scaled(soft, w, w * H / W);
        c.drawBitmap(soft, null, new Rect(0, 0, W, H), smooth);
        c.drawColor(0x40000000);

        // The window: the same picture sharp, cropped to it, with the cards' rounded corners and thin bright edge.
        Path round = new Path();
        round.addRoundRect(WINDOW, 40, 40, Path.Direction.CW);
        c.save();
        c.clipPath(round);
        // Framed on the faces when Android finds some (offline, android.media.FaceDetector), else on the middle.
        Bitmap sharp = cover(src, Math.round(WINDOW.width()), Math.round(WINDOW.height()), faces(src));
        c.drawBitmap(sharp, null, WINDOW, smooth);
        c.restore();
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(2);
        edge.setColor(0x66FFFFFF);
        c.drawPath(round, edge);

        File f = file(ctx), tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            out.compress(Bitmap.CompressFormat.JPEG, 90, o);
        } catch (Exception e) {
            return false;
        }
        if (!tmp.renameTo(f)) return false;
        Settings.setPhotoVersion(System.currentTimeMillis());
        ctx.getContentResolver().notifyChange(NowProvider.PHOTO, null);
        return true;
    }

    /** {@code b} drawn at w × h with bilinear filtering (createScaledBitmap may skip it on some TVs). */
    private static Bitmap scaled(Bitmap b, int w, int h) {
        Bitmap out = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(b, null, new Rect(0, 0, out.getWidth(), out.getHeight()),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    /** The box around the faces Android finds in {@code b} (in b's pixels), or null. */
    private static RectF faces(Bitmap b) {
        try {
            int w = 640, h = Math.round(640f * b.getHeight() / b.getWidth()) & ~1;
            Bitmap small = Bitmap.createScaledBitmap(b, w, h, true).copy(Bitmap.Config.RGB_565, false);
            android.media.FaceDetector.Face[] found = new android.media.FaceDetector.Face[8];
            int n = new android.media.FaceDetector(w, h, found.length).findFaces(small, found);
            if (n == 0) return null;
            RectF box = null;
            android.graphics.PointF mid = new android.graphics.PointF();
            for (int i = 0; i < n; i++) {
                found[i].getMidPoint(mid);
                float e = found[i].eyesDistance() * 1.6f;
                RectF f = new RectF(mid.x - e, mid.y - e, mid.x + e, mid.y + e * 1.3f);
                if (box == null) box = f;
                else box.union(f);
            }
            float k = b.getWidth() / (float) w;
            return new RectF(box.left * k, box.top * k, box.right * k, box.bottom * k);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void clear(Context ctx) {
        //noinspection ResultOfMethodCallIgnored
        file(ctx).delete();
        Settings.setPhotoVersion(System.currentTimeMillis());
        ctx.getContentResolver().notifyChange(NowProvider.PHOTO, null);
    }

    private static Bitmap decode(byte[] b, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(b, 0, b.length, o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= max) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(b, 0, b.length, o);
    }

    /** Phone photos are often stored sideways with an EXIF note: turn them upright. */
    private static Bitmap rotate(Bitmap b, byte[] jpeg) {
        int deg;
        try {
            int o = new ExifInterface(new ByteArrayInputStream(jpeg))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            deg = o == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : o == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : o == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (Exception e) {
            deg = 0;
        }
        if (deg == 0) return b;
        Matrix m = new Matrix();
        m.postRotate(deg);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    /**
     * {@code b} filling w × h like CENTER_CROP, around {@code focus} (the faces' box, in b's pixels) when there is
     * one: the crop is as tight as the faces allow, the faces a little above the middle, as in a portrait.
     */
    private static Bitmap cover(Bitmap b, int w, int h, RectF focus) {
        float scale = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight());
        int cw = Math.round(w / scale), ch = Math.round(h / scale);
        int x = (b.getWidth() - cw) / 2, y = (b.getHeight() - ch) / 2;
        if (focus != null) {
            // A light, fixed zoom (the detector often misses a face, so it never decides how tight to crop): the
            // faces sit a third of the way down. Sideways: on the faces when it found a group, half-way between the
            // face and the middle when it found one (the others are usually next to it).
            cw = Math.round(cw * 0.85f);
            ch = Math.round(ch * 0.85f);
            float cx = focus.width() > b.getWidth() * 0.25f ? focus.centerX() : (focus.centerX() + b.getWidth() / 2f) / 2f;
            x = Math.round(cx - cw / 2f);
            y = Math.round(focus.top - ch * 0.18f);
            x = Math.max(0, Math.min(b.getWidth() - cw, x));
            y = Math.max(0, Math.min(b.getHeight() - ch, y));
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(b, new Rect(x, y, x + cw, y + ch), new Rect(0, 0, w, h),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }
}
