package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

/**
 * wallpaper_scrim: a dark gradient painted into a wallpaper picture once, where Home's text sits (the top band with the
 * greeting, clock, buttons and caption; the lower part with the app name and the dock), as strong as that picture
 * needs. A band's need is the worse of the whole band and the exact spot its text covers, so a mostly dark photo
 * with bright lights right behind one word is still darkened. The arithmetic has no Android types and is tested on
 * the JVM (WallpaperScrimTest).
 */
final class WallpaperScrim {
    private WallpaperScrim() { }

    /** How hard white text is to read over these luminances (0..1): mean brightness plus 1.3x their spread. */
    static float busyness(double sum, double sumSquares, int n) {
        if (n <= 0) return 0f;
        double mean = sum / n, sd = Math.sqrt(Math.max(0, sumSquares / n - mean * mean));
        return (float) (mean + 1.3 * sd);
    }

    /** Scrim alpha 0..255 for a busyness: none for a dark, calm band, up to 72% black for a bright or busy one. */
    static int alpha(float busy) {
        return Math.round(Math.max(0f, Math.min(0.72f, (busy - 0.18f) * 2f)) * 255);
    }

    /** Busyness of a region (fractions of the picture), from a 48 x 16 grid of samples. */
    private static float region(Bitmap b, float x0f, float x1f, float y0f, float y1f) {
        int w = b.getWidth(), h = b.getHeight();
        int x0 = (int) (w * x0f), x1 = Math.min(w - 1, (int) (w * x1f));
        int y0 = (int) (h * y0f), y1 = Math.min(h - 1, (int) (h * y1f));
        double sum = 0, sq = 0;
        for (int gy = 0; gy < 16; gy++) {
            for (int gx = 0; gx < 48; gx++) {
                int c = b.getPixel(x0 + (x1 - x0) * gx / 47, y0 + (y1 - y0) * gy / 15);
                double l = (0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF)) / 255.0;
                sum += l;
                sq += l * l;
            }
        }
        return busyness(sum, sq, 48 * 16);
    }

    /** Paints the scrim into a mutable picture. */
    static void paint(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int top = alpha(Math.max(region(b, 0f, 1f, 0f, 0.28f),
                Math.max(region(b, 0.04f, 0.46f, 0.04f, 0.20f), region(b, 0.55f, 0.96f, 0.18f, 0.26f))));
        int low = alpha(Math.max(region(b, 0f, 1f, 0.45f, 1f), region(b, 0.05f, 0.42f, 0.48f, 0.60f)));
        Canvas canvas = new Canvas(b);
        Paint paint = new Paint();
        if (top > 0) {
            paint.setShader(new LinearGradient(0, 0, 0, h * 0.40f, new int[] {top << 24, (top * 2 / 3) << 24, 0},
                    new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h * 0.40f, paint);
        }
        if (low > 0) {
            paint.setShader(new LinearGradient(0, h * 0.36f, 0, h, new int[] {0, low << 24, low << 24},
                    new float[] {0f, 0.32f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, h * 0.36f, w, h, paint);
        }
    }
}
