package com.gathin.photodream;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The shadow under a print, the same under every print and tilting with it (elevation shadows lean with a light
 * at the top of the screen). From Photo Prints by @shivamsingh262 (#936).
 */
final class PrintShadow extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF card = new RectF();
    private final float inset, radius;

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
