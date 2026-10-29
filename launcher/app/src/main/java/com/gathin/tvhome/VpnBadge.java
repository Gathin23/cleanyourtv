package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

/**
 * The green "VPN" capsule drawn inside the network pill while a VPN is up: a lock and the word VPN, dark on
 * green, so it reads on both the frosted and the focused (white) pill. With a VPN app configured and no VPN
 * up, the same capsule turns red, without the lock, so a dropped VPN is noticed.
 */
final class VpnBadge extends ReplacementSpan {

    private static final int GREEN = 0xFF30D158;
    private static final int INK = 0xFF06290F;
    private static final int RED = 0xFFFF453A;
    private static final int INK_OFF = 0xFFFFFFFF;

    private final Drawable lock;
    private final float density;
    private final int fill, ink;
    private final RectF rect = new RectF();

    /** @param lock the lock icon for the "up" badge, or null for the red "off" badge */
    VpnBadge(Drawable lock, float density) {
        this.fill = lock != null ? GREEN : RED;
        this.ink = lock != null ? INK : INK_OFF;
        this.lock = lock != null ? lock.mutate() : null;
        if (this.lock != null) this.lock.setTint(ink);
        this.density = density;
    }

    private float textSize(Paint p) {
        return p.getTextSize() * 0.78f;
    }

    private float lockSize(Paint p) {
        return lock != null ? p.getTextSize() * 0.8f : 0;
    }

    private float lockGap() {
        return lock != null ? 4 * density : 2 * density;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float old = paint.getTextSize();
        paint.setTextSize(textSize(paint));
        float w = paint.measureText(text, start, end);
        paint.setTextSize(old);
        // margin + left pad + lock + gap + text + right pad
        return (int) (8 * density + 7 * density + lockSize(paint) + lockGap() + w + 9 * density);
    }

    @Override
    public void draw(@NonNull Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                     @NonNull Paint paint) {
        float base = paint.getTextSize();
        float lockPx = lockSize(paint);
        float h = base * 1.3f;
        float cy = (top + bottom) / 2f;
        float left = x + 8 * density;
        float right = x + getSize(paint, text, start, end, null);
        rect.set(left, cy - h / 2f, right, cy + h / 2f);

        int oldColor = paint.getColor();
        paint.setColor(fill);
        c.drawRoundRect(rect, h / 2f, h / 2f, paint);

        int lx = (int) (left + 7 * density);
        if (lock != null) {
            lock.setBounds(lx, (int) (cy - lockPx / 2f), (int) (lx + lockPx), (int) (cy + lockPx / 2f));
            lock.draw(c);
        }

        paint.setColor(ink);
        paint.setTextSize(textSize(paint));
        Paint.FontMetrics fm = paint.getFontMetrics();
        float ty = cy - (fm.ascent + fm.descent) / 2f;
        c.drawText(text, start, end, lx + lockPx + lockGap(), ty, paint);
        paint.setTextSize(base);
        paint.setColor(oldColor);
    }
}
