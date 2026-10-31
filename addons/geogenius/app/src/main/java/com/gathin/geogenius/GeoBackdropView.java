package com.gathin.geogenius;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Calm, minimalist deep-space indigo backdrop with subtle orbital latitude/longitude
 * rings so the 55% Center Stage pops without visual clutter.
 */
final class GeoBackdropView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    GeoBackdropView(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;
        orbitPaint.setStyle(Paint.Style.STROKE);
        orbitPaint.setStrokeWidth(1f * d);
        orbitPaint.setColor(0x1438BDF8);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        bgPaint.setShader(new LinearGradient(
                0, 0, 0, h,
                new int[]{0xFF060B19, 0xFF0B1528, 0xFF070D1C},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, w, h, bgPaint);

        // Soft emerald-cyan ambient glow behind the 55% center stage
        glowPaint.setShader(new RadialGradient(
                w * 0.5f, h * 0.5f, Math.max(w, h) * 0.44f,
                new int[]{0x2410B981, 0x100284C7, 0x00000000},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, w, h, glowPaint);

        // Subtle globe orbital rings in the outer breathing space
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        canvas.drawCircle(cx, cy, Math.min(w, h) * 0.42f, orbitPaint);
        canvas.drawCircle(cx, cy, Math.min(w, h) * 0.56f, orbitPaint);
    }
}

