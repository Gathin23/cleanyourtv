package com.norma.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Big custom circular timer view for Islamic Prayer screensaver.
 * Displays a thick circular gauge measuring elapsed time from previous prayer to next prayer.
 * Features a glowing leading indicator and high-contrast OLED-optimized neon green track.
 */
public class PrayerCircleTimerView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();
    private float strokeWidthPx;
    private float progress = 0.0f; // 0.0f to 1.0f

    public PrayerCircleTimerView(Context context) {
        super(context);
        init();
    }

    public PrayerCircleTimerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PrayerCircleTimerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        strokeWidthPx = 14f * density;

        // Background circle track: dark deep emerald-charcoal
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidthPx);
        trackPaint.setColor(Color.parseColor("#152E22"));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        // Subtle glow under active progress arc
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(strokeWidthPx + 8f * density);
        glowPaint.setColor(Color.parseColor("#2600E676"));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);

        // Active progress arc: vibrant high-visibility neon emerald green
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setColor(Color.parseColor("#00E676"));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        // Glowing thumb at the head of the arc
        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(Color.parseColor("#8000E676"));

        thumbCorePaint.setStyle(Paint.Style.FILL);
        thumbCorePaint.setColor(Color.WHITE);
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        float padding = strokeWidthPx / 2f + 8f * density;

        float diameter = Math.min(w, h);
        float left = (w - diameter) / 2f + padding;
        float top = (h - diameter) / 2f + padding;
        float right = (w + diameter) / 2f - padding;
        float bottom = (h + diameter) / 2f - padding;

        arcRect.set(left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = arcRect.centerX();
        float cy = arcRect.centerY();
        float radius = arcRect.width() / 2f;

        if (radius <= 0) return;

        // 1. Draw full 360-degree background track
        canvas.drawCircle(cx, cy, radius, trackPaint);

        // 2. Draw active green progress arc
        if (progress > 0.001f) {
            float sweepAngle = 360f * progress;

            // Ambient glow
            canvas.drawArc(arcRect, -90f, sweepAngle, false, glowPaint);

            // Crisp arc
            canvas.drawArc(arcRect, -90f, sweepAngle, false, progressPaint);

            // 3. Draw glowing thumb at leading edge
            float density = getResources().getDisplayMetrics().density;
            double angleRad = Math.toRadians(-90f + sweepAngle);
            float thumbX = (float) (cx + radius * Math.cos(angleRad));
            float thumbY = (float) (cy + radius * Math.sin(angleRad));

            canvas.drawCircle(thumbX, thumbY, 9f * density, thumbPaint);
            canvas.drawCircle(thumbX, thumbY, 5f * density, thumbCorePaint);
        }
    }
}
