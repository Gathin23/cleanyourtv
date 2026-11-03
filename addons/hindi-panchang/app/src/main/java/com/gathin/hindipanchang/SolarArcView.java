package com.gathin.hindipanchang;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;

/**
 * Interactive Solar Arc & Time Window Canvas matching the Interactive Layout Blueprint:
 * - Renders the parabolic dashed Sun trajectory from Sunrise to Sunset
 * - Highlights Rahu Kaal (Red) and Abhijit Muhurat (Green) along the horizon timeline
 * - Marks Moonrise (Purple)
 * - Draws the glowing Sun Orb at the current (or simulated) hour with a drop-line time badge
 * - Supports D-pad Left/Right scrubbing for Hour Simulation (समय सिमुलेशन).
 */
final class SolarArcView extends View {
    interface OnTimeSimListener {
        void onTimeSimulated(int minutesFromMidnight, boolean isLive);
    }

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint horizonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rahuPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint abhijitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunOrbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dropLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path arcPath = new Path();
    private final RectF rect = new RectF();

    private PanchangEngine.DayPanchang data;
    private int simMinutes = 810; // 13:30 default
    private boolean isLiveTime = true;
    private int langMode = LocationStore.LANG_BILINGUAL;
    private OnTimeSimListener listener;

    SolarArcView(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;

        setFocusable(true);
        setFocusableInTouchMode(true);

        gridPaint.setColor(0x143B82F6);
        gridPaint.setStrokeWidth(1f * d);

        horizonPaint.setColor(0xFF334155);
        horizonPaint.setStrokeWidth(2f * d);

        arcPaint.setColor(0xFFD97706);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(2.2f * d);
        arcPaint.setPathEffect(new DashPathEffect(new float[]{6f * d, 5f * d}, 0));

        rahuPaint.setColor(0x66EF4444);
        rahuPaint.setStyle(Paint.Style.FILL);

        abhijitPaint.setColor(0x5510B981);
        abhijitPaint.setStyle(Paint.Style.FILL);

        sunOrbPaint.setColor(0xFFF59E0B);
        sunOrbPaint.setStyle(Paint.Style.FILL);

        sunHaloPaint.setColor(0x88F59E0B);
        sunHaloPaint.setStyle(Paint.Style.STROKE);
        sunHaloPaint.setStrokeWidth(1.5f * d);
        sunHaloPaint.setPathEffect(new DashPathEffect(new float[]{4f * d, 4f * d}, 0));

        dropLinePaint.setColor(0x99F59E0B);
        dropLinePaint.setStyle(Paint.Style.STROKE);
        dropLinePaint.setStrokeWidth(1.5f * d);
        dropLinePaint.setPathEffect(new DashPathEffect(new float[]{3f * d, 3f * d}, 0));

        badgePaint.setColor(0xFFF59E0B);
        badgePaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(0xFFE2E8F0);
        textPaint.setTextSize(12f * d);

        focusBorderPaint.setColor(0xFFF59E0B);
        focusBorderPaint.setStyle(Paint.Style.STROKE);
        focusBorderPaint.setStrokeWidth(2f * d);
    }

    void setOnTimeSimListener(OnTimeSimListener l) {
        this.listener = l;
    }

    void bind(PanchangEngine.DayPanchang dp, int minutesFromMidnight, boolean live, int lang) {
        this.data = dp;
        this.simMinutes = minutesFromMidnight;
        this.isLiveTime = live;
        this.langMode = lang;
        invalidate();
    }

    void stepSimMinutes(int deltaMinutes) {
        isLiveTime = false;
        simMinutes = ((simMinutes + deltaMinutes) % 1440 + 1440) % 1440;
        invalidate();
        if (listener != null) listener.onTimeSimulated(simMinutes, false);
    }

