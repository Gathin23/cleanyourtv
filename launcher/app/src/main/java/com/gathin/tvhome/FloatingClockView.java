package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.time.LocalDateTime;

/**
 * Floating digital clock screensaver (HH:mm.ss), enabled by {@code floating_clock}: the first five
 * digits float gently in place and drift slowly upward across a carry (+/- 1.6 s), while the last
 * second digit flows continuously upward as a soft fading stream.
 */
final class FloatingClockView extends View {

    private static final String GLYPHS = "0123456789:.";
    private static final float HALF = 1.6f;

    private final Paint paint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
    private final float dp;

    FloatingClockView(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        paint.setColor(-1);
        paint.setTextSize(94f * dp);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setHinting(Paint.HINTING_OFF);
        paint.setTypeface(ctx.getResources().getFont(R.font.outfit_extralight));
    }

    @Override
    protected void onDraw(Canvas c) {
        LocalDateTime now = LocalDateTime.now();
        int sc = now.getSecond();
        float frac = now.getNano() * 1e-9f, sz = 94f * dp;
        float sec = now.getHour() * 3600 + now.getMinute() * 60 + sc + frac;
        float x = getWidth() * (0.5f + sin(sec * 0.02f) * 0.04f) - sz * 2.15f;
        float cy = getHeight() * (0.5f + sin(sec * 0.02f + HALF) * 0.04f);

        float[] carry = new float[3];
        for (int i = 0; i < 7; i++) {
            float a1 = sec * 0.5f + i * HALF, sgn = (i & 1) == 0 ? -1f : 1f;
            float s1 = sin(a1), s2 = sin(a1 + HALF);
            float bx = x + s2 * 4f * dp, by = cy + (sgn + s1) * 8f * dp, br = sgn * 6f + s2 * 4f;
            if (i % 3 == 2) {
                glyph(c, 10 + i / 5, bx, by, br, 235);
            } else if (!carryState(i - i / 3, sec, carry)) {
                glyph(c, (int) carry[0], bx, by, br, 255);
            } else {
                for (int j = 0; j < 2; j++) {
                    float q = j == 0 ? carry[2] : 1f - carry[2], dir = j == 0 ? -1f : 1f;
                    glyph(c, (int) carry[j], bx + dir * q * (1f - q) * 32f * dp,
                            by + dir * q * sz * HALF, br + dir * q * 8f, (int) (255f * (1f - q)));
                }
            }
            x += sz * (i % 3 != 0 ? 0.54f : 0.68f);
        }

        x += sz * 0.14f;
        for (int k = -3; k <= 4; k++) {
            float d = k - frac, n = (d < 0f ? -d : d) / 2.75f;
            if (n < 1f) {
                float arg = (d - sec) * 0.8f;
                glyph(c, (sc + k + 10) % 10, x + sin(arg) * 16f * dp, cy + d * sz,
                        sin(arg + HALF) * 10f, (int) (255f * (1f - n)));
            }
        }
        if (isAttachedToWindow()) postInvalidateOnAnimation();
    }

    private static float sin(float x) {
        return (float) Math.sin(x);
    }

    private void glyph(Canvas c, int idx, float x, float y, float deg, int a) {
        paint.setAlpha(a);
        c.save();
        c.translate(x, y);
        c.rotate(deg);
        c.drawText(GLYPHS, idx, idx + 1, 0f, 32f * dp, paint);
        c.restore();
    }

    private static int digit(int sec, int col) {
        int v = col < 2 ? sec / 3600 : col < 4 ? (sec / 60) % 60 : sec % 60;
        return (col & 1) == 0 ? v / 10 : v % 10;
    }

    /** Computes carry state for col 0..4 (H1,H2,M1,M2,S1); writes [from, to, smooth p]. */
    static boolean carryState(int col, float sec, float[] out) {
        int d0 = digit((int) ((sec + 86400f - HALF) % 86400f), col);
        int d1 = digit((int) ((sec + HALF) % 86400f), col);
        out[0] = d0;
        if (d0 == d1) return false;
        float u = ((sec + HALF) % 10f) / 3.2f;
        out[1] = d1;
        out[2] = u * u * (3f - 2f * u);
        return true;
    }
}
