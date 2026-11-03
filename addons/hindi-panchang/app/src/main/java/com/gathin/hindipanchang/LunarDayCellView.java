package com.gathin.hindipanchang;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.time.LocalDate;

/**
 * Realistic 3D Pearlescent Lunar Moon-Orb Cell:
 * - Spherical 3D earthshine base disc
 * - True astronomical illuminated crescent/gibbous/full moon with 3D spherical RadialGradient
 * - Subtle Lunar Maria (Sea of Tranquility / Serenity) surface texture inside the lit hemisphere
 * - Luminous sunlit limb arc + atmospheric Purnima corona or Amavasya eclipse ring
 * - High-contrast date number with deep shadow layer so it stays legible over any phase.
 */
final class LunarDayCellView extends View {

    private final Paint coronaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkSpherePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint litSpherePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mariaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunlitLimbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path phasePath = new Path();
    private final RectF circleBounds = new RectF();

    private LocalDate date;
    private PanchangEngine.DayPanchang panchang;
    private boolean isToday;
    private boolean isSelected;

    LunarDayCellView(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;

        coronaPaint.setStyle(Paint.Style.FILL);
        darkSpherePaint.setStyle(Paint.Style.FILL);
        litSpherePaint.setStyle(Paint.Style.FILL);

        mariaPaint.setStyle(Paint.Style.FILL);

        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(1.0f * d);

        sunlitLimbPaint.setStyle(Paint.Style.STROKE);
        sunlitLimbPaint.setStrokeWidth(1.6f * d);
        sunlitLimbPaint.setStrokeCap(Paint.Cap.ROUND);

        focusRingPaint.setStyle(Paint.Style.STROKE);
        focusRingPaint.setStrokeWidth(2.4f * d);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(15.5f * d);
        textPaint.setShadowLayer(4.5f * d, 0f, 1.2f * d, 0xF0000000);

        dotPaint.setStyle(Paint.Style.FILL);
    }

    void bind(LocalDate d, PanchangEngine.DayPanchang dp, boolean today, boolean selected) {
        this.date = d;
        this.panchang = dp;
        this.isToday = today;
        this.isSelected = selected;
        boolean active = (d != null && dp != null);
        setFocusable(active);
        setFocusableInTouchMode(active);
        setClickable(active);
        invalidate();
    }

    LocalDate getDate() {
        return date;
    }

    PanchangEngine.DayPanchang getPanchang() {
        return panchang;
    }

