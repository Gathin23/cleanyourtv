package com.gathin.artframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The matte around a fitted picture: its colour, its width and its surface. The textures are drawn by the app (a
 * repeating 256 px tile of fine light and dark specks, threads or mottling over the colour), so no picture files
 * are needed and they stay sharp at 4K.
 */
final class Matte {
    static final String[] COLOURS = {"white", "cream", "light-grey", "warm-grey", "charcoal", "black", "navy", "forest",
            "burgundy"};
    static final String[] SIZES = {"hairline", "extra-thin", "thin", "medium", "wide"};
    static final String[] TEXTURES = {"smooth", "paper", "linen", "suede"};
    /** How far the picture looks sunk behind the matte. */
    static final String[] DEPTHS = {"none", "subtle", "deep"};

    private static final Map<String, Bitmap> TILES = new HashMap<>();

    private Matte() {}

    static int color(String name) {
        switch (name) {
            case "cream": return 0xFFEFE8D8;
            case "light-grey": return 0xFFDAD9D5;
            case "warm-grey": return 0xFFB8B0A3;
            case "charcoal": return 0xFF2A2B2E;
            case "black": return 0xFF0B0B0C;
            case "navy": return 0xFF1F2A44;
            case "forest": return 0xFF2E3B2F;
            case "burgundy": return 0xFF4A1F24;
            case "white":
            default: return 0xFFF4F3EF;
        }
    }

    /** The matte's width on each side, as a share of the screen's height (wide is the original size). */
    static float margin(String size) {
        switch (size) {
            case "hairline": return 0.01f;
            case "extra-thin": return 0.02f;
            case "thin": return 0.035f;
            case "wide": return 0.11f;
            case "medium":
            default: return 0.065f;
        }
    }

    static String label(Context c, String v) {
        switch (v) {
            case "none": return c.getString(R.string.matte_none);
            case "cream": return c.getString(R.string.matte_cream);
            case "light-grey": return c.getString(R.string.matte_light_grey);
            case "warm-grey": return c.getString(R.string.matte_warm_grey);
            case "charcoal": return c.getString(R.string.matte_charcoal);
            case "black": return c.getString(R.string.matte_black);
            case "navy": return c.getString(R.string.matte_navy);
            case "forest": return c.getString(R.string.matte_forest);
            case "burgundy": return c.getString(R.string.matte_burgundy);
            case "hairline": return c.getString(R.string.size_hairline);
            case "extra-thin": return c.getString(R.string.size_extra_thin);
            case "thin": return c.getString(R.string.size_thin);
            case "medium": return c.getString(R.string.size_medium);
            case "wide": return c.getString(R.string.size_wide);
            case "subtle": return c.getString(R.string.depth_subtle);
            case "deep": return c.getString(R.string.depth_deep);
            case "smooth": return c.getString(R.string.texture_smooth);
            case "paper": return c.getString(R.string.texture_paper);
            case "linen": return c.getString(R.string.texture_linen);
            case "suede": return c.getString(R.string.texture_suede);
            case "white":
            default: return c.getString(R.string.matte_white);
        }
    }

    /** Lays the texture over a matte already filled with its colour. */
    static void drawTexture(Canvas c, String texture, int w, int h) {
        if ("smooth".equals(texture)) return;
        Bitmap tile = tile(texture);
        if (tile == null) return;
        BitmapShader shader = new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        // The same physical size on a 1080p or a 4K canvas.
        float k = Math.max(1f, h / 1440f);
        Matrix m = new Matrix();
        m.setScale(k, k);
        shader.setLocalMatrix(m);
        Paint p = new Paint();
        p.setShader(shader);
        c.drawRect(0, 0, w, h, p);
    }

    static synchronized Bitmap tile(String texture) {
        Bitmap t = TILES.get(texture);
        if (t != null) return t;
        int n = 256;
        int[] px = new int[n * n];
        Random r = new Random(texture.hashCode());
        switch (texture) {
            case "paper":
                // Fine grain: scattered light and dark specks, a little denser in small clumps.
                for (int i = 0; i < px.length; i++) {
                    int a = r.nextInt(18);
                    px[i] = r.nextBoolean() ? Color.argb(a, 255, 255, 255) : Color.argb(a, 0, 0, 0);
                }
                break;
            case "linen": {
                // Woven threads: each row and column a slightly different shade, crossing, with a touch of grain.
                float[] row = new float[n];
                float[] col = new float[n];
                for (int i = 0; i < n; i++) {
                    row[i] = (r.nextFloat() - 0.5f) * ((i % 3 == 0) ? 1.6f : 1f);
                    col[i] = (r.nextFloat() - 0.5f) * ((i % 3 == 0) ? 1.6f : 1f);
                }
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        // Over and under: rows show on one diagonal, columns on the other.
                        float v = ((x + y) & 1) == 0 ? row[y] : col[x];
                        v += (r.nextFloat() - 0.5f) * 0.3f;
                        int a = Math.min(40, Math.round(Math.abs(v) * 44));
                        px[y * n + x] = v > 0 ? Color.argb(a, 255, 255, 255) : Color.argb(a, 0, 0, 0);
                    }
                }
                break;
            }
            case "suede": {
                // Soft mottling: smooth low-frequency patches, then a fine nap on top.
                int g = 16;
                float[] grid = new float[g * g];
                for (int i = 0; i < grid.length; i++) grid[i] = r.nextFloat() - 0.5f;
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        float gx = x * g / (float) n;
                        float gy = y * g / (float) n;
                        int x0 = (int) gx;
                        int y0 = (int) gy;
                        float fx = gx - x0;
                        float fy = gy - y0;
                        float a00 = grid[(y0 % g) * g + (x0 % g)];
                        float a10 = grid[(y0 % g) * g + ((x0 + 1) % g)];
                        float a01 = grid[((y0 + 1) % g) * g + (x0 % g)];
                        float a11 = grid[((y0 + 1) % g) * g + ((x0 + 1) % g)];
                        float v = (a00 * (1 - fx) + a10 * fx) * (1 - fy) + (a01 * (1 - fx) + a11 * fx) * fy;
                        v += (r.nextFloat() - 0.5f) * 0.25f;
                        int a = Math.min(34, Math.round(Math.abs(v) * 52));
                        px[y * n + x] = v > 0 ? Color.argb(a, 255, 255, 255) : Color.argb(a, 0, 0, 0);
                    }
                }
                break;
            }
            default:
                return null;
        }
        t = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        TILES.put(texture, t);
        return t;
    }

    /**
     * Whether a picture is close enough to the matte opening's shape to fill it (cropping at most about a seventh),
     * which keeps the matte equally thick all round. Pictures of other shapes are shown whole, centred.
     */
    static boolean coversOpening(float pw, float ph, float boxW, float boxH) {
        if (pw <= 0 || ph <= 0 || boxW <= 0 || boxH <= 0) return false;
        float r = (pw / ph) / (boxW / boxH);
        return r >= 0.85f && r <= 1.17f;
    }

    static String next(String[] values, String current, int step) {
        int i = 0;
        for (int k = 0; k < values.length; k++) if (values[k].equals(current)) i = k;
        return values[Math.floorMod(i + step, values.length)];
    }

    static boolean known(String[] values, String v) {
        for (String x : values) if (x.equals(v)) return true;
        return false;
    }

    /** values with "auto" (follow Settings) in front, for a piece's own choice. */
    static String[] withAuto(String[] values) {
        String[] out = new String[values.length + 1];
        out[0] = "auto";
        System.arraycopy(values, 0, out, 1, values.length);
        return out;
    }
}
