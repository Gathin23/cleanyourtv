package io.github.shivamsingh262.photoprints;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The shadow under a print, drawn here rather than by elevation: Android's own shadow comes from a light at the top
 * of the screen, so each print's leaned a different way and showed as a grey band down one side. This one is the
 * same under every print and tilts with it. It sits behind the photo, inset by the print's clear margin.
 */
final class PrintShadow extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF card = new RectF();
    private final float inset, radius;

    /**
     * @param inset  the clear margin around the photo, px
     * @param radius the photo's corner radius, px
     * @param blur   how soft the shadow is, px
     * @param dy     how far it falls below the photo, px
     * @param color  the shadow's colour, alpha included
     */
    PrintShadow(float inset, float radius, float blur, float dy, int color) {
        this.inset = inset;
        this.radius = radius;
        paint.setColor(0xFF101014);
        paint.setShadowLayer(blur, 0f, dy, color);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        card.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRoundRect(card, radius, radius, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
