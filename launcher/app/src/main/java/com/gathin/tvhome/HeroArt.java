package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * The cinematic shelf's backdrop: an app's artwork reduced to 48×27 pixels, saturated and smoothed,
 * then stretched over the screen by its ImageView. Bilinear filtering of an image that small is a
 * blur that costs one tiny texture; a real blur would mean RenderScript or a shader pass per frame,
 * which a 1 GB TV cannot spare.
 */
final class HeroArt {
    static final int W = 48, H = 27;
    private static final float SATURATION = 1.7f;

    private HeroArt() {
    }

    /** The softened artwork, about 5 KB; null when there is nothing to draw. */
    static Bitmap from(Drawable live) {
        if (live == null) return null;
        Bitmap big = Bitmap.createBitmap(W * 4, H * 4, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(big);
        c.drawColor(Color.BLACK);
        Bitmap src = live instanceof BitmapDrawable ? ((BitmapDrawable) live).getBitmap() : null;
        if (src != null && src.getConfig() == Bitmap.Config.HARDWARE) {
            // A software Canvas cannot draw a HARDWARE bitmap directly; copy it to ARGB_8888 first.
            Bitmap sw = src.copy(Bitmap.Config.ARGB_8888, false);
            if (sw != null) {
                c.drawBitmap(sw, null, new Rect(0, 0, big.getWidth(), big.getHeight()), null);
                sw.recycle();
            }
        } else {
            // Draw a private copy: changing the bounds of the drawable a tile shows would blank the tile.
            Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
            Rect saved = new Rect(d.getBounds());
            d.setBounds(0, 0, big.getWidth(), big.getHeight());
            d.draw(c);
            d.setBounds(saved);
        }
        // Halve twice: each filtered halving averages 2×2 pixels, where one 4× step would skip most of them.
        Bitmap half = Bitmap.createScaledBitmap(big, W * 2, H * 2, true);
        big.recycle();
        Bitmap small = Bitmap.createScaledBitmap(half, W, H, true);
        half.recycle();

        int[] px = new int[W * H];
        small.getPixels(px, 0, W, 0, 0, W, H);
        saturate(px);
        // Three box passes approximate a gaussian, so the stretched image shows no seams between pixels.
        for (int pass = 0; pass < 3; pass++) {
            px = boxBlur(px, W, H, 2, true);
            px = boxBlur(px, W, H, 2, false);
        }
        Bitmap out = small.isMutable() ? small : small.copy(Bitmap.Config.ARGB_8888, true);
        if (out != small) small.recycle();
        out.setPixels(px, 0, W, 0, 0, W, H);
        return out;
    }

    private static void saturate(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = Color.red(p), g = Color.green(p), b = Color.blue(p);
            float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
            px[i] = Color.rgb(clamp(lum + (r - lum) * SATURATION), clamp(lum + (g - lum) * SATURATION),
                    clamp(lum + (b - lum) * SATURATION));
        }
    }

    private static int clamp(float v) {
        return v < 0 ? 0 : v > 255 ? 255 : Math.round(v);
    }

    /** One box blur pass along rows (horizontal) or columns, edges clamped. */
    private static int[] boxBlur(int[] src, int w, int h, int radius, boolean horizontal) {
        int[] dst = new int[src.length];
        int lines = horizontal ? h : w, length = horizontal ? w : h, span = 2 * radius + 1;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < length; i++) {
                int r = 0, g = 0, b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int j = Math.max(0, Math.min(length - 1, i + k));
                    int p = horizontal ? src[line * w + j] : src[j * w + line];
                    r += Color.red(p);
                    g += Color.green(p);
                    b += Color.blue(p);
                }
                dst[horizontal ? line * w + i : i * w + line] = Color.rgb(r / span, g / span, b / span);
            }
        }
        return dst;
    }
}
