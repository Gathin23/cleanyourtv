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
    private final float dp, sz;

    FloatingClockView(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        sz = 94f * dp;
        paint.setColor(-1);
        paint.setTextSize(sz);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setHinting(Paint.HINTING_OFF);
        paint.setTypeface(ctx.getResources().getFont(R.font.outfit_extralight));
    }

    @Override
    protected void onDraw(Canvas c) {
        LocalDateTime now = LocalDateTime.now();
        int h = now.getHour(), m = now.getMinute(), sc = now.getSecond();
        float frac = now.getNano() * 1e-9f, sec = h * 3600 + m * 60 + sc + frac;
        float t = (System.currentTimeMillis() % 86400000L) * 0.001f;
        float dw = sz * 0.68f, x = (getWidth() - sz * 5f + dw) * 0.5f + sin(t * 0.025f) * getWidth() * 0.04f;
        float cy = getHeight() * 0.5f + sin(t * 0.02f + 1f) * getHeight() * 0.04f;

        float[] carry = new float[3];
        for (int i = 0; i < 7; i++) {
            float ph = i * HALF, sign = (i & 1) == 0 ? -1f : 1f;
            float bx = x + sin(t * 0.5f + ph + HALF) * 5f * dp;
            float by = cy + sign * 8f * dp + sin(t * 0.75f + ph) * 9f * dp;
            float br = sign * 6.5f + sin(t * 0.4f + ph) * 4f;
            int col = i - (i > 5 ? 2 : i > 2 ? 1 : 0);
            int g = i == 2 ? 10 : i == 5 ? 11 : col == 0 ? h / 10 : col == 1 ? h % 10 : col == 2 ? m / 10 : col == 3 ? m % 10 : sc / 10;
            if (g > 9 || !carryState(col, sec, h, g, carry)) {
                glyph(c, g, bx, by, br, g > 9 ? 235 : 255);
            } else {
                for (int j = 0; j < 2; j++) {
                    float q = j == 0 ? carry[2] : 1f - carry[2], dir = j == 0 ? -1f : 1f;
                    int a = (int) (255f * (1f - q));
                    if (a > 2) glyph(c, (int) carry[j],
                            bx + dir * q * (1f - q) * 36f * dp, by + dir * q * sz * HALF, br + dir * q * 9f, a);
                }
            }
            x += (g > 9 || i == 1 || i == 4) ? sz * 0.54f : dw;
        }

        float sx = x + dw * 0.2f;
        for (int k = -3; k <= 4; k++) {
            float dy = (k - frac) * sz, n = (dy < 0f ? -dy : dy) / (sz * 2.75f);
            if (n < 1f) {
                float arg = (dy / sz - t) * 0.85f;
                glyph(c, ((sc % 10 + k) % 10 + 10) % 10,
                        sx + sin(arg) * 16f * dp, cy + dy, sin(arg + HALF) * 10f,
                        (int) (255f * (1f - n) * (1f - n * 0.2f)));
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
        c.drawText(GLYPHS, idx, idx + 1, 0f, sz * 0.34f, paint);
        c.restore();
    }

    /** Computes carry state for col 0..4 (H1,H2,M1,M2,S1); writes [from, to, smooth p]. */
    static boolean carryState(int col, float sec, int curH, int cur, float[] out) {
        float p = col == 4 ? 10f : col == 3 ? 60f : col == 2 ? 600f : col == 1 ? 3600f : curH < 20 ? 36000f : 14400f;
        float dPrev = col == 0 ? sec - (curH / 10) * 36000f : sec % p;
        float dNext = p - dPrev;
        boolean app = dNext < HALF;
        if (!app && dPrev >= HALF) return false;

        int r = (col & 1) == 0 ? 6 : 10;
        int o = col < 2 ? (curH + (app ? 1 : 23)) % 24 : (cur + (app ? 1 : r - 1)) % r;
        if (col < 2) o = col == 0 ? o / 10 : o % 10;
        float u = (app ? HALF - dNext : HALF + dPrev) / 3.2f;
        out[0] = app ? cur : o;
        out[1] = app ? o : cur;
        out[2] = u * u * (3f - 2f * u);
        return true;
    }
}
