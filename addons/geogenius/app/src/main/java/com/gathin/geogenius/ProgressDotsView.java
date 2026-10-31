package com.gathin.geogenius;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * Minimal 10-Dot Question Progress Strip + Streak Flame Badge (Zero text clutter).
 * Each dot: 0 = upcoming (hollow), 1 = correct (emerald), -1 = missed (coral).
 */
final class ProgressDotsView extends View {

    private int totalDots = 10;
    private int currentIndex = 0;
    private int streak = 0;
    private final int[] states = new int[12];

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    ProgressDotsView(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2f * d);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(12f * d);
        textPaint.setColor(0xFFFBBF24);
    }

    void reset(int total) {
        this.totalDots = Math.min(12, Math.max(1, total));
        this.currentIndex = 0;
        this.streak = 0;
        for (int i = 0; i < states.length; i++) states[i] = 0;
        invalidate();
    }

    void setCurrentQuestion(int index, int currentStreak) {
        this.currentIndex = index;
        this.streak = currentStreak;
        invalidate();
    }

    void markResult(int index, boolean correct, int newStreak) {
        if (index >= 0 && index < states.length) {
            states[index] = correct ? 1 : -1;
        }
        this.streak = newStreak;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float spacing = 18f * d;
        float totalW = (totalDots - 1) * spacing;
        float startX = (w - totalW) * 0.5f;
        float cy = h * 0.5f;

        for (int i = 0; i < totalDots; i++) {
            float x = startX + i * spacing;
            int st = states[i];
            if (st == 1) {
                dotPaint.setStyle(Paint.Style.FILL);
                dotPaint.setColor(0xFF10B981);
                canvas.drawCircle(x, cy, 4.8f * d, dotPaint);
            } else if (st == -1) {
                dotPaint.setStyle(Paint.Style.FILL);
                dotPaint.setColor(0xFFEF4444);
                canvas.drawCircle(x, cy, 4.8f * d, dotPaint);
            } else if (i == currentIndex) {
                dotPaint.setStyle(Paint.Style.FILL);
                dotPaint.setColor(0xFFFBBF24);
                canvas.drawCircle(x, cy, 3.6f * d, dotPaint);
                ringPaint.setColor(0xFFFDE047);
                canvas.drawCircle(x, cy, 6.8f * d, ringPaint);
            } else {
                dotPaint.setStyle(Paint.Style.FILL);
                dotPaint.setColor(0x4494A3B8);
                canvas.drawCircle(x, cy, 3.5f * d, dotPaint);
            }
        }

        if (streak >= 2) {
            canvas.drawText("🔥 ×" + streak, startX + totalW + 18f * d, cy + 4.5f * d, textPaint);
        }
    }
}