    void resetToLive(int currentMinutes) {
        isLiveTime = true;
        simMinutes = currentMinutes;
        invalidate();
        if (listener != null) listener.onTimeSimulated(simMinutes, true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            stepSimMinutes(-15);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            stepSimMinutes(15);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(
                    data != null ? data.place.zoneId() : java.time.ZoneId.systemDefault());
            resetToLive(now.getHour() * 60 + now.getMinute());
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null) return;
        float d = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Subtle background blueprint grid
        int gridStep = Math.round(32 * d);
        for (int x = gridStep; x < w; x += gridStep) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (int y = gridStep; y < h; y += gridStep) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        if (isFocused()) {
            rect.set(2 * d, 2 * d, w - 2 * d, h - 2 * d);
            canvas.drawRoundRect(rect, 10 * d, 10 * d, focusBorderPaint);
        }

        // Top Legend Row
        float legY = 20 * d;
        float legX = 16 * d;
        textPaint.setTextSize(11.5f * d);
        textPaint.setTextAlign(Paint.Align.LEFT);

        // Dot 1: Sun Path
        sunOrbPaint.setColor(0xFFF59E0B);
        canvas.drawCircle(legX + 5 * d, legY - 4 * d, 5 * d, sunOrbPaint);
        textPaint.setColor(0xFFE2E8F0);
        String leg1 = langMode == LocationStore.LANG_ENGLISH ? "Sun Path / Shubh Kaal" : "सूर्य पथ / शुभ काल";
        canvas.drawText(leg1, legX + 15 * d, legY, textPaint);
        legX += textPaint.measureText(leg1) + 34 * d;

        // Dot 2: Rahu Kaal
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(0xFFEF4444);
        canvas.drawCircle(legX + 5 * d, legY - 4 * d, 5 * d, dotPaint);
        String leg2 = langMode == LocationStore.LANG_ENGLISH ? "Rahu Kaal (Avoid)" : "राहुकाल (वर्जित समय)";
        canvas.drawText(leg2, legX + 15 * d, legY, textPaint);
        legX += textPaint.measureText(leg2) + 34 * d;

        // Dot 3: Abhijit
        dotPaint.setColor(0xFF10B981);
        canvas.drawCircle(legX + 5 * d, legY - 4 * d, 5 * d, dotPaint);
        String leg3 = langMode == LocationStore.LANG_ENGLISH ? "Abhijit Muhurat" : "अभिजीत मुहूर्त";
        canvas.drawText(leg3, legX + 15 * d, legY, textPaint);
        legX += textPaint.measureText(leg3) + 34 * d;

        // Dot 4: Moonrise
        dotPaint.setColor(0xFF818CF8);
        canvas.drawCircle(legX + 5 * d, legY - 4 * d, 5 * d, dotPaint);
        String leg4 = (langMode == LocationStore.LANG_ENGLISH ? "Moonrise: " : "चन्द्रोदय: ") + data.moonriseFormatted;
        canvas.drawText(leg4, legX + 15 * d, legY, textPaint);

        // Right-side simulation hint
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setColor(isFocused() ? 0xFFFBBF24 : 0xFF94A3B8);
        String simHint = isFocused()
                ? "◀ ▶ समय सिमुलेशन (Scrub Hour) · OK = Live"
                : (isLiveTime ? "● Live Time" : "● सिमुलेशन समय (Simulated)");
        canvas.drawText(simHint, w - 16 * d, legY, textPaint);

        // Horizon geometry
        float leftX = 48 * d;
        float rightX = w - 48 * d;
        float spanX = rightX - leftX;
        float horizonY = h - 46 * d;
        float arcPeakY = 36 * d;

        canvas.drawLine(leftX - 20 * d, horizonY, rightX + 20 * d, horizonY, horizonPaint);

        int sr = data.sunriseMin;
        int ss = data.sunsetMin;
        int daySpan = Math.max(1, ss - sr);

        // Rahu Kaal Red Window on Horizon
        float rahuX1 = leftX + ((data.rahuStartMin - sr) / (float) daySpan) * spanX;
        float rahuX2 = leftX + ((data.rahuEndMin - sr) / (float) daySpan) * spanX;
        rect.set(rahuX1, horizonY - 10 * d, rahuX2, horizonY + 10 * d);
        canvas.drawRoundRect(rect, 5 * d, 5 * d, rahuPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFF87171);
        textPaint.setTextSize(11f * d);
        canvas.drawText(langMode == LocationStore.LANG_ENGLISH ? "Rahu Kaal" : "राहुकाल",
                (rahuX1 + rahuX2) / 2f, horizonY + 24 * d, textPaint);

        // Abhijit Muhurat Green Window on Horizon
        float abhX1 = leftX + ((data.abhijitStartMin - sr) / (float) daySpan) * spanX;
        float abhX2 = leftX + ((data.abhijitEndMin - sr) / (float) daySpan) * spanX;
        rect.set(abhX1, horizonY - 8 * d, abhX2, horizonY + 8 * d);
        canvas.drawRoundRect(rect, 5 * d, 5 * d, abhijitPaint);
        textPaint.setColor(0xFF34D399);
        canvas.drawText(langMode == LocationStore.LANG_ENGLISH ? "Abhijit" : "अभिजीत",
                (abhX1 + abhX2) / 2f, horizonY - 13 * d, textPaint);

        // Parabolic Solar Arc from Sunrise (leftX, horizonY) to Sunset (rightX, horizonY)
        arcPath.reset();
        int steps = 50;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = leftX + t * spanX;
            float parabola = 4f * t * (1f - t); // 0 at ends, 1 at midpoint
            float y = horizonY - parabola * (horizonY - arcPeakY);
            if (i == 0) arcPath.moveTo(x, y);
            else arcPath.lineTo(x, y);
        }
        canvas.drawPath(arcPath, arcPaint);

