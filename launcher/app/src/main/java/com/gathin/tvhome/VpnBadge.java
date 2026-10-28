package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

/**
 * The green "VPN" capsule drawn inside the network pill while a VPN is up: a lock and the word VPN, dark on
 * green, so it reads on both the frosted and the focused (white) pill. With a VPN app configured and no VPN
 * up, the same capsule turns red, without the lock, so a dropped VPN is noticed.
 */
final class VpnBadge extends ReplacementSpan {

    static final int GREEN = 0xFF30D158;
    static final int INK = 0xFF06290F;
    static final int RED = 0xFFFF453A;
    static final int INK_OFF = 0xFFFFFFFF;

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

    /** vpn_pill: what the VPN pill shows, from {@link #pillState}. */
    static final int PILL_HIDDEN = 0, PILL_UP = 1, PILL_OFF = 2;

    /**
     * vpn_pill: green while any VPN is up, red "VPN off" while none is but the configured VPN app is installed (so a
     * dropped VPN is noticed), hidden while neither is true: no pill on a TV without a VPN.
     */
    static int pillState(boolean vpnUp, boolean vpnAppInstalled) {
        return vpnUp ? PILL_UP : vpnAppInstalled ? PILL_OFF : PILL_HIDDEN;
    }

    /**
     * vpn_pill: the pill's background. At rest it is an outline in the state colour and only the focused state is
     * filled solid with a white ring — a solid pill at rest read as already selected, so OK pressed on a tile was
     * taken for a press on the pill.
     */
    static StateListDrawable pillBackground(boolean up, float density) {
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focused = pillShape(up, density);
        focused.setStroke(Math.round(3 * density), 0xFFFFFFFF);
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        GradientDrawable rest = new GradientDrawable();
        rest.setColor(0x00000000);
        rest.setCornerRadius(21 * density);
        rest.setStroke(Math.round(2 * density), up ? GREEN : RED);
        bg.addState(new int[0], rest);
        return bg;
    }

    /**
     * vpn_pill: the pill's text and icon colour. At rest the pill is an outline, so the ink is the state colour;
     * on the solid focused fill it is the contrasting ink.
     */
    static int pillInkColor(boolean up, boolean focused) {
        if (up) return focused ? INK : GREEN;
        return focused ? INK_OFF : RED;
    }

    private static GradientDrawable pillShape(boolean up, float density) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(up ? GREEN : RED);
        shape.setCornerRadius(21 * density);
        return shape;
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
