package io.github.shiruiliu.floatingclock;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;

import java.time.LocalDateTime;

/**
 * Full-screen floating digital clock (HH : MM . S1 S2) over a slow subtle ambient glow:
 * - First five digits and separators float organically in place with staggered offsets and
 *   independent slow bobbing/tilt; across a carry (+/- 1.6 s) the outgoing digit drifts slowly
 *   upward while the incoming digit floats up from below.
 * - Only the last second digit flows continuously upward as a swaying, fading stream.
 * - Uses hardware Canvas translation + SUBPIXEL_TEXT_FLAG | LINEAR_TEXT_FLAG + HINTING_OFF
 *   for jitter-free motion, plus slow continuous Lissajous burn-in drift.
 */
final class FloatingClockView extends View {

    private static final String GLYPHS = "0123456789:.";
    static final float CARRY_HALF_SEC = 1.6f;

    /** Subtle low-brightness ambient glow palette cycled smoothly over time. */
    private static final int[] PALETTE = {0x2B5CFF, 0x00A39A, 0x8A3FFC, 0xE0661B, 0xD6336C};
    private static final float GLOW_CYCLE_SEC = 60f;

    // Independent slow periods (rad/s) and staggered resting poses per glyph slot.
    private static final float[] FREQ_Y = {0.72f, 0.86f, 0.64f, 0.91f, 0.78f, 0.69f, 0.83f};
    private static final float[] FREQ_X = {0.48f, 0.57f, 0.43f, 0.53f, 0.61f, 0.49f, 0.55f};
    private static final float[] FREQ_R = {0.37f, 0.44f, 0.33f, 0.47f, 0.39f, 0.35f, 0.42f};
    private static final float[] PHASE  = {0.0f, 1.4f, 2.7f, 3.9f, 5.1f, 0.9f, 2.3f};
    private static final float[] BASE_Y = {-7f, 8f, -3f, -10f, 9f, -4f, -8f};
    private static final float[] BASE_R = {-6.5f, 5.5f, -3.0f, 7.0f, -7.5f, 4.0f, 6.0f};

    private final Paint paint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
    private final GradientDrawable glow = new GradientDrawable();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final float[] carry = new float[3];

    private final float dp;
    private final float sz;
    private final float step;
    private final float fadeR;
    private final float carryTravel;
    private final float dw;
    private final float pw;
    private final float streamGap;
    private final float baseline;

    private boolean running;
    private long startUptimeMs;
    private int lastGlowStep = -1;

