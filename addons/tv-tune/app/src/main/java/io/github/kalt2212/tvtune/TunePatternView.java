package io.github.kalt2212.tvtune;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Original app-code patterns; no images, filters, LUTs or system-setting writes. */
public final class TunePatternView extends View {
    private final Paint paint = new Paint();
    private final int kind;
    private final PatternState fields;
    private boolean labels;
    private static final int INK = 0xfff1f5fb, MUTED = 0xffa4b4cc, ACCENT = 0xff88cfff;
    private static final int[] SHADOWS = {0, 2, 4, 8, 12, 16, 24, 32};
    private static final int[] HIGHLIGHTS = {223, 231, 239, 247, 251, 253, 255};
    private static final int[] RAMP = {0,17,34,51,68,85,102,119,136,153,170,187,204,221,238,255};
    private static final int[] COVER_COLORS = {0xff7598ff, 0xff85c9f4, 0xff9bd8cb, 0xffe0dcb0, 0xffedbd99, 0xffdc9fb2, 0xffbaa9df};
    private final RectF coverBounds = new RectF();
    private final Typeface labelTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public TunePatternView(Context context, int kind, PatternState fields, boolean labels) {
        super(context); this.kind = kind; this.fields = fields; this.labels = labels;
        setFocusable(kind != -2); setContentDescription(context.getString(kind == -2 ? R.string.app_name : R.string.show_pattern));
        setImportantForAccessibility(kind == -2 ? IMPORTANT_FOR_ACCESSIBILITY_NO : IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    public void setLabels(boolean value) { labels = value; invalidate(); }
    private float unit() { return Math.min(getWidth() / 960f, getHeight() / 540f); }
    private void rectangle(Canvas canvas, float left, float top, float right, float bottom, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setAntiAlias(false); paint.setColor(color); canvas.drawRect(left, top, right, bottom, paint);
    }
    private void line(Canvas canvas, float x1, float y1, float x2, float y2, int color, float width) {
        paint.setStyle(Paint.Style.STROKE); paint.setColor(color); paint.setStrokeWidth(width); paint.setAntiAlias(true); canvas.drawLine(x1, y1, x2, y2, paint);
    }
    private void label(Canvas canvas, String text, float x, float y, float size, int color, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setTextSize(size); paint.setAntiAlias(true); paint.setTextAlign(align);
        paint.setTypeface(labelTypeface); canvas.drawText(text, x, y, paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), u = unit();
        if (w <= 0 || h <= 0) return;
        if (kind == -2) { cover(canvas, w, h); return; }
        canvas.drawColor(Color.BLACK);
        if (fields.dimmed()) {
            canvas.drawColor(0xff111111);
            label(canvas, getResources().getString(R.string.dimmed), w / 2, h / 2, 20 * u, MUTED, Paint.Align.CENTER);
            return;
        }
        if (kind == -1) {
            int[] rgb = fields.rgb(); float size = (float) Math.min(Math.sqrt(w * h * 0.1), Math.min(w, h));
            rectangle(canvas, (w - size) / 2, (h - size) / 2, (w + size) / 2, (h + size) / 2, Color.rgb(rgb[0], rgb[1], rgb[2]));
            if (labels) label(canvas, fields.id() + "  ·  RGB " + rgb[0] + "/" + rgb[1] + "/" + rgb[2], w / 2, h * 0.82f, 23 * u, INK, Paint.Align.CENTER);
        } else if (kind == 0) {
            paint.setAntiAlias(true); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * u); paint.setColor(INK);
            canvas.drawRect(2 * u, 2 * u, w - 2 * u, h - 2 * u, paint);
            canvas.drawRect(w * 0.05f, h * 0.05f, w * 0.95f, h * 0.95f, paint);
            canvas.drawCircle(w / 2, h / 2, Math.min(w, h) * 0.28f, paint);
            line(canvas, w / 2, h * 0.12f, w / 2, h * 0.88f, 0xff526783, u);
            line(canvas, w * 0.15f, h / 2, w * 0.85f, h / 2, 0xff526783, u);
        } else if (kind == 1) steps(canvas, SHADOWS);
        else if (kind == 2) steps(canvas, HIGHLIGHTS);
        else if (kind == 3) {
            rectangle(canvas, w * 0.13f, h * 0.3f, w * 0.87f, h * 0.7f, INK);
            for (int group = 0; group < 4; group++) {
                int width = 1 << group; float start = w * 0.16f + group * w * 0.17f;
                for (int x = 0; x < w * 0.12f; x += width * 2) rectangle(canvas, start + x, h * 0.34f, start + x + width, h * 0.66f, Color.BLACK);
            }
        } else {
            steps(canvas, RAMP);
        }
        if (labels) {
            label(canvas, "TV Tune", 32 * u, 38 * u, 22 * u, ACCENT, Paint.Align.LEFT);
            label(canvas, getResources().getString(R.string.render_size, getWidth(), getHeight()), w / 2, h - 55 * u, 13 * u, MUTED, Paint.Align.CENTER);
            label(canvas, getResources().getString(kind == -1 ? R.string.field_hint : R.string.pattern_hint), w / 2, h - 29 * u, 15 * u, INK, Paint.Align.CENTER);
        }
    }
    private void steps(Canvas canvas, int[] values) {
        float w = getWidth(), h = getHeight(), unit = unit(), span = w * 0.84f, start = w * 0.08f;
        float cell = span / values.length;
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            rectangle(canvas, start + i * cell + 2, h * 0.32f, start + (i + 1) * cell - 2, h * 0.68f, Color.rgb(value, value, value));
            if (labels) label(canvas, Integer.toString(value), start + (i + 0.5f) * cell, h * 0.75f, 15 * unit, MUTED, Paint.Align.CENTER);
        }
    }
    private void cover(Canvas canvas, float w, float h) {
        canvas.drawColor(0xff111e34); float u = Math.min(w / 480f, h / 300f);
        for (int i = 1; i <= 4; i++) {
            line(canvas, w * i / 5, 20 * u, w * i / 5, h - 20 * u, 0xff233954, u);
            line(canvas, 20 * u, h * i / 5, w - 20 * u, h * i / 5, 0xff233954, u);
        }
        rectangle(canvas, w * 0.1f, h * 0.17f, w * 0.9f, h * 0.76f, 0xff02050a);
        paint.setStyle(Paint.Style.STROKE); paint.setAntiAlias(true); paint.setColor(0xff7595b8); paint.setStrokeWidth(2 * u);
        coverBounds.set(w * 0.09f, h * 0.15f, w * 0.91f, h * 0.78f);
        canvas.drawRoundRect(coverBounds, 10 * u, 10 * u, paint);
        float size = Math.min(w, h) * 0.25f;
        rectangle(canvas, (w - size) / 2, h * 0.46f - size / 2, (w + size) / 2, h * 0.46f + size / 2, 0xff808080);
        float left = (w - size) / 2 - 9 * u, right = (w + size) / 2 + 9 * u, top = h * 0.46f - size / 2 - 9 * u, bottom = h * 0.46f + size / 2 + 9 * u;
        line(canvas, left, top, left + 18 * u, top, ACCENT, 2 * u); line(canvas, left, top, left, top + 18 * u, ACCENT, 2 * u);
        line(canvas, right, top, right - 18 * u, top, ACCENT, 2 * u); line(canvas, right, top, right, top + 18 * u, ACCENT, 2 * u);
        line(canvas, left, bottom, left + 18 * u, bottom, ACCENT, 2 * u); line(canvas, left, bottom, left, bottom - 18 * u, ACCENT, 2 * u);
        line(canvas, right, bottom, right - 18 * u, bottom, ACCENT, 2 * u); line(canvas, right, bottom, right, bottom - 18 * u, ACCENT, 2 * u);
        line(canvas, w / 2, h * 0.79f, w / 2, h * 0.85f, 0xff7595b8, 2 * u);
        line(canvas, w * 0.4f, h * 0.85f, w * 0.6f, h * 0.85f, 0xff7595b8, 2 * u);
        for (int i = 0; i < COVER_COLORS.length; i++) rectangle(canvas, w * 0.25f + i * w * 0.073f, h * 0.91f, w * 0.25f + (i + 0.8f) * w * 0.073f, h * 0.93f, COVER_COLORS[i]);
    }
}
