package com.gathin.hindipanchang;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Minimalist Circular Icon-Only Button (No Hindi/English text) with muted,
 * warm temple-grade color grading across all screens:
 * - TYPE_CLOSE (0): Circular button with a crisp vector '✕' (warm maroon / muted coral)
 * - TYPE_REFRESH (1): Circular button with a crisp vector '↻' (deep sage-bronze / antique gold)
 * - TYPE_CHEVRON_LEFT (2): Circular month navigation '<' chevron
 * - TYPE_CHEVRON_RIGHT (3): Circular month navigation '>' chevron
 */
final class CircleIconButtonView extends View {

    static final int TYPE_CLOSE = 0;
    static final int TYPE_REFRESH = 1;
    static final int TYPE_CHEVRON_LEFT = 2;
    static final int TYPE_CHEVRON_RIGHT = 3;

    private final int type;
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final Path arrowPath = new Path();

    CircleIconButtonView(Context context, int type, OnClickListener onClick) {
        super(context);
        this.type = type;
        float d = context.getResources().getDisplayMetrics().density;

        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        if (onClick != null) setOnClickListener(onClick);

        glowPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStyle(Paint.Style.FILL);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(1.4f * d);

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(2.1f * d);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);

        arrowPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        animate().scaleX(gainFocus ? 1.07f : 1.0f)
                .scaleY(gainFocus ? 1.07f : 1.0f)
                .setDuration(130)
                .start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f - 3.5f * d;
        boolean focused = isFocused();

        if (type == TYPE_CLOSE) {
            if (focused) {
                glowPaint.setColor(0x28B85A52);
                canvas.drawCircle(cx, cy, r + 2.5f * d, glowPaint);
                fillPaint.setColor(0xCC3B1A18); // Deep warm maroon glass (never neon red)
                ringPaint.setColor(0xFFD47A72); // Muted terracotta-coral ring
                iconPaint.setColor(0xFFF2D5D2); // Soft warm rose-ivory '✕'
            } else {
                fillPaint.setColor(0x551A1110);
                ringPaint.setColor(0x669E5852);
                iconPaint.setColor(0xFFC48680);
            }
            canvas.drawCircle(cx, cy, r, fillPaint);
            canvas.drawCircle(cx, cy, r, ringPaint);

            float s = r * 0.34f;
            canvas.drawLine(cx - s, cy - s, cx + s, cy + s, iconPaint);
            canvas.drawLine(cx + s, cy - s, cx - s, cy + s, iconPaint);

        } else if (type == TYPE_REFRESH) {
            if (focused) {
                glowPaint.setColor(0x288FA868);
                canvas.drawCircle(cx, cy, r + 2.5f * d, glowPaint);
                fillPaint.setColor(0xCC1E2E20); // Deep muted forest/olive glass (never neon green)
                ringPaint.setColor(0xFFC8B268); // Antique warm gold ring
                iconPaint.setColor(0xFFE8D8A0); // Warm parchment gold '↻'
                arrowPaint.setColor(0xFFE8D8A0);
            } else {
                fillPaint.setColor(0x55141A14);
                ringPaint.setColor(0x666E8E68);
                iconPaint.setColor(0xFFB8C49A);
                arrowPaint.setColor(0xFFB8C49A);
            }
            canvas.drawCircle(cx, cy, r, fillPaint);
            canvas.drawCircle(cx, cy, r, ringPaint);

            float ar = r * 0.42f;
            arcRect.set(cx - ar, cy - ar, cx + ar, cy + ar);
            canvas.drawArc(arcRect, 35f, 285f, false, iconPaint);

            double endRad = Math.toRadians(-40.0);
            float ax = cx + (float) Math.cos(endRad) * ar;
            float ay = cy + (float) Math.sin(endRad) * ar;
            float s = 3.2f * d;
            arrowPath.reset();
            arrowPath.moveTo(ax + s * 0.9f, ay + s * 0.2f);
            arrowPath.lineTo(ax - s * 0.8f, ay + s * 0.9f);
            arrowPath.lineTo(ax - s * 0.3f, ay - s * 0.9f);
            arrowPath.close();
            canvas.drawPath(arrowPath, arrowPaint);

        } else {
            // TYPE_CHEVRON_LEFT or TYPE_CHEVRON_RIGHT
            if (focused) {
                glowPaint.setColor(0x28C89E52);
                canvas.drawCircle(cx, cy, r + 2.5f * d, glowPaint);
                fillPaint.setColor(0xCC2A1E12); // Deep warm bronze glass (no bright yellow rectangle)
                ringPaint.setColor(0xFFD8AF64); // Warm antique gold ring
                iconPaint.setColor(0xFFF5DFB0); // Warm cream-gold chevron
            } else {
                fillPaint.setColor(0x4014100C);
                ringPaint.setColor(0x448A7250);
                iconPaint.setColor(0xFFB89E78);
            }
            canvas.drawCircle(cx, cy, r, fillPaint);
            canvas.drawCircle(cx, cy, r, ringPaint);

            float cw = r * 0.24f;
            float ch = r * 0.38f;
            arrowPath.reset();
            if (type == TYPE_CHEVRON_LEFT) {
                arrowPath.moveTo(cx + cw * 0.6f, cy - ch);
                arrowPath.lineTo(cx - cw * 0.8f, cy);
                arrowPath.lineTo(cx + cw * 0.6f, cy + ch);
            } else {
                arrowPath.moveTo(cx - cw * 0.6f, cy - ch);
                arrowPath.lineTo(cx + cw * 0.8f, cy);
                arrowPath.lineTo(cx - cw * 0.6f, cy + ch);
            }
            canvas.drawPath(arrowPath, iconPaint);
        }
    }
}
