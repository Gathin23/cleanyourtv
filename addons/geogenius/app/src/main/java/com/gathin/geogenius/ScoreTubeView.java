package com.gathin.geogenius;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The Vertical "Liquid Score Tube" (Right flank of Center Stage):
 * - Shows a sleek translucent glass cylinder from 0 at the bottom to maxScore at the top.
 * - Draws a golden marker line (★ Best) at the child's saved Personal Best score.
 * - Smoothly fills with glowing emerald-green liquid as points are earned, turning
 *   Supernova Gold the moment the child beats their Personal Best!
 */
final class ScoreTubeView extends View {

    private int maxScore = 100;
    private int bestScore = 0;
    private int currentScore = 0;
    private float animatedScore = 0f;

    private final Paint tubeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tubeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint liquidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crestPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bestLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tubeRect = new RectF();
    private final RectF fillRect = new RectF();
    private final Path clipPath = new Path();
    private ValueAnimator animator;

    ScoreTubeView(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;

        tubeBgPaint.setStyle(Paint.Style.FILL);
        tubeBgPaint.setColor(0x880B132B);

        tubeBorderPaint.setStyle(Paint.Style.STROKE);
        tubeBorderPaint.setStrokeWidth(1.6f * d);
        tubeBorderPaint.setColor(0x6694A3B8);

        bestLinePaint.setStyle(Paint.Style.STROKE);
        bestLinePaint.setStrokeWidth(2.2f * d);
        bestLinePaint.setColor(0xFFFBBF24);

        crestPaint.setStyle(Paint.Style.FILL);
        crestPaint.setColor(0x99FFFFFF);

        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    void configure(int maxScore, int bestScore, int initialScore) {
        this.maxScore = Math.max(10, maxScore);
        this.bestScore = Math.max(0, bestScore);
        this.currentScore = initialScore;
        this.animatedScore = initialScore;
        if (animator != null) animator.cancel();
        invalidate();
    }

    void setScoreAnimated(int newScore) {
        int target = Math.max(0, Math.min(maxScore, newScore));
        this.currentScore = target;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(animatedScore, target);
        animator.setDuration(420);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            animatedScore = (Float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float topPad = 24f * d;
        float botPad = 18f * d;
        float tubeW = 28f * d;
        float cx = w * 0.5f;

        tubeRect.set(cx - tubeW / 2f, topPad, cx + tubeW / 2f, h - botPad);
        float radius = tubeW / 2f;

        // Top Max Crown Label
        textPaint.setTextSize(10.5f * d);
        textPaint.setColor(0xFFFBBF24);
        canvas.drawText("🏆 " + maxScore, cx, topPad - 7f * d, textPaint);

        // Glass cylinder background
        canvas.drawRoundRect(tubeRect, radius, radius, tubeBgPaint);

        // Clip inside cylinder to draw rising liquid
        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(tubeRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        float frac = Math.max(0f, Math.min(1f, animatedScore / (float) maxScore));
        float fillH = tubeRect.height() * frac;
        float fillTop = tubeRect.bottom - fillH;

        int targetLineScore = (bestScore > 0) ? bestScore : (int) (maxScore * 0.8f);
        boolean beatRecord = currentScore > targetLineScore;

        if (fillH > 1f) {
            fillRect.set(tubeRect.left, fillTop, tubeRect.right, tubeRect.bottom);
            liquidPaint.setShader(new LinearGradient(
                    0, fillTop, 0, tubeRect.bottom,
                    beatRecord
                            ? new int[]{0xFFFDE047, 0xFFF59E0B, 0xFFD97706}
                            : new int[]{0xFF34D399, 0xFF10B981, 0xFF059669},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRect(fillRect, liquidPaint);

            // Glowing liquid surface meniscus
            canvas.drawRect(tubeRect.left, fillTop, tubeRect.right, Math.min(tubeRect.bottom, fillTop + 3f * d), crestPaint);
        }

        // Subtle measurement ticks (25%, 50%, 75%)
        tubeBorderPaint.setStrokeWidth(1f * d);
        tubeBorderPaint.setColor(0x33FFFFFF);
        for (int i = 1; i <= 3; i++) {
            float ty = tubeRect.bottom - (tubeRect.height() * (i * 0.25f));
            canvas.drawLine(tubeRect.left + 5 * d, ty, tubeRect.right - 5 * d, ty, tubeBorderPaint);
        }

        canvas.restoreToCount(save);

        // Outer glass cylinder border
        tubeBorderPaint.setStrokeWidth(1.6f * d);
        tubeBorderPaint.setColor(beatRecord ? 0xFFFBBF24 : 0x7794A3B8);
        canvas.drawRoundRect(tubeRect, radius, radius, tubeBorderPaint);

        // Personal Best / Target Golden Marker Line (★ Best)
        if (targetLineScore > 0 && targetLineScore <= maxScore) {
            float bestFrac = targetLineScore / (float) maxScore;
            float bestY = tubeRect.bottom - tubeRect.height() * bestFrac;
            canvas.drawLine(tubeRect.left - 4f * d, bestY, tubeRect.right + 4f * d, bestY, bestLinePaint);
            textPaint.setTextSize(9.5f * d);
            textPaint.setColor(0xFFFDE047);
            canvas.drawText("★" + targetLineScore, cx, bestY - 4f * d, textPaint);
        }

        // Live Score Pill at the liquid crest (or bottom if 0)
        float scoreY = Math.max(tubeRect.top + 14f * d, Math.min(tubeRect.bottom - 6f * d, fillTop + 12f * d));
        textPaint.setTextSize(11.5f * d);
        textPaint.setColor(Color.WHITE);
        textPaint.setShadowLayer(3f * d, 0, 1f, 0xEE000000);
        canvas.drawText(String.valueOf(currentScore), cx, scoreY, textPaint);
        textPaint.setShadowLayer(0, 0, 0, 0);
    }
}

