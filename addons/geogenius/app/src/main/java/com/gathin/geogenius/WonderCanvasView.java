package com.gathin.geogenius;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Stylized vector postcard renderer for Mode 4 (Landmark Explorer / World Wonders).
 */
final class WonderCanvasView extends View {

    private int artType = GeoData.ART_TAJ;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    WonderCanvasView(Context context) {
        super(context);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    void setArtType(int type) {
        this.artType = type;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        rect.set(2 * d, 2 * d, w - 2 * d, h - 2 * d);
        float r = 14f * d;

        int save = canvas.save();
        path.reset();
        path.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.clipPath(path);

        // Sky backdrop gradient
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
                0, rect.top, 0, rect.bottom,
                new int[]{0xFF0F2042, 0xFF1E3A5F, 0xFF334155},
                new float[]{0f, 0.65f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(rect, paint);
        paint.setShader(null);

        float cx = rect.centerX();
        float baseY = rect.bottom - 14f * d;

        // Ground line
        paint.setColor(0xFF0B1528);
        canvas.drawRect(rect.left, baseY, rect.right, rect.bottom, paint);

        // Warm sun/moon glow behind landmark
        paint.setColor(0x33FBBF24);
        canvas.drawCircle(cx + 28 * d, rect.top + 32 * d, 20 * d, paint);
        paint.setColor(0xFFFDE047);
        canvas.drawCircle(cx + 28 * d, rect.top + 32 * d, 11 * d, paint);

        switch (artType) {
            case GeoData.ART_TAJ: {
                paint.setColor(0xFFF8FAFC);
                // Main base & dome
                canvas.drawRect(cx - 38 * d, baseY - 32 * d, cx + 38 * d, baseY, paint);
                canvas.drawCircle(cx, baseY - 40 * d, 20 * d, paint);
                // 2 Minarets
                canvas.drawRect(cx - 56 * d, baseY - 52 * d, cx - 50 * d, baseY, paint);
                canvas.drawRect(cx + 50 * d, baseY - 52 * d, cx + 56 * d, baseY, paint);
                // Archway
                paint.setColor(0xFF1E293B);
                canvas.drawRect(cx - 10 * d, baseY - 22 * d, cx + 10 * d, baseY, paint);
                break;
            }
            case GeoData.ART_EIFFEL: {
                strokePaint.setColor(0xFFF59E0B);
                strokePaint.setStrokeWidth(3.2f * d);
                canvas.drawLine(cx - 26 * d, baseY, cx, rect.top + 14 * d, strokePaint);
                canvas.drawLine(cx + 26 * d, baseY, cx, rect.top + 14 * d, strokePaint);
                canvas.drawLine(cx - 18 * d, baseY - 24 * d, cx + 18 * d, baseY - 24 * d, strokePaint);
                canvas.drawLine(cx - 10 * d, baseY - 46 * d, cx + 10 * d, baseY - 46 * d, strokePaint);
                break;
            }
            case GeoData.ART_PYRAMID: {
                path.reset();
                path.moveTo(cx - 48 * d, baseY);
                path.lineTo(cx - 8 * d, baseY - 54 * d);
                path.lineTo(cx + 32 * d, baseY);
                path.close();
                paint.setColor(0xFFF59E0B);
                canvas.drawPath(path, paint);

                path.reset();
                path.moveTo(cx, baseY);
                path.lineTo(cx + 30 * d, baseY - 40 * d);
                path.lineTo(cx + 60 * d, baseY);
                path.close();
                paint.setColor(0xFFD97706);
                canvas.drawPath(path, paint);
                break;
            }
            case GeoData.ART_LIBERTY: {
                paint.setColor(0xFF34D399);
                canvas.drawRect(cx - 16 * d, baseY - 20 * d, cx + 16 * d, baseY, paint);
                canvas.drawRect(cx - 10 * d, baseY - 52 * d, cx + 10 * d, baseY - 20 * d, paint);
                canvas.drawCircle(cx, baseY - 58 * d, 7 * d, paint);
                // Raised arm & golden torch flame
                strokePaint.setColor(0xFF34D399);
                strokePaint.setStrokeWidth(3.5f * d);
                canvas.drawLine(cx + 8 * d, baseY - 46 * d, cx + 16 * d, baseY - 70 * d, strokePaint);
                paint.setColor(0xFFFBBF24);
                canvas.drawCircle(cx + 16 * d, baseY - 74 * d, 5 * d, paint);
                break;
            }
            case GeoData.ART_BIGBEN: {
                paint.setColor(0xFFD97706);
                canvas.drawRect(cx - 14 * d, baseY - 62 * d, cx + 14 * d, baseY, paint);
                path.reset();
                path.moveTo(cx - 16 * d, baseY - 62 * d);
                path.lineTo(cx, baseY - 82 * d);
                path.lineTo(cx + 16 * d, baseY - 62 * d);
                path.close();
                canvas.drawPath(path, paint);
                // White clock face
                paint.setColor(Color.WHITE);
                canvas.drawCircle(cx, baseY - 46 * d, 8 * d, paint);
                break;
            }
            case GeoData.ART_FUJI: {
                path.reset();
                path.moveTo(cx - 60 * d, baseY);
                path.lineTo(cx - 12 * d, baseY - 56 * d);
                path.lineTo(cx + 12 * d, baseY - 56 * d);
                path.lineTo(cx + 60 * d, baseY);
                path.close();
                paint.setColor(0xFF3B82F6);
                canvas.drawPath(path, paint);
                // Snow cap
                path.reset();
                path.moveTo(cx - 24 * d, baseY - 36 * d);
                path.lineTo(cx - 12 * d, baseY - 56 * d);
                path.lineTo(cx + 12 * d, baseY - 56 * d);
                path.lineTo(cx + 24 * d, baseY - 36 * d);
                path.close();
                paint.setColor(Color.WHITE);
                canvas.drawPath(path, paint);
                break;
            }
            default: {
                // Classical columns / arches / temple monument
                paint.setColor(0xFFE2E8F0);
                canvas.drawRect(cx - 42 * d, baseY - 10 * d, cx + 42 * d, baseY, paint);
                for (int i = -3; i <= 3; i += 2) {
                    canvas.drawRect(cx + i * 11 * d - 3 * d, baseY - 44 * d, cx + i * 11 * d + 3 * d, baseY - 10 * d, paint);
                }
                path.reset();
                path.moveTo(cx - 46 * d, baseY - 44 * d);
                path.lineTo(cx, baseY - 64 * d);
                path.lineTo(cx + 46 * d, baseY - 44 * d);
                path.close();
                canvas.drawPath(path, paint);
                break;
            }
        }

        canvas.restoreToCount(save);

        strokePaint.setStrokeWidth(1.5f * d);
        strokePaint.setColor(0x55FFFFFF);
        canvas.drawRoundRect(rect, r, r, strokePaint);
    }
}

