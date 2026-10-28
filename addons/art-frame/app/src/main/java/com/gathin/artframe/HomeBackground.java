package com.gathin.artframe;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Home background matches the art": sets the TV's system wallpaper to the default piece (or, when the default is a
 * collection or everything, the last piece shown), with its brightness and contrast. A launcher that shows the
 * system wallpaper (cleanyourtv's launcher with the theme key "system_wallpaper": true) then has the same picture
 * behind Home. Only the wallpaper changes; nothing is written anywhere else.
 */
final class HomeBackground {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private HomeBackground() {}

    /** The piece Home should show: the default piece if there is one, else the given fallback (may be null). */
    static void follow(Context context, Artwork fallback) {
        Context app = context.getApplicationContext();
        Library lib = Library.get(app);
        Artwork a = Library.DEFAULT_PIECE.equals(lib.defaultKind()) ? lib.find(lib.defaultId()) : null;
        if (a == null) a = fallback;
        if (a != null) set(app, a);
    }

    static void set(Context context, Artwork a) {
        Prefs prefs = new Prefs(context);
        if (!prefs.homeBackground()) return;
        String key = a.id + "/" + a.brightness + "/" + a.contrast;
        if (key.equals(prefs.homeBackgroundKey())) return;
        Library lib = Library.get(context);
        WORKER.execute(() -> {
            try {
                Bitmap src = Images.decodeFit(lib.picture(a), 2560, 1440);
                Bitmap out = src;
                android.graphics.ColorMatrixColorFilter f = Images.filter(a.brightness, a.contrast, 1f);
                if (f != null) {
                    out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
                    Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
                    p.setColorFilter(f);
                    new Canvas(out).drawBitmap(src, 0, 0, p);
                    src.recycle();
                }
                WallpaperManager.getInstance(context).setBitmap(out, null, true, WallpaperManager.FLAG_SYSTEM);
                out.recycle();
                prefs.edit().putString("home_bg_key", key).apply();
                Log.i(Library.TAG, "Home background set to " + a.title);
            } catch (Exception | OutOfMemoryError e) {
                Log.w(Library.TAG, "Home background not set: " + e);
            }
        });
    }
}
