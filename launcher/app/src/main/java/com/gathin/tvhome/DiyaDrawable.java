package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * A clay diya whose flame shows the network: the flame's height follows the signal level (1 to 4), it goes out
 * with a wisp of smoke when the level drops to 0, and relights with a spark when it comes back. Ethernet gets a
 * calm, steady flame. Drawn in a 24 x 24 unit box scaled into the bounds, with a dark outline so it reads on the
 * frosted and on the white (focused) pill. The flame flickers only while started, at about 15 fps; nothing is
 * allocated per frame. The first {@link #setFlame} call applies its level without an animation.
 */
public final class DiyaDrawable extends Drawable implements FestiveLamp {

    private static final float BOX = 24f;
    private static final long FRAME_MS = 66;
    private static final long LEVEL_MS = 300, OUT_MS = 350, SMOKE_DELAY_MS = 260, SMOKE_MS = 900;
    private static final long OUT_END_MS = Math.max(OUT_MS, SMOKE_DELAY_MS + SMOKE_MS);
    private static final long SPARK_MS = 200, GROW_DELAY_MS = 110, GROW_MS = 500;
    private static final int IDLE = 0, OUT = 1, RELIGHT = 2;
    /** Flame size for each level; index 0 is out. */
    private static final float[] LEVEL_SCALE = {0f, 0.46f, 0.64f, 0.82f, 1f};
    /** Where the flame stands: the wick at the tip of the spout. */
    private static final float WICK_X = 18.5f, WICK_Y = 12.1f;
    /** Full flame height and half width, in box units. */
    private static final float FLAME_H = 10f, FLAME_W = 2f;
    private static final int SMOKE_POINTS = 16;
    /** Painted dots on the bowl, x/y pairs. */
    private static final float[] DOTS = {5.0f, 17.6f, 7.4f, 18.5f, 9.9f, 18.8f, 12.4f, 18.4f, 14.6f, 17.3f};

    private final float density;
    private final float[] noise = new float[256];

    private final Path body = new Path(), opening = new Path(), rimLip = new Path(), sheen = new Path();
    private final Path flame = new Path(), core = new Path(), smoke = new Path();
    private final RectF foot = new RectF(7.2f, 20.0f, 12.4f, 22.4f);
    private final RectF oval = new RectF();
    private final Matrix matrix = new Matrix();

    private final Paint footPaint, bodyPaint, warmPaint, openingPaint, openingEdge, dotPaint, lipPaint, sheenPaint,
            outlinePaint, wickPaint, glowPaint, flamePaint, edgePaint, corePaint, smokePaint, emberPaint, sparkPaint,
            rayPaint;
    private final Paint[] paints;
    private final Shader glowShader, flameShader, coreShader, smokeShader, sparkShader;

    private int level = 4;
    private boolean primed;
    private float scaleFrom = 1f, scaleTo = 1f;
    private long scaleStart;
    private int phase = IDLE;
    private long phaseStart;
    private float outFrom;
    private boolean running, steady, reduced;
    private int alpha = 255;
    private final long epoch = SystemClock.uptimeMillis();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            update(now);
            invalidateSelf();
            if (wantsFrames()) scheduleSelf(this, now + FRAME_MS);
        }
    };

    public DiyaDrawable(float density) {
        this.density = density;
        Random rnd = new Random(0xD1A5L);
        for (int i = 0; i < noise.length; i++) noise[i] = rnd.nextFloat() * 2f - 1f;
        buildLamp();

        footPaint = fill(0xFF8E3E1E);
        bodyPaint = fill(0xFFC4612F);
        bodyPaint.setShader(new RadialGradient(7.5f, 14.5f, 13f,
                new int[]{0xFFE58C57, 0xFFC4612F, 0xFF9A4220, 0xFF6E2C14},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        warmPaint = fill(0xFFFFB347);
        RadialGradient warm = new RadialGradient(0f, 0f, 1f,
                new int[]{0xA6FFB347, 0x40FF9A2A, 0x00FF8A1A}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        matrix.setScale(9f, 9f);
        matrix.postTranslate(WICK_X, WICK_Y);
        warm.setLocalMatrix(matrix);
        warmPaint.setShader(warm);
        openingPaint = fill(0xFF4A1E0E);
        openingPaint.setShader(new LinearGradient(2.6f, 0f, 19f, 0f,
                new int[]{0xFF3B170A, 0xFF5A2A10, 0xFF7A4416}, null, Shader.TileMode.CLAMP));
        openingEdge = stroke(0xFF4A1A0A, 0.3f);
        dotPaint = fill(0xFFFFE0A3);
        lipPaint = stroke(0xFFEE9A68, 0.6f);
        sheenPaint = stroke(0x73FFE8D2, 0.75f);
        outlinePaint = stroke(0xFF3D1408, 0.7f);
        wickPaint = stroke(0xFF2B140A, 0.6f);

        glowPaint = fill(0xFFFFB040);
        glowShader = new RadialGradient(0f, 0f, 1f,
                new int[]{0x99FFC45A, 0x44FF9A2A, 0x00FF8A1A}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        glowPaint.setShader(glowShader);
        flamePaint = fill(0xFFFF9A2A);
        flameShader = new LinearGradient(0f, 0f, 0f, 1f,
                new int[]{0xFFD9430B, 0xFFFF7A14, 0xFFFFA62B, 0xFFFFB83F, 0xFFF08A24},
                new float[]{0f, 0.22f, 0.55f, 0.85f, 1f}, Shader.TileMode.CLAMP);
        flamePaint.setShader(flameShader);
        edgePaint = stroke(0xFFB8400A, 0.28f);
        corePaint = fill(0xFFFFF0B8);
        coreShader = new LinearGradient(0f, 0f, 0f, 1f,
                new int[]{0xFFFFD25A, 0xFFFFF0B8, 0xFFFFF8E1, 0xFFFFE9A6},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        corePaint.setShader(coreShader);

        smokePaint = stroke(0xFFA0A0A0, 0.6f);
        smokeShader = new LinearGradient(0f, 0f, 0f, 1f,
                new int[]{0x00A0A0A0, 0xB09A9A9A, 0xFF8E8E8E}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP);
        smokePaint.setShader(smokeShader);
        emberPaint = fill(0xFFFF5A1A);
        sparkPaint = fill(0xFFFFF4C8);
        sparkShader = new RadialGradient(0f, 0f, 1f,
                new int[]{0xFFFFF4C8, 0xCCFFC24A, 0x00FF9A20}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        sparkPaint.setShader(sparkShader);
        rayPaint = stroke(0xFFF2A12E, 0.35f);

        paints = new Paint[]{footPaint, bodyPaint, warmPaint, openingPaint, openingEdge, dotPaint, lipPaint,
                sheenPaint, outlinePaint, wickPaint, glowPaint, flamePaint, edgePaint, corePaint, smokePaint,
                emberPaint, sparkPaint, rayPaint};
    }

    // ---- public API ----

    /** 0 puts the flame out; 1 to 4 is the signal strength, which sets the flame's height. */
    @Override
    public void setFlame(int newLevel) {
        int lvl = Math.max(0, Math.min(4, newLevel));
        if (!primed) {
            primed = true;
            level = lvl;
            snap();
            kick();
            return;
        }
        if (lvl == level) return;
        long now = SystemClock.uptimeMillis();
        update(now);
        float current = flameScale(now);
        int old = level;
        level = lvl;
        if (!animates()) {
            snap();
        } else if (lvl == 0) {
            outFrom = current;
            phase = OUT;
            phaseStart = now;
        } else if (old == 0) {
            phase = RELIGHT;
            phaseStart = now;
            scaleFrom = scaleTo = LEVEL_SCALE[lvl];
            scaleStart = now;
        } else {
            scaleFrom = baseScale(now);
            scaleTo = LEVEL_SCALE[lvl];
            scaleStart = now;
        }
        kick();
    }

    /** A steady flame (Ethernet) only breathes slowly and does not sway. */
    @Override
    public void setSteady(boolean steady) {
        if (this.steady == steady) return;
        this.steady = steady;
        invalidateSelf();
    }

    /** With reduced motion the flame is still, there is no smoke or spark, and level changes are instant. */
    @Override
    public void setReducedMotion(boolean reducedMotion) {
        if (reduced == reducedMotion) return;
        reduced = reducedMotion;
        if (reduced) snap();
        kick();
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        kick();
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        snap();
        kick();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (changed) {
            if (!visible) snap();
            kick();
        }
        return changed;
    }

    // ---- state ----

    private boolean animates() {
        return running && !reduced && isVisible();
    }

    private boolean wantsFrames() {
        return running && isVisible() && ((level > 0 && !reduced) || phase != IDLE);
    }

    private void kick() {
        unscheduleSelf(tick);
        if (wantsFrames()) scheduleSelf(tick, SystemClock.uptimeMillis() + FRAME_MS);
        invalidateSelf();
    }

    /** Jumps to the end state of whatever is playing. */
    private void snap() {
        phase = IDLE;
        scaleFrom = scaleTo = LEVEL_SCALE[level];
    }

    private void update(long now) {
        if (phase == OUT && now - phaseStart >= OUT_END_MS) phase = IDLE;
        else if (phase == RELIGHT && now - phaseStart >= GROW_DELAY_MS + GROW_MS) phase = IDLE;
    }

    private float baseScale(long now) {
        float p = clamp01((now - scaleStart) / (float) LEVEL_MS);
        return scaleFrom + (scaleTo - scaleFrom) * decel(p);
    }

    private float flameScale(long now) {
        switch (phase) {
            case OUT: {
                float e = (now - phaseStart) / (float) OUT_MS;
                return e >= 1f ? 0f : outFrom * (1f - e * e);
            }
            case RELIGHT: {
                float g = (now - phaseStart - GROW_DELAY_MS) / (float) GROW_MS;
                return g <= 0f ? 0f : baseScale(now) * backOut(Math.min(1f, g));
            }
            default:
                return level == 0 ? 0f : baseScale(now);
        }
    }

    private float flameBright(long now) {
        if (phase != OUT) return 1f;
        float e = clamp01((now - phaseStart) / (float) OUT_MS);
        return 1f - e * (float) Math.sqrt(e);
    }

    // ---- drawing ----

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        update(now);
        float s = Math.min(b.width(), b.height()) / BOX;
        int save = alpha < 255 ? c.saveLayerAlpha(b.left, b.top, b.right, b.bottom, alpha) : c.save();
        c.clipRect(b);
        c.translate(b.exactCenterX() - 12f * s, b.exactCenterY() - 12f * s);
        c.scale(s, s);

        float scale = flameScale(now);
        float bright = flameBright(now);
        float hMul = 1f, wMul = 1f, sway = 0f;
        if (running && !reduced && isVisible()) {
            float t = (now - epoch) / 1000f;
            if (steady) {
                float breath = sin(t / 4.2f);
                hMul = 1f + 0.035f * breath;
                wMul = 1f + 0.02f * breath;
            } else {
                hMul = 1f + 0.055f * sin(0.83f * t) + 0.035f * sin(1.71f * t + 0.21f)
                        + 0.025f * sin(2.63f * t + 0.33f) + 0.03f * noise(t * 1.9f);
                wMul = 1f + 0.045f * sin(1.37f * t + 0.14f) + 0.03f * noise(t * 2.2f + 31f);
                sway = 0.45f * sin(0.47f * t + 0.1f) + 0.28f * sin(1.13f * t + 0.38f) + 0.2f * noise(t * 1.3f + 17f);
            }
        }
        float h = FLAME_H * scale * hMul;
        float w = FLAME_W * Math.min(1f, scale * 2.2f) * (0.5f + 0.5f * scale) * wMul;
        float lean = sway * scale;
        float vis = scale * bright;

        if (vis > 0.01f) {
            float cx = WICK_X + lean * 0.4f, cy = WICK_Y - h * 0.45f;
            float rx = 2.6f + 3.4f * scale, ry = rx * 1.2f;
            matrix.setScale(rx, ry);
            matrix.postTranslate(cx, cy);
            glowShader.setLocalMatrix(matrix);
            glowPaint.setAlpha((int) (255 * bright * (0.5f + 0.5f * scale)));
            oval.set(cx - rx, cy - ry, cx + rx, cy + ry);
            c.drawOval(oval, glowPaint);
        }

        drawLamp(c, vis);

        if (phase == OUT) drawSmoke(c, now);
        if (phase == RELIGHT) drawSpark(c, now);

        if (h > 0.15f && bright > 0.01f) {
            float by = WICK_Y + 0.2f;
            teardrop(flame, WICK_X, by, h, w, lean);
            matrix.setScale(1f, h);
            matrix.postTranslate(0f, by - h);
            flameShader.setLocalMatrix(matrix);
            flamePaint.setAlpha((int) (255 * bright));
            c.drawPath(flame, flamePaint);
            edgePaint.setAlpha((int) (150 * bright));
            c.drawPath(flame, edgePaint);

            float cby = WICK_Y - h * 0.06f, ch = h * 0.58f, cw = w * 0.5f;
            teardrop(core, WICK_X + lean * 0.05f, cby, ch, cw, lean * 0.55f);
            matrix.setScale(1f, ch);
            matrix.postTranslate(0f, cby - ch);
            coreShader.setLocalMatrix(matrix);
            corePaint.setAlpha((int) (255 * bright * bright));
            c.drawPath(core, corePaint);
        }
        c.restoreToCount(save);
    }

    private void drawLamp(Canvas c, float warmth) {
        c.drawOval(foot, footPaint);
        c.drawOval(foot, outlinePaint);
        c.drawPath(body, bodyPaint);
        if (warmth > 0.01f) {
            warmPaint.setAlpha((int) (190 * warmth));
            c.drawPath(body, warmPaint);
        }
        c.drawPath(opening, openingPaint);
        if (warmth > 0.01f) {
            warmPaint.setAlpha((int) (255 * warmth));
            c.drawPath(opening, warmPaint);
        }
        c.drawPath(opening, openingEdge);
        for (int i = 0; i < DOTS.length; i += 2) c.drawCircle(DOTS[i], DOTS[i + 1], 0.42f, dotPaint);
        c.drawPath(rimLip, lipPaint);
        c.drawPath(sheen, sheenPaint);
        c.drawPath(body, outlinePaint);
        c.drawLine(WICK_X - 0.9f, WICK_Y + 0.7f, WICK_X, WICK_Y, wickPaint);
    }

    private void drawSmoke(Canvas c, long now) {
        long since = now - phaseStart;
        // the wick glows for a moment after the flame dies
        float ember = 1f - since / (float) (SMOKE_DELAY_MS + SMOKE_MS * 0.6f);
        if (ember > 0f) {
            emberPaint.setAlpha((int) (255 * ember));
            c.drawCircle(WICK_X, WICK_Y, 0.5f, emberPaint);
        }
        if (reduced) return;
        float sp = (since - SMOKE_DELAY_MS) / (float) SMOKE_MS;
        if (sp <= 0f || sp >= 1f) return;
        float head = 10.5f * decel(sp);
        float tp = (sp - 0.3f) / 0.7f;
        float tail = tp <= 0f ? 0f : Math.min(head - 0.6f, 8.5f * tp * tp);
        if (head - tail < 0.3f) return;
        smoke.reset();
        for (int i = 0; i < SMOKE_POINTS; i++) {
            float d = tail + (head - tail) * i / (SMOKE_POINTS - 1);
            float curl = (0.2f + 0.17f * d) * (float) Math.sin(d * 0.9f - sp * 5.5f);
            float x = WICK_X - 0.28f * d + curl, y = WICK_Y - 0.3f - d;
            if (i == 0) smoke.moveTo(x, y);
            else smoke.lineTo(x, y);
        }
        float top = WICK_Y - 0.3f - head;
        matrix.setScale(1f, head - tail);
        matrix.postTranslate(0f, top);
        smokeShader.setLocalMatrix(matrix);
        smokePaint.setStrokeWidth(0.55f + 0.35f * sp);
        smokePaint.setAlpha((int) (230 * (1f - sp)));
        c.drawPath(smoke, smokePaint);
    }

    private void drawSpark(Canvas c, long now) {
        float k = (now - phaseStart) / (float) SPARK_MS;
        if (k >= 1.5f) return;
        float r = 0.5f + 2.8f * decel(Math.min(1f, k));
        float a = k < 0.25f ? k / 0.25f : 1f - (k - 0.25f) / 1.25f;
        float cx = WICK_X, cy = WICK_Y - 0.5f;
        matrix.setScale(r, r);
        matrix.postTranslate(cx, cy);
        sparkShader.setLocalMatrix(matrix);
        sparkPaint.setAlpha((int) (255 * a));
        c.drawCircle(cx, cy, r, sparkPaint);
        rayPaint.setAlpha((int) (255 * a));
        for (int i = 0; i < 4; i++) {
            double ang = Math.toRadians(45 + 90 * i + 30 * k);
            float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
            c.drawLine(cx + cs * r * 0.55f, cy + sn * r * 0.55f, cx + cs * r * 1.15f, cy + sn * r * 1.15f, rayPaint);
        }
    }

    /** A candle-flame teardrop standing on (bx, by): round bulb low down, pointed tip leaning by {@code lean}. */
    private static void teardrop(Path p, float bx, float by, float h, float w, float lean) {
        float tx = bx + lean, ty = by - h;
        float ml = lean * 0.35f;
        float wy = by - Math.max(w * 1.1f, h * 0.24f);
        float up = (wy - ty) * 0.35f, down = (by - wy) * 0.55f;
        p.reset();
        p.moveTo(tx, ty);
        p.cubicTo(tx + w * 0.25f, ty + h * 0.3f, bx + ml + w, wy - up, bx + w, wy);
        p.cubicTo(bx + w, wy + down, bx + w * 0.55f, by, bx, by);
        p.cubicTo(bx - w * 0.55f, by, bx - w, wy + down, bx - w, wy);
        p.cubicTo(bx + ml - w, wy - up, tx - w * 0.25f, ty + h * 0.3f, tx, ty);
        p.close();
    }

    private void buildLamp() {
        body.moveTo(1.6f, 14.2f);
        body.cubicTo(1.8f, 18.6f, 5.4f, 21.0f, 9.6f, 21.0f);
        body.cubicTo(13.2f, 21.0f, 15.6f, 19.4f, 16.8f, 16.6f);
        body.cubicTo(17.6f, 15.0f, 18.6f, 13.6f, 20.2f, 12.4f);
        body.quadTo(20.5f, 12.0f, 19.9f, 11.9f);
        body.cubicTo(18.2f, 12.1f, 16.8f, 12.6f, 15.4f, 13.0f);
        body.cubicTo(12.0f, 11.6f, 5.0f, 11.8f, 1.6f, 14.2f);
        body.close();

        opening.moveTo(2.6f, 14.2f);
        opening.cubicTo(5.6f, 12.6f, 11.8f, 12.5f, 15.2f, 13.4f);
        opening.cubicTo(16.6f, 13.0f, 17.8f, 12.7f, 19.2f, 12.4f);
        opening.cubicTo(17.6f, 13.4f, 16.6f, 14.0f, 15.4f, 14.6f);
        opening.cubicTo(11.6f, 16.4f, 5.0f, 16.2f, 2.6f, 14.2f);
        opening.close();

        rimLip.moveTo(2.7f, 14.65f);
        rimLip.cubicTo(5.1f, 16.65f, 11.6f, 16.85f, 15.5f, 15.0f);
        rimLip.cubicTo(16.7f, 14.4f, 17.7f, 13.8f, 19.4f, 12.75f);

        sheen.moveTo(3.2f, 15.4f);
        sheen.quadTo(4.0f, 18.4f, 6.9f, 19.7f);
    }

    // ---- helpers ----

    private float noise(float x) {
        int i = (int) Math.floor(x);
        float f = x - i;
        float s = f * f * (3f - 2f * f);
        float a = noise[i & 255], b = noise[(i + 1) & 255];
        return a + (b - a) * s;
    }

    /** sin of a phase given in turns. */
    private static float sin(float turns) {
        return (float) Math.sin(turns * 2.0 * Math.PI);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float decel(float p) {
        float q = 1f - p;
        return 1f - q * q;
    }

    private static float backOut(float x) {
        float s = 1.4f, q = x - 1f;
        return 1f + (s + 1f) * q * q * q + s * q * q;
    }

    private static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    private static Paint stroke(int color, float width) {
        Paint p = fill(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
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
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