    FloatingClockView(Context ctx) {
        super(ctx);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        dp = dm.density;
        sz = 94f * dp;
        step = sz * 0.98f;
        fadeR = step * 2.75f;
        carryTravel = sz * 1.55f;

        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(sz);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setHinting(Paint.HINTING_OFF);
        try {
            paint.setTypeface(ctx.getResources().getFont(R.font.outfit_extralight));
        } catch (Exception ignored) {
            paint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }

        float maxW = 0f;
        for (int i = 0; i <= 9; i++) {
            maxW = Math.max(maxW, paint.measureText(GLYPHS, i, i + 1));
        }
        dw = maxW * 1.16f;
        pw = dw * 0.58f;
        streamGap = dw * 0.16f;
        Paint.FontMetrics fm = paint.getFontMetrics();
        baseline = -(fm.ascent + fm.descent) * 0.5f;

        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.5f, 0.5f);
        setBackground(glow);
        updateGlow(0f);
    }

    void start() {
        running = true;
        startUptimeMs = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    void stop() {
        running = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        glow.setGradientRadius(Math.max(w, h) * 0.55f);
    }

    @Override
    protected void onDraw(Canvas c) {
        LocalDateTime now = LocalDateTime.now();
        int sc = now.getSecond();
        float frac = now.getNano() * 1e-9f;
        float sec = now.getHour() * 3600 + now.getMinute() * 60 + sc + frac;
        float t = (SystemClock.uptimeMillis() - startUptimeMs) * 0.001f;

        // Update the subtle radial ambient glow ~2 times per second to keep GPU work minimal.
        int glowStep = (int) (t * 2f);
        if (glowStep != lastGlowStep) {
            lastGlowStep = glowStep;
            updateGlow(t);
        }

        // Imperceptible slow Lissajous burn-in drift (~4 min cycle) around screen center.
        float driftX = (float) Math.sin(t * 0.025f) * getWidth() * 0.045f;
        float driftY = (float) Math.sin(t * 0.018f + 1.1f) * getHeight() * 0.035f;
        float totalW = dw * 6f + pw * 2f + streamGap;
        float x = (getWidth() - totalW) * 0.5f + driftX + dw * 0.5f;
        float cy = getHeight() * 0.5f + driftY;

        for (int i = 0; i < 7; i++) {
            float bx = x + (float) Math.cos(t * FREQ_X[i] + PHASE[i]) * 4.5f * dp;
            float by = cy + BASE_Y[i] * dp + (float) Math.sin(t * FREQ_Y[i] + PHASE[i]) * 9f * dp;
            float br = BASE_R[i] + (float) Math.sin(t * FREQ_R[i] + PHASE[i]) * 3.8f;

            if (i % 3 == 2) {
                glyph(c, 10 + i / 5, bx, by - (i == 2 ? sz * 0.05f : 0f), br, 235);
            } else if (!carryState(i - i / 3, sec, carry)) {
                glyph(c, (int) carry[0], bx, by, br, 255);
            } else {
                float p = carry[2];
                int outA = Math.round(255f * (1f - p));
                if (outA > 2) {
                    glyph(c, (int) carry[0],
                            bx - (float) Math.sin(p * Math.PI) * 9.9f * dp,
                            by - p * carryTravel, br - p * 9.5f, outA);
                }
                int inA = Math.round(255f * p);
                if (inA > 2) {
                    glyph(c, (int) carry[1],
                            bx + (float) Math.sin((1f - p) * Math.PI) * 9.9f * dp,
                            by + (1f - p) * carryTravel, br + (1f - p) * 9.5f, inA);
                }
            }
            x += (i % 3 != 0) ? (dw + pw) * 0.5f : dw;
        }

        // Last digit (seconds ones): continuous upward floating stream with gentle S-wave.
        float sx = x + streamGap;
        for (int k = -3; k <= 4; k++) {
            float dy = (k - frac) * step;
            float n = Math.abs(dy) / fadeR;
            if (n >= 1f) continue;
            int a = Math.round(255f * Math.max(0f, 1f - (float) Math.pow(n, 1.2)));
            if (a <= 2) continue;
            float arg = (dy / step) * 0.85f - t * 0.88f;
            glyph(c, (sc + k + 10) % 10,
                    sx + (float) Math.sin(arg) * 16f * dp, cy + dy,
                    (float) Math.cos(arg) * 10.5f, a);
        }

        if (running) postInvalidateOnAnimation();
    }

    private void updateGlow(float t) {
        float pos = (t / GLOW_CYCLE_SEC) % PALETTE.length;
        int idx = (int) pos;
        int next = (idx + 1) % PALETTE.length;
        int rgb = ((int) argb.evaluate(pos - idx, 0xFF000000 | PALETTE[idx], 0xFF000000 | PALETTE[next])) & 0x00FFFFFF;
        glow.setColors(new int[]{0x38000000 | rgb, 0x12000000 | rgb, 0xFF000000});
    }

    private void glyph(Canvas c, int idx, float x, float y, float deg, int a) {
        paint.setAlpha(a);
        c.save();
        c.translate(x, y);
        c.rotate(deg);
        c.drawText(GLYPHS, idx, idx + 1, 0f, baseline, paint);
        c.restore();
    }

    private static int digit(int sec, int col) {
        int v = col < 2 ? sec / 3600 : col < 4 ? (sec / 60) % 60 : sec % 60;
        return (col & 1) == 0 ? v / 10 : v % 10;
    }

    /**
     * Computes carry state for column {@code col} (0..4 for H1, H2, M1, M2, S1).
     * Writes [fromDigit, toDigit, quintic smootherstep p] into {@code out}.
     */
    static boolean carryState(int col, float sec, float[] out) {
        int d0 = digit((int) ((sec + 86400f - CARRY_HALF_SEC) % 86400f), col);
        int d1 = digit((int) ((sec + CARRY_HALF_SEC) % 86400f), col);
        out[0] = d0;
        if (d0 == d1) return false;
        float u = ((sec + CARRY_HALF_SEC) % 10f) / (CARRY_HALF_SEC * 2f);
        u = Math.max(0f, Math.min(1f, u));
        out[1] = d1;
        out[2] = u * u * u * (u * (u * 6f - 15f) + 10f);
        return true;
    }
}
