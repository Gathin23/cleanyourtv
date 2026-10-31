package com.gathin.geogenius;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

/**
 * Circular Glass Icon Orb for the Home Screen 4x2 Mode Grid:
 * - Draws a glowing circular glass orb with custom vector iconography
 * - Prints a short 1-2 word label below the circle
 * - Shows a compact ★ Star + Best Score badge underneath
 */
final class GeoModeOrbView extends View {

    private final int mode;
    private final String title;
    private final int accentColor;
    private int stars = 0;
    private int bestScore = 0;

    private final Paint orbFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    GeoModeOrbView(Context context, int mode, String title, int accentColor) {
        super(context);
        this.mode = mode;
        this.title = title;
        this.accentColor = accentColor;
        float d = context.getResources().getDisplayMetrics().density;

        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);

        orbFill.setStyle(Paint.Style.FILL);
        orbRing.setStyle(Paint.Style.STROKE);
        glowPaint.setStyle(Paint.Style.FILL);

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(2.2f * d);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);

        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTextSize(12.5f * d);

        subPaint.setTypeface(Typeface.DEFAULT_BOLD);
        subPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTextSize(10f * d);
    }

    void updateProgress(int stars, int bestScore) {
        this.stars = stars;
        this.bestScore = bestScore;
        invalidate();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        animate().scaleX(gainFocus ? 1.09f : 1.0f)
                .scaleY(gainFocus ? 1.09f : 1.0f)
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
        float cx = w * 0.5f;
        float cy = 31f * d;
        float r = 25f * d;

        if (focused) {
            glowPaint.setColor((accentColor & 0x00FFFFFF) | 0x38000000);
            canvas.drawCircle(cx, cy, r + 4f * d, glowPaint);
            orbFill.setColor(0xDD1E293B);
            orbRing.setStrokeWidth(2.4f * d);
            orbRing.setColor(0xFFFBBF24);
            iconPaint.setColor(0xFFFDE047);
            titlePaint.setColor(0xFFFDE047);
        } else {
            orbFill.setColor(0xAA0F172A);
            orbRing.setStrokeWidth(1.5f * d);
            orbRing.setColor(accentColor);
            iconPaint.setColor(accentColor);
            titlePaint.setColor(0xFFF1F5F9);
        }

        canvas.drawCircle(cx, cy, r, orbFill);
        canvas.drawCircle(cx, cy, r, orbRing);

        drawModeVectorIcon(canvas, cx, cy, d);

        titlePaint.setShadowLayer(3f * d, 0, 1.2f, 0xEE000000);
        canvas.drawText(title, cx, cy + r + 14.5f * d, titlePaint);

        if (mode == GeoStore.MODE_ATLAS && bestScore <= 0) {
            subPaint.setColor(0xFF38BDF8);
            canvas.drawText("7 Decks", cx, cy + r + 28f * d, subPaint);
        } else {
            String starStr = (stars >= 1 ? "★" : "☆") + (stars >= 2 ? "★" : "☆") + (stars >= 3 ? "★" : "☆");
            String badge = bestScore > 0 ? (starStr + " · " + bestScore) : starStr;
            subPaint.setColor(bestScore > 0 ? 0xFFFBBF24 : 0xFF64748B);
            canvas.drawText(badge, cx, cy + r + 28f * d, subPaint);
        }
    }

    private void drawModeVectorIcon(Canvas canvas, float cx, float cy, float d) {
        switch (mode) {
            case GeoStore.MODE_FLAG_FINDER: {
                canvas.drawLine(cx - 8 * d, cy - 10 * d, cx - 8 * d, cy + 11 * d, iconPaint);
                path.reset();
                path.moveTo(cx - 8 * d, cy - 9 * d);
                path.lineTo(cx + 9 * d, cy - 9 * d);
                path.lineTo(cx + 6 * d, cy - 4 * d);
                path.lineTo(cx + 9 * d, cy + 1 * d);
                path.lineTo(cx - 8 * d, cy + 1 * d);
                path.close();
                canvas.drawPath(path, iconPaint);
                break;
            }
            case GeoStore.MODE_FLAG_MATCHER: {
                canvas.drawCircle(cx, cy, 9.5f * d, iconPaint);
                canvas.drawLine(cx - 13 * d, cy, cx - 7 * d, cy, iconPaint);
                canvas.drawLine(cx + 7 * d, cy, cx + 13 * d, cy, iconPaint);
                canvas.drawLine(cx, cy - 13 * d, cx, cy - 7 * d, iconPaint);
                canvas.drawLine(cx, cy + 7 * d, cx, cy + 13 * d, iconPaint);
                canvas.drawCircle(cx, cy, 2.3f * d, iconPaint);
                break;
            }
            case GeoStore.MODE_WORD_BUILDER: {
                canvas.drawRoundRect(cx - 11 * d, cy - 8 * d, cx - 1 * d, cy + 2 * d, 2 * d, 2 * d, iconPaint);
                canvas.drawRoundRect(cx + 1 * d, cy - 2 * d, cx + 11 * d, cy + 8 * d, 2 * d, 2 * d, iconPaint);
                break;
            }
            case GeoStore.MODE_CAPITALS: {
                canvas.drawLine(cx - 10 * d, cy + 9 * d, cx + 10 * d, cy + 9 * d, iconPaint);
                canvas.drawLine(cx - 7 * d, cy + 9 * d, cx - 7 * d, cy, iconPaint);
                canvas.drawLine(cx, cy + 9 * d, cx, cy, iconPaint);
                canvas.drawLine(cx + 7 * d, cy + 9 * d, cx + 7 * d, cy, iconPaint);
                path.reset();
                path.moveTo(cx - 10 * d, cy);
                path.quadTo(cx, cy - 13 * d, cx + 10 * d, cy);
                path.close();
                canvas.drawPath(path, iconPaint);
                break;
            }
            case GeoStore.MODE_WONDERS: {
                path.reset();
                path.moveTo(cx - 11 * d, cy + 8 * d);
                path.lineTo(cx - 1 * d, cy - 8 * d);
                path.lineTo(cx + 9 * d, cy + 8 * d);
                path.close();
                canvas.drawPath(path, iconPaint);
                canvas.drawCircle(cx + 7 * d, cy - 6 * d, 2.5f * d, iconPaint);
                break;
            }
            case GeoStore.MODE_SNAP_MATCH: {
                canvas.drawCircle(cx - 8 * d, cy - 6 * d, 3.2f * d, iconPaint);
                canvas.drawCircle(cx + 8 * d, cy + 6 * d, 3.2f * d, iconPaint);
                canvas.drawLine(cx - 5 * d, cy - 4 * d, cx + 5 * d, cy + 4 * d, iconPaint);
                break;
            }
            case GeoStore.MODE_ODD_ONE_OUT: {
                // Magnifying glass with '✕' inside (Spot the Lie / Odd One Out)
                float mr = 7.5f * d;
                float mcx = cx - 2f * d;
                float mcy = cy - 2f * d;
                canvas.drawCircle(mcx, mcy, mr, iconPaint);
                canvas.drawLine(mcx + 5.2f * d, mcy + 5.2f * d, cx + 10f * d, cy + 10f * d, iconPaint);
                canvas.drawLine(mcx - 2.6f * d, mcy - 2.6f * d, mcx + 2.6f * d, mcy + 2.6f * d, iconPaint);
                canvas.drawLine(mcx + 2.6f * d, mcy - 2.6f * d, mcx - 2.6f * d, mcy + 2.6f * d, iconPaint);
                break;
            }
            case GeoStore.MODE_ATLAS:
            default: {
                // Globe with meridian & equator lines
                float gr = 10f * d;
                canvas.drawCircle(cx, cy, gr, iconPaint);
                canvas.drawLine(cx - gr, cy, cx + gr, cy, iconPaint);
                canvas.drawOval(cx - gr * 0.5f, cy - gr, cx + gr * 0.5f, cy + gr, iconPaint);
                break;
            }
        }
    }
}
