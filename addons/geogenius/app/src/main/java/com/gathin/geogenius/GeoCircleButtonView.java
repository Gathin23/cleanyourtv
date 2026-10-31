package com.gathin.geogenius;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Minimalist Circular Icon-Only Button (No text):
 * - TYPE_CLOSE (0): '✕' (muted maroon / coral)
 * - TYPE_REFRESH (1): '↻' (muted sage-emerald / gold)
 * - TYPE_SOUND (2): '🔊' speaker wave icon
 * - TYPE_BACKSPACE (3): '⌫' undo letter icon
 * - TYPE_CHEVRON_LEFT (4): '‹' left chevron navigation
 * - TYPE_CHEVRON_RIGHT (5): '›' right chevron navigation
 */
final class GeoCircleButtonView extends View {

    static final int TYPE_CLOSE = 0;
    static final int TYPE_REFRESH = 1;
    static final int TYPE_SOUND = 2;
    static final int TYPE_BACKSPACE = 3;
    static final int TYPE_CHEVRON_LEFT = 4;
    static final int TYPE_CHEVRON_RIGHT = 5;

    private final int type;
    private boolean mutedState = false;
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final Path path = new Path();

    GeoCircleButtonView(Context context, int type, OnClickListener onClick) {
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
        ringPaint.setStrokeWidth(1.5f * d);

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(2.1f * d);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);

        arrowPaint.setStyle(Paint.Style.FILL);
    }

    void setMutedState(boolean muted) {
        this.mutedState = muted;
        invalidate();
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
                fillPaint.setColor(0xCC3B1A18);
                ringPaint.setColor(0xFFD47A72);
                iconPaint.setColor(0xFFF2D5D2);
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
                glowPaint.setColor(0x2810B981);
                canvas.drawCircle(cx, cy, r + 2.5f * d, glowPaint);
                fillPaint.setColor(0xCC13382E);
                ringPaint.setColor(0xFF34D399);
                iconPaint.setColor(0xFFA7F3D0);
                arrowPaint.setColor(0xFFA7F3D0);
            } else {
                fillPaint.setColor(0x550F241E);
                ringPaint.setColor(0x6610B981);
                iconPaint.setColor(0xFF6EE7B7);
                arrowPaint.setColor(0xFF6EE7B7);
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
            path.reset();
            path.moveTo(ax + s * 0.9f, ay + s * 0.2f);
            path.lineTo(ax - s * 0.8f, ay + s * 0.9f);
            path.lineTo(ax - s * 0.3f, ay - s * 0.9f);
            path.close();
            canvas.drawPath(path, arrowPaint);

        } else if (type == TYPE_SOUND) {
            if (focused) {
                glowPaint.setColor(0x2838BDF8);
                canvas.drawCircle(cx, cy, r + 2.5f * d, glowPaint);
                fillPaint.setColor(0xCC162B40);
                ringPaint.setColor(0xFFFBBF24);
                iconPaint.setColor(0xFFFDE047);
            } else {
                fillPaint.setColor(0x550F1E32);
                ringPaint.setColor(0x6664748B);
                iconPaint.setColor(mutedState ? 0xFF64748B : 0xFF93C5FD);
            }
            canvas.drawCircle(cx, cy, r, fillPaint);
            canvas.drawCircle(cx, cy, r, ringPaint);

            // Speaker cone
            path.reset();
            path.moveTo(cx - 5f * d, cy - 2.5f * d);
            path.lineTo(cx - 2f * d, cy - 2.5f * d);
            path.lineTo(cx + 2f * d, cy - 6f * d);
            path.lineTo(cx + 2f * d, cy + 6f * d);
            path.lineTo(cx - 2f * d, cy + 2.5f * d);
            path.lineTo(cx - 5f * d, cy + 2.5f * d);
            path.close();
            canvas.drawPath(path, iconPaint);

            if (!mutedState) {
                arcRect.set(cx + 1f * d, cy - 5f * d, cx + 6.5f * d, cy + 5f * d);
                canvas.drawArc(arcRect, -45f, 90f, false, iconPaint);
            } else {
                canvas.drawLine(cx - 6f * d, cy - 6f * d, cx + 6f * d, cy + 6f * d, iconPaint);
            }

        } else if (type == TYPE_CHEVRON_LEFT) {
            if (focused) {
                glowPaint.setColor(0x2838BDF8);
                canvas.drawCircle(cx, cy, r + 2.5f * d, glowPaint);
                fillPaint.setColor(0xCC162B40);
                ringPaint.setColor(0xFFFBBF24);
                iconPaint.setColor(0xFFFDE047);
            } else {
                fillPaint.setColor(0x550F1E32);
                ringPaint.setColor(0x6664748B);
                iconPaint.setColor(0xFFCBD5E1);
            }
            canvas.drawCircle(cx, cy, r, fillPaint);
            canvas.drawCircle(cx, cy, r, ringPaint);

            path.reset();
            path.moveTo(cx + 2.5f * d, cy - 6f * d);
            path.lineTo(cx - 3.5f * d, cy);
            path.lineTo(cx + 2.5f * d, cy + 6f * d);
            canvas.drawPath(path, iconPaint);

        } else if (type == TYPE_CHEVRON_RIGHT) {
            if (focused) {
                glowPaint.setColor(0x2838BDF8);
                canvas.drawCircle(cx, cy, r + 2.5f * d, glowPaint);
                fillPaint.setColor(0xCC162B40);
                ringPaint.setColor(0xFFFBBF24);
                iconPaint.setColor(0xFFFDE047);
            } else {
                fillPaint.setColor(0x550F1E32);
                ringPaint.setColor(0x6664748B);
                iconPaint.setColor(0xFFCBD5E1);
            }
            canvas.drawCircle(cx, cy, r, fillPaint);
            canvas.drawCircle(cx, cy, r, ringPaint);

            path.reset();
            path.moveTo(cx - 2.5f * d, cy - 6f * d);
            path.lineTo(cx + 3.5f * d, cy);
            path.lineTo(cx - 2.5f * d, cy + 6f * d);
            canvas.drawPath(path, iconPaint);

        } else {
            // TYPE_BACKSPACE ('⌫')
            if (focused) {
                glowPaint.setColor(0x28FBBF24);
                canvas.drawCircle(cx, cy, r + 2.5f * d, glowPaint);
                fillPaint.setColor(0xCC2E2214);
                ringPaint.setColor(0xFFFBBF24);
                iconPaint.setColor(0xFFFDE047);
            } else {
                fillPaint.setColor(0x55161F30);
                ringPaint.setColor(0x6694A3B8);
                iconPaint.setColor(0xFFCBD5E1);
            }
            canvas.drawCircle(cx, cy, r, fillPaint);
            canvas.drawCircle(cx, cy, r, ringPaint);

            canvas.drawLine(cx + 5f * d, cy, cx - 5f * d, cy, iconPaint);
            canvas.drawLine(cx - 5f * d, cy, cx - 1f * d, cy - 4f * d, iconPaint);
            canvas.drawLine(cx - 5f * d, cy, cx - 1f * d, cy + 4f * d, iconPaint);
        }
    }
}

