package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * show_stats with stats_rings on: one ring per figure, in the style of the Apple Watch activity rings. A dim track,
 * a round-capped arc for the fraction, the figure in the middle and a caption under it. Drawn once per reading
 * (every STATS_RINGS_MS while Home is on screen) and not in between: easing the arcs to each reading kept the TV
 * drawing about 19 frames a second on Home (MiTV-MOOQ0: 2.7 s of CPU per 30 s, against 0.1 s with the rings off),
 * as the CPU clock moves with nearly every reading.
 */
public class StatsRingsView extends View {
    /** CPU red, RAM green, storage cyan, network orange: Apple's ring colours, so the four tell apart at a glance. */
    private static final int[] COLORS = {0xFFFA114F, 0xFF92E82A, 0xFF1EEAEF, 0xFFFF9F0A};
    private static final int RINGS = COLORS.length;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float density;

    private final float[] fractions = new float[RINGS];
    private final String[] values = new String[RINGS];
    private final String[] captions = new String[RINGS];
    private final String[] details = new String[RINGS];

    public StatsRingsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        value.setColor(0xFFFFFFFF);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(UiFont.get(context, R.string.font_medium));
        caption.setTextAlign(Paint.Align.CENTER);
        caption.setTypeface(UiFont.get(context, R.string.font_regular));
        for (int i = 0; i < RINGS; i++) {
            values[i] = "–";
            captions[i] = "";
            details[i] = "";
        }
    }

    /**
     * One reading. Index 0..3 is CPU, RAM, storage, network. fraction is 0..1, or negative for a figure the TV
     * would not give (its ring stays an empty track with a dash). detail is a second small line, may be empty.
     * Call invalidate() after the four.
     */
    void set(int i, float fraction, String figure, String label, String more) {
        fractions[i] = fraction < 0 ? 0f : Math.min(1f, fraction);
        values[i] = fraction < 0 ? "–" : figure;
        captions[i] = label;
        details[i] = more == null ? "" : more;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int ring = (int) (76 * density), gap = (int) (28 * density);
        int w = RINGS * ring + (RINGS - 1) * gap + getPaddingLeft() + getPaddingRight();
        int h = ring + (int) (40 * density) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(w, widthSpec), resolveSize(h, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float ring = 76 * density, gap = 28 * density, stroke = 8 * density;
        float avail = getWidth() - getPaddingLeft() - getPaddingRight();
        float x0 = getPaddingLeft() + Math.max(0f, avail - (RINGS * ring + (RINGS - 1) * gap));
        float top = getPaddingTop();
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        value.setTextSize(17 * density);
        caption.setTextSize(11 * density);
        for (int i = 0; i < RINGS; i++) {
            float left = x0 + i * (ring + gap);
            oval.set(left + stroke / 2, top + stroke / 2, left + ring - stroke / 2, top + ring - stroke / 2);
            track.setColor((COLORS[i] & 0x00FFFFFF) | 0x33000000);
            canvas.drawArc(oval, 0, 360, false, track);
            arc.setColor(COLORS[i]);
            canvas.drawArc(oval, -90, fractions[i] * 360f, false, arc);
            float cx = left + ring / 2;
            float cy = top + ring / 2 - (value.ascent() + value.descent()) / 2;
            canvas.drawText(values[i], cx, cy, value);
            float capY = top + ring + 18 * density;
            caption.setColor(0xB3FFFFFF);
            caption.setLetterSpacing(0.14f);
            canvas.drawText(captions[i], cx, capY, caption);
            caption.setColor(0x80FFFFFF);   // the detail line: the caption's paint, dimmer and not spaced out
            caption.setLetterSpacing(0f);
            canvas.drawText(details[i], cx, capY + 15 * density, caption);
        }
    }
}
