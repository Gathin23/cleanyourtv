package com.gathin.addons.youtubetimer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

public class UsageGraphView extends View {

    private final int[] slotSeconds = new int[48];
    private int selectedSlot = -1;

    private Paint bgPaint;
    private Paint borderPaint;
    private Paint gridPaint;
    private Paint trackPaint;
    private Paint barActivePaint;
    private Paint barSelectedPaint;
    private Paint cursorPaint;
    private Paint textHeaderLeft;
    private Paint textHeaderRight;
    private Paint textAxis;

    private final RectF bgRect = new RectF();
    private final RectF barRect = new RectF();
    private final RectF cursorRect = new RectF();

    public UsageGraphView(Context context) {
        super(context);
        init();
    }

    public UsageGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UsageGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#161B22"));
        bgPaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(1.5f));
        borderPaint.setColor(Color.parseColor("#30363D"));

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dpToPx(1f));
        gridPaint.setColor(Color.parseColor("#21262D"));
        gridPaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(4), dpToPx(4)}, 0));

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(Color.parseColor("#21262D"));

        barActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barActivePaint.setStyle(Paint.Style.FILL);
        barActivePaint.setColor(Color.parseColor("#E52D27")); // YouTube Red

        barSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barSelectedPaint.setStyle(Paint.Style.FILL);
        barSelectedPaint.setColor(Color.parseColor("#FFFFFF"));

        cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(dpToPx(2f));
        cursorPaint.setColor(Color.parseColor("#FFFFFF"));

        textHeaderLeft = new Paint(Paint.ANTI_ALIAS_FLAG);
        textHeaderLeft.setColor(Color.parseColor("#8B949E"));
        textHeaderLeft.setTextSize(dpToPx(12f));

        textHeaderRight = new Paint(Paint.ANTI_ALIAS_FLAG);
        textHeaderRight.setColor(Color.parseColor("#FFFFFF"));
        textHeaderRight.setTextSize(dpToPx(13f));
        textHeaderRight.setFakeBoldText(true);
        textHeaderRight.setTextAlign(Paint.Align.RIGHT);

        textAxis = new Paint(Paint.ANTI_ALIAS_FLAG);
        textAxis.setColor(Color.parseColor("#8B949E"));
        textAxis.setTextSize(dpToPx(10f));
    }

    public void setData(int[] halfHourSeconds) {
        if (halfHourSeconds != null) {
            System.arraycopy(halfHourSeconds, 0, slotSeconds, 0, Math.min(halfHourSeconds.length, 48));
        }
        invalidate();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private int getCurrentSlotIndex() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int slot = (hour * 2) + (minute >= 30 ? 1 : 0);
        return Math.max(0, Math.min(47, slot));
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus) {
            borderPaint.setColor(Color.parseColor("#E52D27")); // Highlight card border in red when focused
            borderPaint.setStrokeWidth(dpToPx(2.5f));
            if (selectedSlot < 0) {
                // Find most recent slot with usage, or default to current slot
                int lastActive = -1;
                for (int i = 47; i >= 0; i--) {
                    if (slotSeconds[i] > 0) {
                        lastActive = i;
                        break;
                    }
                }
                selectedSlot = (lastActive != -1) ? lastActive : getCurrentSlotIndex();
            }
        } else {
            borderPaint.setColor(Color.parseColor("#30363D"));
            borderPaint.setStrokeWidth(dpToPx(1.5f));
        }
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (selectedSlot < 0) {
                selectedSlot = getCurrentSlotIndex();
            } else if (selectedSlot > 0) {
                selectedSlot--;
            }
            invalidate();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (selectedSlot < 0) {
                selectedSlot = getCurrentSlotIndex();
            } else if (selectedSlot < 47) {
                selectedSlot++;
            }
            invalidate();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cardRadius = dpToPx(12f);

        // 1. Draw Card Background & Border
        bgRect.set(dpToPx(1f), dpToPx(1f), w - dpToPx(1f), h - dpToPx(1f));
        canvas.drawRoundRect(bgRect, cardRadius, cardRadius, bgPaint);
        canvas.drawRoundRect(bgRect, cardRadius, cardRadius, borderPaint);

        // Layout measurements
        float padLeft = dpToPx(22f);
        float padRight = dpToPx(38f);
        float padTop = dpToPx(38f);
        float padBottom = dpToPx(24f);

        float chartLeft = padLeft;
        float chartRight = w - padRight;
        float chartTop = padTop;
        float chartBottom = h - padBottom;
        float chartHeight = chartBottom - chartTop;
        float chartWidth = chartRight - chartLeft;

        // 2. Draw Header
        canvas.drawText("Activity Breakdown (every 30 mins)", padLeft, dpToPx(22f), textHeaderLeft);

        if (selectedSlot >= 0 && selectedSlot < 48) {
            int startH = selectedSlot / 2;
            int startM = (selectedSlot % 2) * 30;
            int endH = (selectedSlot + 1) / 2;
            int endM = ((selectedSlot + 1) % 2) * 30;
            int sec = slotSeconds[selectedSlot];
            int mins = (sec + 59) / 60;
            if (sec == 0) mins = 0;

            String detail = String.format(Locale.US, "%02d:%02d – %02d:%02d : %d min watched", startH, startM, endH, endM, mins);
            canvas.drawText(detail, chartRight, dpToPx(22f), textHeaderRight);
        } else {
            int totalSec = 0;
            int peakSec = 0;
            for (int s : slotSeconds) {
                totalSec += s;
                if (s > peakSec) peakSec = s;
            }
            int totalM = totalSec / 60;
            int peakM = (peakSec + 59) / 60;
            String summary = String.format(Locale.US, "Today: %dh %dm  •  Peak: %dm / 30m slot", totalM / 60, totalM % 60, peakM);
            canvas.drawText(summary, chartRight, dpToPx(22f), textHeaderRight);
        }

        // 3. Draw Grid Lines (30m top, 15m mid, 0m baseline)
        float midY = chartTop + (chartHeight / 2f);
        canvas.drawLine(chartLeft, chartTop, chartRight, chartTop, gridPaint);
        canvas.drawLine(chartLeft, midY, chartRight, midY, gridPaint);
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, borderPaint);

        // Axis Y labels
        textAxis.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("30m", chartRight + dpToPx(6f), chartTop + dpToPx(4f), textAxis);
        canvas.drawText("15m", chartRight + dpToPx(6f), midY + dpToPx(4f), textAxis);
        canvas.drawText("0m", chartRight + dpToPx(6f), chartBottom + dpToPx(3f), textAxis);

        // 4. Draw 48 Bars
        float slotWidth = chartWidth / 48f;
        float barWidth = Math.max(dpToPx(4f), slotWidth * 0.70f);
        float barRadius = dpToPx(2f);

        for (int i = 0; i < 48; i++) {
            float centerX = chartLeft + (i + 0.5f) * slotWidth;
            float bLeft = centerX - (barWidth / 2f);
            float bRight = centerX + (barWidth / 2f);

            // Empty slot baseline track (subtle 2dp dot)
            barRect.set(bLeft, chartBottom - dpToPx(2.5f), bRight, chartBottom);
            canvas.drawRoundRect(barRect, barRadius, barRadius, trackPaint);

            // Active usage bar
            int sec = slotSeconds[i];
            if (sec > 0) {
                float frac = Math.min(1.0f, sec / 1800f);
                float activeHeight = Math.max(dpToPx(4f), frac * chartHeight);
                float bTop = chartBottom - activeHeight;
                barRect.set(bLeft, bTop, bRight, chartBottom);

                if (i == selectedSlot) {
                    canvas.drawRoundRect(barRect, barRadius, barRadius, barSelectedPaint);
                } else {
                    canvas.drawRoundRect(barRect, barRadius, barRadius, barActivePaint);
                }
            }

            // If selected slot: draw highlight cursor around column
            if (i == selectedSlot && isFocused()) {
                cursorRect.set(centerX - (slotWidth / 2f) + dpToPx(1f), chartTop - dpToPx(2f), centerX + (slotWidth / 2f) - dpToPx(1f), chartBottom + dpToPx(2f));
                canvas.drawRoundRect(cursorRect, dpToPx(4f), dpToPx(4f), cursorPaint);
            }
        }

        // 5. Draw X-axis Time Labels (every 4 hours: 00:00, 04:00, 08:00, 12:00, 16:00, 20:00, 24:00)
        textAxis.setTextAlign(Paint.Align.CENTER);
        for (int hour = 0; hour <= 24; hour += 4) {
            int slotIdx = Math.min(47, hour * 2);
            float centerX = (hour == 24) ? chartRight : chartLeft + (slotIdx * slotWidth);
            String label = String.format(Locale.US, "%02d:00", hour);
            canvas.drawText(label, centerX, chartBottom + dpToPx(15f), textAxis);
        }
    }
}