        // Sunrise & Sunset Labels at Ends of Arc
        textPaint.setTextSize(12f * d);
        textPaint.setColor(0xFFCBD5E1);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("🌅 " + PanchangEngine.formatMinutes24h(sr), leftX, horizonY + 24 * d, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("🌇 " + PanchangEngine.formatMinutes24h(ss), rightX, horizonY + 24 * d, textPaint);

        // Sun Orb Position along the Arc
        float tSun = (simMinutes - sr) / (float) daySpan;
        float clampedT = Math.max(0f, Math.min(1f, tSun));
        float sunX = leftX + clampedT * spanX;
        float parabola = 4f * clampedT * (1f - clampedT);
        float sunY = horizonY - parabola * (horizonY - arcPeakY);

        boolean isDaytime = simMinutes >= sr && simMinutes <= ss;
        boolean inRahu = data.isInRahuKaal(simMinutes);
        sunOrbPaint.setColor(inRahu ? 0xFFEF4444 : (isDaytime ? 0xFFF59E0B : 0xFF818CF8));

        // Vertical drop line & time pill
        canvas.drawLine(sunX, sunY + 14 * d, sunX, horizonY + 8 * d, dropLinePaint);
        canvas.drawCircle(sunX, sunY, 17 * d, sunHaloPaint);
        canvas.drawCircle(sunX, sunY, 11 * d, sunOrbPaint);

        // Time badge below horizon
        String badgeTime = PanchangEngine.formatMinutes24h(simMinutes);
        float bw = 54 * d;
        float bh = 20 * d;
        float bx = Math.max(leftX + 70 * d, Math.min(rightX - 70 * d, sunX));
        rect.set(bx - bw / 2f, horizonY + 10 * d, bx + bw / 2f, horizonY + 10 * d + bh);
        badgePaint.setColor(inRahu ? 0xFFEF4444 : 0xFFF59E0B);
        canvas.drawRoundRect(rect, 6 * d, 6 * d, badgePaint);

        textPaint.setColor(0xFF0F172A);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        canvas.drawText(badgeTime, bx, horizonY + 24 * d, textPaint);
        textPaint.setFakeBoldText(false);
    }
}

