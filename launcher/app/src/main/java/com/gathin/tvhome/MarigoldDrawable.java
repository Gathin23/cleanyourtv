package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * A marigold (genda phool) seen from the front: four rings of ruffled petals, deep saffron outside to golden
 * inside, around a dark centre. Petal shapes and colours come from a fixed seed and are built once. Light comes
 * from the top left and stays there while the flower turns, and each ring shades the base of the one behind it.
 * {@link #turn()} rotates the flower a further 60 degrees; the inner rings turn slightly more than the outer
 * ones. Drawn in a 24 x 24 unit box scaled into the bounds.
 */
public final class MarigoldDrawable extends Drawable {

    private static final float BOX = 24f, C = 12f;
    private static final int[] COUNT = {13, 11, 9, 7};
    private static final float[] RADIUS = {11.6f, 9.4f, 7.2f, 5.0f};
    /** How much faster each ring turns than the outer one, so a turn ruffles the flower a little. */
    private static final float[] SPIN = {1f, 1.04f, 1.09f, 1.15f};
    private static final float CORE_SPIN = 1.2f;
    private static final int[][] SHADES = {
            {0xFFE5600A, 0xFFF0800E}, {0xFFF0800E, 0xFFF79A16}, {0xFFF9A31B, 0xFFFFB524}, {0xFFFFB524, 0xFFFFC63E}};
    private static final float CORE_R = 2.9f;
    /** Direction the light comes from (top left), in degrees with y pointing down. */
    private static final float LIGHT_DEG = 225f;
    private static final int LIT = 0xFFFFE9A6, SHADOWED = 0xFFA43E00, EDGE = 0xFF6E2400;
    /** Shadows fall a little down and right of each ring, away from the light. */
    private static final float SHADOW_DX = 0.35f, SHADOW_DY = 0.45f;
    private static final long TURN_MS = 900;

    private final float density;
    private final Path[][] petals = new Path[COUNT.length][];
    private final float[][] angles = new float[COUNT.length][];
    private final int[][] colors = new int[COUNT.length][];
    private final int[][] edges = new int[COUNT.length][];
    private final float[] shadowR = new float[COUNT.length];
    private final Paint[] shadowPaints = new Paint[COUNT.length];
    private final Path core = new Path();
    private final float[] seeds = new float[18];

    private final Paint fill, edge, coreFill, coreEdge, seedPaint;
    private final Paint[] paints;
    private final ValueAnimator animator;

    private double rotation, from, target;
    private boolean reduced;
    private int alpha = 255;

    public MarigoldDrawable(float density) {
        this.density = density;
        Random rnd = new Random(0x6E7DAL);
        for (int i = 0; i < COUNT.length; i++) buildRing(i, rnd);
        buildCore(rnd);

        fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(0.3f);
        edge.setStrokeJoin(Paint.Join.ROUND);
        for (int i = 0; i < COUNT.length; i++) {
            float inner = i + 1 < COUNT.length ? RADIUS[i + 1] : CORE_R;
            float sr = inner + 0.6f * (RADIUS[i] - inner);
            float q = inner / sr;
            shadowR[i] = sr;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(C + SHADOW_DX, C + SHADOW_DY, sr,
                    new int[]{0x805A1E00, 0x805A1E00, 0x665A1E00, 0x005A1E00},
                    new float[]{0f, q * 0.9f, q, 1f}, Shader.TileMode.CLAMP));
            shadowPaints[i] = p;
        }
        coreFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        coreFill.setShader(new RadialGradient(C - 0.7f, C - 0.7f, 3.6f,
                new int[]{0xFFE0821A, 0xFFB04E08, 0xFF7A3004}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        coreEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        coreEdge.setStyle(Paint.Style.STROKE);
        coreEdge.setStrokeWidth(0.3f);
        coreEdge.setStrokeJoin(Paint.Join.ROUND);
        coreEdge.setColor(0xFF5C2403);
        seedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        seedPaint.setColor(0xFFFFB84A);

        paints = new Paint[COUNT.length + 5];
        System.arraycopy(shadowPaints, 0, paints, 0, COUNT.length);
        paints[COUNT.length] = fill;
        paints[COUNT.length + 1] = edge;
        paints[COUNT.length + 2] = coreFill;
        paints[COUNT.length + 3] = coreEdge;
        paints[COUNT.length + 4] = seedPaint;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(TURN_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> {
            rotation = from + (target - from) * (float) a.getAnimatedValue();
            invalidateSelf();
        });
    }

    /** Turns the flower a further 60 degrees, easing to a stop. Repeated calls add up smoothly. */
    public void turn() {
        if (reduced) return;
        from = rotation;
        target += 60.0;
        animator.cancel();
        animator.start();
    }

    /** With reduced motion {@link #turn()} does nothing; a turn in progress jumps to its end. */
    public void setReducedMotion(boolean reducedMotion) {
        reduced = reducedMotion;
        if (reduced && animator.isRunning()) animator.end();
    }

    // ---- geometry, built once ----

    private void buildRing(int ring, Random rnd) {
        int n = COUNT[ring];
        petals[ring] = new Path[n];
        angles[ring] = new float[n];
        colors[ring] = new int[n];
        edges[ring] = new int[n];
        float offset = ring * (180f / n) + (rnd.nextFloat() - 0.5f) * 8f;
        float edgeDepth = ring == 0 ? 0.55f : 0.45f;
        for (int j = 0; j < n; j++) {
            float deg = offset + j * 360f / n + (rnd.nextFloat() - 0.5f) * 10f;
            float r = RADIUS[ring] * (0.96f + 0.04f * rnd.nextFloat());
            float hw = (float) (Math.PI / n) * (1.18f + 0.15f * rnd.nextFloat());
            int scallops = 3 + rnd.nextInt(2);
            petals[ring][j] = petal((float) Math.toRadians(deg), hw, r * 0.25f, r, scallops, rnd);
            angles[ring][j] = deg;
            int base = lerp(SHADES[ring][0], SHADES[ring][1], rnd.nextFloat());
            colors[ring][j] = base;
            edges[ring][j] = lerp(base, EDGE, edgeDepth);
        }
    }

    /** One petal: narrow at the base, fanning out to a frilled, notched edge. */
    private static Path petal(float a, float hw, float r0, float r, int scallops, Random rnd) {
        Path p = new Path();
        float a0 = a - hw, a1 = a + hw;
        p.moveTo(px(r0, a - hw * 0.35f), py(r0, a - hw * 0.35f));
        float sideA = a0 - hw * 0.08f;
        p.quadTo(px(r * 0.62f, sideA), py(r * 0.62f, sideA), px(r * 0.86f, a0), py(r * 0.86f, a0));
        int steps = scallops * 2;
        for (int k = 1; k < steps; k += 2) {
            float ta = a0 + (a1 - a0) * k / steps + (rnd.nextFloat() - 0.5f) * hw * 0.12f;
            float na = a0 + (a1 - a0) * (k + 1) / steps;
            float tr = r * (1.0f + 0.06f * rnd.nextFloat());
            float nr = k + 1 >= steps ? r * 0.86f : r * (0.83f + 0.06f * rnd.nextFloat());
            p.quadTo(px(tr, ta), py(tr, ta), px(nr, na), py(nr, na));
        }
        float sideB = a1 + hw * 0.08f;
        float endA = a + hw * 0.35f;
        p.quadTo(px(r * 0.62f, sideB), py(r * 0.62f, sideB), px(r0, endA), py(r0, endA));
        p.close();
        return p;
    }

    private void buildCore(Random rnd) {
        int bumps = 18;
        for (int k = 0; k <= bumps * 2; k++) {
            float a = (float) (Math.PI * k / bumps);
            float r = (k % 2 == 1) ? CORE_R * (1.0f + 0.06f * rnd.nextFloat()) : CORE_R * 0.84f;
            if (k == 0) core.moveTo(px(r, a), py(r, a));
            else if (k % 2 == 1) {
                float na = (float) (Math.PI * (k + 1) / bumps);
                float nr = CORE_R * 0.84f;
                core.quadTo(px(r, a), py(r, a), px(nr, na), py(nr, na));
            }
        }
        core.close();
        // tiny florets in a sunflower spiral
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int k = 0; k < seeds.length / 2; k++) {
            float r = 0.62f * (float) Math.sqrt(k + 0.5);
            float a = (float) (k * golden);
            seeds[k * 2] = px(r, a);
            seeds[k * 2 + 1] = py(r, a);
        }
    }

    // ---- drawing ----

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float s = Math.min(b.width(), b.height()) / BOX;
        int save = alpha < 255 ? c.saveLayerAlpha(b.left, b.top, b.right, b.bottom, alpha) : c.save();
        c.clipRect(b);
        c.translate(b.exactCenterX() - C * s, b.exactCenterY() - C * s);
        c.scale(s, s);

        for (int i = 0; i < COUNT.length; i++) {
            float rot = (float) ((rotation * SPIN[i]) % 360.0);
            c.save();
            c.rotate(rot, C, C);
            Path[] ring = petals[i];
            for (int j = 0; j < ring.length; j++) {
                float l = (float) Math.cos(Math.toRadians(angles[i][j] + rot - LIGHT_DEG));
                int base = colors[i][j];
                fill.setColor(l >= 0f ? lerp(base, LIT, 0.3f * l) : lerp(base, SHADOWED, -0.24f * l));
                c.drawPath(ring[j], fill);
                edge.setColor(edges[i][j]);
                c.drawPath(ring[j], edge);
            }
            c.restore();
            c.drawCircle(C + SHADOW_DX, C + SHADOW_DY, shadowR[i], shadowPaints[i]);
        }

        c.save();
        c.rotate((float) ((rotation * CORE_SPIN) % 360.0), C, C);
        c.drawPath(core, coreFill);
        c.drawPath(core, coreEdge);
        for (int k = 0; k < seeds.length; k += 2) c.drawCircle(seeds[k], seeds[k + 1], 0.26f, seedPaint);
        c.restore();

        c.restoreToCount(save);
    }

    // ---- helpers ----

    private static float px(float r, float a) {
        return C + r * (float) Math.cos(a);
    }

    private static float py(float r, float a) {
        return C + r * (float) Math.sin(a);
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(BOX * density);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(BOX * density);
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) return;
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {
        for (Paint p : paints) p.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
