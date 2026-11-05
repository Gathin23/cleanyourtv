package io.github.nickld.frigateglance;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * A pill outline around a footer action that draws itself clockwise from the top centre as a hold key is held.
 * White while it is drawing; green once it closes, which is when letting go acts.
 */
final class PillProgress extends Drawable {
    private static final int DRAWING = 0xFFFFFFFF, CLOSED = 0xFF3DDC84;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final Path part = new Path();
    private final PathMeasure measure = new PathMeasure();
    private float progress;

    PillProgress(float strokePx) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokePx);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** 0 to 1; at 1 the pill is closed and turns green. */
    void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect b) {
        float inset = paint.getStrokeWidth() / 2f;
        RectF r = new RectF(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        float radius = r.height() / 2f;
        // Start at the top centre and go clockwise, so the stroke grows out from above the label.
        outline.reset();
        outline.moveTo(r.centerX(), r.top);
        outline.lineTo(r.right - radius, r.top);
        outline.arcTo(new RectF(r.right - 2 * radius, r.top, r.right, r.bottom), -90, 180);
        outline.lineTo(r.left + radius, r.bottom);
        outline.arcTo(new RectF(r.left, r.top, r.left + 2 * radius, r.bottom), 90, 180);
        outline.close();
        measure.setPath(outline, false);
    }

    @Override
    public void draw(Canvas canvas) {
        if (progress <= 0f) return;
        paint.setColor(progress >= 1f ? CLOSED : DRAWING);
        if (progress >= 1f) {
            canvas.drawPath(outline, paint);
            return;
        }
        part.reset();
        measure.getSegment(0, measure.getLength() * progress, part, true);
        canvas.drawPath(part, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
