package com.gathin.hindipanchang;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

/**
 * Minimalist Circular Icon Dock Orb + Short Label Underneath:
 * Draws the vector icon inside a sleek circular glass orb, with the 1-word Hindi title
 * ("सुविचार", "पंचांग", "व्रत/पर्व") cleanly printed below the circle.
 */
final class IconTileView extends View {

    static final int ICON_WISDOM = 0;   // सुविचार (Sacred Book / Scroll)
    static final int ICON_PANCHANG = 1; // पंचांग (Sun Path Arc)
    static final int ICON_VRAT = 2;     // व्रत/पर्व (Glowing Diya)

    private final int iconType;
    private String label;
    private boolean showBadgeDot;

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circleBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    IconTileView(Context context, int iconType, String label) {
        super(context);
        this.iconType = iconType;
        this.label = label;
        float d = context.getResources().getDisplayMetrics().density;

        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);

        glowPaint.setStyle(Paint.Style.FILL);
        circleFill.setStyle(Paint.Style.FILL);
        circleBorder.setStyle(Paint.Style.STROKE);

        iconStroke.setStyle(Paint.Style.STROKE);
        iconStroke.setStrokeWidth(2.1f * d);
        iconStroke.setStrokeCap(Paint.Cap.ROUND);
        iconStroke.setStrokeJoin(Paint.Join.ROUND);

        iconFill.setStyle(Paint.Style.FILL);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(12.5f * d);
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        labelPaint.setShadowLayer(3f * d, 0f, 1f * d, 0xDD000000);

        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(0xFFF97316);
    }

    void setLabel(String newLabel) {
        this.label = newLabel;
        invalidate();
    }

    void setBadgeDot(boolean hasDot) {
        if (this.showBadgeDot != hasDot) {
            this.showBadgeDot = hasDot;
            invalidate();
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        animate().scaleX(gainFocus ? 1.15f : 1.0f)
                .scaleY(gainFocus ? 1.15f : 1.0f)
                .setDuration(140)
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

        boolean focused = isFocused();
        int accent = iconType == ICON_WISDOM ? 0xFFFBBF24 : (iconType == ICON_PANCHANG ? 0xFFF59E0B : 0xFFF97316);

        float cx = w / 2f;
        float r = 24f * d;
        float cy = r + 5f * d;

        // 1. Outer Glow when Focused
        if (focused) {
            glowPaint.setColor(0x44F59E0B);
            canvas.drawCircle(cx, cy, r + 4.5f * d, glowPaint);
        }

        // 2. Circular Glass Orb Background & Rim
        circleFill.setColor(focused ? 0xEE2B1B10 : 0xAA140E0A);
        canvas.drawCircle(cx, cy, r, circleFill);

        circleBorder.setStrokeWidth((focused ? 2.2f : 1.2f) * d);
        circleBorder.setColor(focused ? accent : 0x88785634);
        canvas.drawCircle(cx, cy, r, circleBorder);

        // 3. Vector Icon inside the Circle
        iconStroke.setColor(focused ? 0xFFFFFBEB : accent);
        iconFill.setColor(focused ? 0xFFFFFBEB : accent);

        if (iconType == ICON_WISDOM) {
            float bw = 11.5f * d;
            float bh = 8.5f * d;
            path.reset();
            path.moveTo(cx, cy - bh * 0.4f);
            path.quadTo(cx - bw * 0.5f, cy - bh * 0.7f, cx - bw, cy - bh * 0.4f);
            path.lineTo(cx - bw, cy + bh * 0.65f);
            path.quadTo(cx - bw * 0.5f, cy + bh * 0.35f, cx, cy + bh * 0.65f);

            path.moveTo(cx, cy - bh * 0.4f);
            path.quadTo(cx + bw * 0.5f, cy - bh * 0.7f, cx + bw, cy - bh * 0.4f);
            path.lineTo(cx + bw, cy + bh * 0.65f);
            path.quadTo(cx + bw * 0.5f, cy + bh * 0.35f, cx, cy + bh * 0.65f);

            path.moveTo(cx, cy - bh * 0.4f);
            path.lineTo(cx, cy + bh * 0.65f);
            canvas.drawPath(path, iconStroke);

            canvas.drawCircle(cx, cy - bh * 0.9f, 2.2f * d, iconFill);
        } else if (iconType == ICON_PANCHANG) {
            float span = 13 * d;
            float baseY = cy + 6 * d;
            canvas.drawLine(cx - span, baseY, cx + span, baseY, iconStroke);

            path.reset();
            path.moveTo(cx - span * 0.85f, baseY);
            path.quadTo(cx, cy - 12 * d, cx + span * 0.85f, baseY);
            canvas.drawPath(path, iconStroke);

            iconFill.setColor(0xFFFDE047);
            canvas.drawCircle(cx + 2 * d, cy - 4 * d, 3.8f * d, iconFill);
        } else {
            float bowlW = 11.5f * d;
            float bowlY = cy + 1.5f * d;
            path.reset();
            path.moveTo(cx - bowlW, bowlY);
            path.quadTo(cx, bowlY + 10 * d, cx + bowlW, bowlY);
            path.close();
            canvas.drawPath(path, iconStroke);

            path.reset();
            float fy = bowlY - 1.5f * d;
            path.moveTo(cx, fy - 10 * d);
            path.quadTo(cx + 4.5f * d, fy - 3 * d, cx, fy);
            path.quadTo(cx - 4.5f * d, fy - 3 * d, cx, fy - 10 * d);
            path.close();
            iconFill.setColor(0xFFFDE047);
            canvas.drawPath(path, iconFill);

            if (showBadgeDot) {
                canvas.drawCircle(cx + r * 0.72f, cy - r * 0.72f, 3.6f * d, badgePaint);
            }
        }

        // 4. Short 1-word Hindi Label Underneath the Circle
        labelPaint.setColor(focused ? Color.WHITE : 0xFFE6DEC8);
        canvas.drawText(label, cx, h - 4 * d, labelPaint);
    }
}