    void setSelectedDay(boolean selected) {
        if (this.isSelected != selected) {
            this.isSelected = selected;
            invalidate();
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        animate().scaleX(gainFocus ? 1.18f : 1.0f)
                .scaleY(gainFocus ? 1.18f : 1.0f)
                .setDuration(140)
                .start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (date == null || panchang == null) return;

        float d = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f - 6.5f * d;
        circleBounds.set(cx - r, cy - r, cx + r, cy + r);

        boolean focused = isFocused();
        boolean isPurnima = (panchang.tithiIndex == 14);
        boolean isAmavasya = (panchang.tithiIndex == 29);
        boolean hasFest = !panchang.festivalsHi.isEmpty();
        float illum = panchang.moonIlluminationPercent / 100f;
        boolean waxing = panchang.moonPhaseAngle <= 180.0;

        // 1. Atmospheric Lunar Halo / Corona
        if (focused || isPurnima || illum > 0.20f) {
            float glowR = r + (focused ? 7.5f : (isPurnima ? 8.0f : (2.5f + illum * 4.5f))) * d;
            int coreCol = isPurnima ? 0x58FDE68A : (focused ? 0x55F59E0B : (((int) (illum * 52) << 24) | 0xE2E8F0));
            coronaPaint.setShader(new RadialGradient(
                    cx, cy, glowR,
                    new int[]{coreCol, 0x00000000},
                    new float[]{0.45f, 1.0f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy, glowR, coronaPaint);
        } else if (isAmavasya) {
            // Total Eclipse Corona on Amavasya
            float glowR = r + 5.5f * d;
            coronaPaint.setShader(new RadialGradient(
                    cx, cy, glowR,
                    new int[]{0x44818CF8, 0x00000000},
                    new float[]{0.55f, 1.0f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(cx, cy, glowR, coronaPaint);
        }

        // 2. 3D Earthshine Dark Lunar Sphere
        darkSpherePaint.setShader(new RadialGradient(
                cx - r * 0.25f, cy - r * 0.25f, r * 1.35f,
                isAmavasya ? new int[]{0xFF141622, 0xFF07080E} : new int[]{0xFF212636, 0xFF0E121B},
                null,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, r, darkSpherePaint);

        // 3. 3D Pearlescent Illuminated Moon Phase + Lunar Maria Texture
        if (!isAmavasya && illum > 0.02f) {
            float cosPhase = (float) Math.cos(Math.toRadians(panchang.moonPhaseAngle));
            phasePath.reset();
            int steps = 32;
            for (int i = 0; i <= steps; i++) {
                double theta = -Math.PI / 2.0 + (Math.PI * i / steps);
                float y = cy + (float) Math.sin(theta) * r;
                float xlimb = (float) Math.cos(theta) * r;
                float x = cx + (waxing ? xlimb : -xlimb);
                if (i == 0) phasePath.moveTo(x, y);
                else phasePath.lineTo(x, y);
            }
            for (int i = steps; i >= 0; i--) {
                double theta = -Math.PI / 2.0 + (Math.PI * i / steps);
                float y = cy + (float) Math.sin(theta) * r;
                float xterm = (float) Math.cos(theta) * r * cosPhase;
                float x = cx + (waxing ? xterm : -xterm);
                phasePath.lineTo(x, y);
            }
            phasePath.close();

            // 3D Spherical Moonlight Shader (brightest toward sunlit limb, softer toward terminator)
            float lightCx = cx + (waxing ? r * 0.35f : -r * 0.35f);
            float lightCy = cy - r * 0.18f;
            int[] litCols = isPurnima
                    ? new int[]{0xB8FFFBEB, 0x92FDE68A, 0x64D97706}
                    : new int[]{0x9EFFFDF7, 0x76E2E8F0, 0x4894A3B8};
            litSpherePaint.setShader(new RadialGradient(
                    lightCx, lightCy, r * 1.35f,
                    litCols,
                    new float[]{0f, 0.62f, 1f},
                    Shader.TileMode.CLAMP
            ));

            canvas.save();
            canvas.clipPath(phasePath);
            canvas.drawCircle(cx, cy, r, litSpherePaint);

            // Subtle Lunar Maria (Crater Seas) on the lit surface for authentic moon realism
            mariaPaint.setColor(isPurnima ? 0x2692400E : 0x28334155);
            canvas.drawCircle(cx - r * 0.22f, cy - r * 0.18f, r * 0.28f, mariaPaint);
            canvas.drawCircle(cx + r * 0.24f, cy - r * 0.10f, r * 0.22f, mariaPaint);
            canvas.drawCircle(cx + r * 0.12f, cy + r * 0.26f, r * 0.18f, mariaPaint);
            canvas.drawCircle(cx - r * 0.30f, cy + r * 0.20f, r * 0.15f, mariaPaint);
            canvas.restore();

            // Luminous Sunlit Outer Limb Rim
            sunlitLimbPaint.setColor(isPurnima ? 0xEEFEF08A : 0xCCF8FAFC);
            if (isPurnima) {
                canvas.drawCircle(cx, cy, r, sunlitLimbPaint);
            } else {
                canvas.drawArc(circleBounds, waxing ? -86f : 94f, 172f, false, sunlitLimbPaint);
            }
        }

        // 4. Subtle Unlit Sphere Rim / Focus Ring
        if (focused) {
            focusRingPaint.setColor(0xFFF59E0B);
            canvas.drawCircle(cx, cy, r + 1.2f * d, focusRingPaint);
        } else if (isToday) {
            focusRingPaint.setColor(0xFFEA580C);
            canvas.drawCircle(cx, cy, r + 0.8f * d, focusRingPaint);
        } else if (isAmavasya) {
            rimPaint.setColor(0xBB818CF8);
            canvas.drawCircle(cx, cy, r, rimPaint);
        } else {
            rimPaint.setColor(0x3894A3B8);
            canvas.drawCircle(cx, cy, r, rimPaint);
        }

        // 5. High-Contrast Date Number centered in the Moon Orb
        textPaint.setColor(focused ? 0xFFFFFBEB : Color.WHITE);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f - (hasFest ? 1.5f * d : 0f);
        canvas.drawText(Integer.toString(date.getDayOfMonth()), cx, textY, textPaint);

        // 6. Subtle Festival / Vrat Indicator Dot
        if (hasFest) {
            dotPaint.setColor(panchang.isMajorFestival ? 0xFFF97316 : 0xFFFBBF24);
            canvas.drawCircle(cx, cy + r - 4.6f * d, 2.3f * d, dotPaint);
        }
    }
}
