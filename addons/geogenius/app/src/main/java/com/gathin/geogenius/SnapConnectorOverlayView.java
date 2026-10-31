package com.gathin.geogenius;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Draws glowing emerald bezier connector lines between matched Left-Column
 * Countries (0..3) and Right-Column Flags (0..3) in Mode 6 (Snap Match).
 */
final class SnapConnectorOverlayView extends View {

    // matchRightForLeft[leftIdx] = rightIdx (or -1 if not yet matched)
    private final int[] matchRightForLeft = {-1, -1, -1, -1};
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curvePath = new Path();

    SnapConnectorOverlayView(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(5.5f * d);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(0x5510B981);

        corePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStrokeWidth(2.4f * d);
        corePaint.setStrokeCap(Paint.Cap.ROUND);
        corePaint.setColor(0xFF34D399);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFF34D399);
    }

    void reset() {
        for (int i = 0; i < 4; i++) matchRightForLeft[i] = -1;
        invalidate();
    }

    void setMatch(int leftIdx, int rightIdx) {
        if (leftIdx >= 0 && leftIdx < 4) {
            matchRightForLeft[leftIdx] = rightIdx;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float rowH = h / 4f;
        float startX = 4f * d;
        float endX = w - 4f * d;
        float midX = w * 0.5f;

        for (int l = 0; l < 4; l++) {
            int r = matchRightForLeft[l];
            if (r < 0 || r >= 4) continue;
            float y1 = (l + 0.5f) * rowH;
            float y2 = (r + 0.5f) * rowH;

            curvePath.reset();
            curvePath.moveTo(startX, y1);
            curvePath.cubicTo(midX, y1, midX, y2, endX, y2);

            canvas.drawPath(curvePath, glowPaint);
            canvas.drawPath(curvePath, corePaint);
            canvas.drawCircle(startX, y1, 3.8f * d, dotPaint);
            canvas.drawCircle(endX, y2, 3.8f * d, dotPaint);
        }
    }
}

