package com.tungpv.lichvannien;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * The whole calendar screen, drawn on one canvas. Every size is a fraction of the view's width, so the layout is
 * the approved mockup ("Bản phác 3") at any resolution: left column with the lunar date, verdict and can chi, a
 * 12-hour ring with the zodiac animals on the right, the day's trực at the bottom.
 */
final class CalendarView extends View {

    private static final int TEXT = 0xFFF4F1EA;
    private static final int GOLD = 0xFFE8C77D;
    private static final int SILVER = 0xFFC2C7D2;
    private static final int RED = 0xFFD99A8A;
    private static final String[] WEEKDAYS = {"Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật"};
    private static final String[] LUNAR_MONTHS = {"Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín",
            "Mười", "Mười Một", "Chạp"};
    /** Clock hour each double-hour starts at, Tý first. */
    private static final int[] START_HOUR = {23, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface serif = Typeface.create("serif", Typeface.NORMAL);
    private final Typeface sans = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface sansLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Path path = new Path();
    private final RectF outer = new RectF(), inner = new RectF();

    private VnCalendar.Day day;
    /** Current double-hour, or -1 when another day than today is shown. */
    private int nowHour = -1;
    private int nowClockHour;

    CalendarView(Context context) {
        super(context);
    }

    void show(VnCalendar.Day day, int nowHour, int nowClockHour) {
        this.day = day;
        this.nowHour = nowHour;
        this.nowClockHour = nowClockHour;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        drawBackground(c, getWidth(), getHeight());
        if (day == null) return;
        // Lay out in a centred 16:9 frame, so another screen shape letterboxes instead of shifting things.
        float w = Math.min(getWidth(), getHeight() * 16f / 9f), h = w * 9f / 16f, u = w / 100f;
        c.save();
        c.translate((getWidth() - w) / 2f, (getHeight() - h) / 2f);
        float ringR = 18 * u, ringX = w - 6 * u - ringR, ringLeft = ringX - ringR * 1.16f;

        float left = 6 * u, y = 7 * u;
        caps(c, WEEKDAYS[day.weekday] + " · " + day.solarDay + " tháng " + day.solarMonth + " · " + day.solarYear,
                left, y, 1.45f * u, 0x8CF4F1EA);

        // Lunar day, big.
        paint.setTypeface(serif);
        paint.setTextSize(15 * u);
        paint.setColor(TEXT);
        String lunarDay = String.valueOf(day.lunarDay);
        float base = y + 14.5f * u;
        c.drawText(lunarDay, left - 0.6f * u, base, paint);
        float dx = left + paint.measureText(lunarDay) + 2.2f * u;
        paint.setTextSize(3.6f * u);
        c.drawText("tháng " + LUNAR_MONTHS[day.lunarMonth - 1] + (day.leap ? " nhuận" : ""), dx, base - 6.2f * u, paint);
        caps(c, "Âm lịch · " + VnCalendar.moonWord(day.lunarDay), dx, base - 2.4f * u, 1.35f * u, 0x8CF4F1EA,
                ringLeft - 1.5f * u);

        // Thin gold rule.
        float ry = base + 3.4f * u;
        paint.setShader(new LinearGradient(left, 0, left + 46 * u, 0, 0x99E8C77D, 0x00E8C77D, Shader.TileMode.CLAMP));
        c.drawRect(left, ry, left + 46 * u, ry + Math.max(1, 0.1f * u), paint);
        paint.setShader(null);

        // Verdict.
        paint.setTypeface(serif);
        paint.setTextSize(5.4f * u);
        paint.setColor(day.good ? GOLD : SILVER);
        float vy = ry + 7.6f * u;
        c.drawText(day.good ? "Ngày Hoàng đạo" : "Ngày Hắc đạo", left, vy, paint);
        paint.setTypeface(sans);
        paint.setTextSize(2f * u);
        paint.setColor(0xB3F4F1EA);
        c.drawText("sao " + VnCalendar.STARS[day.star] + " chiếu", left, vy + 3.6f * u, paint);

        // Can chi columns.
        float cy = vy + 10 * u;
        String[] heads = {"Ngày", "Tháng", "Năm"};
        String[] vals = {
                VnCalendar.CAN[day.dayCan] + " " + VnCalendar.CHI[day.dayChi],
                VnCalendar.CAN[day.monthCan] + " " + VnCalendar.CHI[day.monthChi],
                VnCalendar.CAN[day.yearCan] + " " + VnCalendar.CHI[day.yearChi]};
        float cx = left;
        for (int i = 0; i < 3; i++) {
            caps(c, heads[i], cx, cy, 1.35f * u, 0x8CF4F1EA);
            paint.setTypeface(serif);
            paint.setTextSize(3.1f * u);
            paint.setColor(TEXT);
            c.drawText(vals[i], cx, cy + 4.2f * u, paint);
            cx += paint.measureText(vals[i]) + 4.5f * u;
        }

        paint.setTypeface(sans);
        paint.setTextSize(1.4f * u);
        paint.setColor(0x73F4F1EA);
        paint.setTextAlign(Paint.Align.RIGHT);
        c.drawText("◀ ▶ đổi ngày  ·  OK về hôm nay", w - 6 * u, y, paint);
        paint.setTextAlign(Paint.Align.LEFT);

        drawRing(c, ringX, h * 0.53f, ringR, u);
        drawTruc(c, left, w - 6 * u, h - 3.6f * u, u);
        c.restore();
    }

    private void drawBackground(Canvas c, float w, float h) {
        paint.setShader(new LinearGradient(0, 0, 0, h, 0xFF141821, 0xFF0B0D12, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * 0.22f, h * 0.18f, w * 0.6f,
                day != null && !day.good ? 0x332E3445 : 0x3348391C, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * 0.8f, h * 0.55f, w * 0.35f, 0x1A3A4458, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    /** Small letter-spaced uppercase caption, like the launcher's header. */
    private void caps(Canvas c, String text, float x, float y, float size, int color) {
        caps(c, text, x, y, size, color, Float.MAX_VALUE);
    }

    /** As above, made smaller when it would run past maxRight. */
    private void caps(Canvas c, String text, float x, float y, float size, int color, float maxRight) {
        String upper = text.toUpperCase(new java.util.Locale("vi"));
        paint.setTypeface(sansLight);
        paint.setLetterSpacing(0.3f);
        paint.setTextSize(size);
        float width = paint.measureText(upper);
        if (x + width > maxRight) paint.setTextSize(size * (maxRight - x) / width);
        paint.setColor(color);
        c.drawText(upper, x, y, paint);
        paint.setLetterSpacing(0);
    }

    /** The 12 double-hours as a clock face: Tý at the top, good hours gold, animal and name in each segment. */
    private void drawRing(Canvas c, float cx, float cy, float r, float u) {
        boolean[] good = VnCalendar.goodHours(day.dayChi);
        // From 23:00 the Tý segment is the next day's Tý.
        if (nowHour == 0) good[0] = VnCalendar.goodAt(day.dayChi, nowClockHour);
        float r1 = r, r2 = r * 0.64f;
        outer.set(cx - r1, cy - r1, cx + r1, cy + r1);
        inner.set(cx - r2, cy - r2, cx + r2, cy + r2);
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 12; i++) {
            float start = i * 30 - 90 - 15 + 0.8f, sweep = 30 - 1.6f;
            path.reset();
            path.arcTo(outer, start, sweep);
            path.arcTo(inner, start + sweep, -sweep);
            path.close();
            paint.setColor(good[i] ? (i == nowHour ? 0xFFF1D592 : 0xD9E8C77D) : (i == nowHour ? 0x33FFFFFF : 0x14FFFFFF));
            c.drawPath(path, paint);

            double a = Math.toRadians(i * 30 - 90);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            // Animal above its name, both centred on the middle of the segment.
            float mx = cx + r * 0.82f * cos, my = cy + r * 0.82f * sin;
            paint.setTypeface(sans);
            paint.setTextSize(r * 0.12f);
            paint.setColor(Color.BLACK);
            paint.setAlpha(good[i] ? 255 : 110);
            c.drawText(VnCalendar.ANIMALS[i], mx, my + r * 0.005f, paint);
            paint.setTypeface(serif);
            paint.setTextSize(r * 0.082f);
            paint.setColor(good[i] ? 0xFF1B1A17 : 0x80F4F1EA);
            c.drawText(VnCalendar.CHI[i], mx, my + r * 0.1f, paint);

            // Starting clock hour outside the ring.
            paint.setTypeface(sans);
            paint.setTextSize(r * 0.07f);
            paint.setColor(0x80F4F1EA);
            float hr = r * 1.1f;
            c.drawText(String.valueOf(START_HOUR[i]), cx + hr * cos, cy + hr * sin + r * 0.025f, paint);
        }

        // Centre.
        if (nowHour >= 0) {
            double a = Math.toRadians(nowHour * 30 - 90);
            paint.setColor(TEXT);
            c.drawCircle(cx + r * 0.58f * (float) Math.cos(a), cy + r * 0.58f * (float) Math.sin(a), r * 0.025f, paint);
            centre(c, cx, cy, r, "Bây giờ", VnCalendar.ANIMALS[nowHour], "giờ " + VnCalendar.CHI[nowHour],
                    (good[nowHour] ? "hoàng đạo" : "hắc đạo") + " · đến " + ((START_HOUR[nowHour] + 2) % 24) + ":00",
                    good[nowHour]);
        } else {
            centre(c, cx, cy, r, "Ngày", VnCalendar.ANIMALS[day.dayChi], VnCalendar.CHI[day.dayChi],
                    day.solarDay + "/" + day.solarMonth + "/" + day.solarYear, day.good);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void centre(Canvas c, float cx, float cy, float r, String top, String animal, String main, String sub,
                        boolean gold) {
        paint.setTypeface(sansLight);
        paint.setTextSize(r * 0.058f);
        paint.setColor(0x8CF4F1EA);
        paint.setLetterSpacing(0.3f);
        c.drawText(top.toUpperCase(new java.util.Locale("vi")), cx, cy - r * 0.33f, paint);
        paint.setLetterSpacing(0);
        paint.setTypeface(sans);
        paint.setTextSize(r * 0.25f);
        paint.setColor(Color.BLACK);
        c.drawText(animal, cx, cy + r * 0.02f, paint);
        paint.setTypeface(serif);
        paint.setTextSize(r * 0.12f);
        paint.setColor(gold ? GOLD : SILVER);
        c.drawText(main, cx, cy + r * 0.2f, paint);
        paint.setTypeface(sans);
        paint.setTextSize(r * 0.06f);
        paint.setColor(0x99F4F1EA);
        c.drawText(sub, cx, cy + r * 0.31f, paint);
    }

    /** "Trực Nguy · Nên … · Kiêng …", made smaller as a whole when the day's lists would run past right. */
    private void drawTruc(Canvas c, float left, float right, float y, float u) {
        String truc = "Trực " + VnCalendar.TRUC[day.truc];
        String nen = list(VnCalendar.nen(day.truc)), kieng = list(VnCalendar.kieng(day.truc));
        float scale = 1;
        float width = trucLine(c, truc, nen, kieng, left, y, u, false);
        if (width > right - left) scale = (right - left) / width;
        trucLine(c, truc, nen, kieng, left, y, u * scale, true);
    }

    /** Draws the line (or only measures it) at unit u; returns its width. */
    private float trucLine(Canvas c, String truc, String nen, String kieng, float left, float y, float u, boolean draw) {
        float x = left;
        paint.setTypeface(sansLight);
        paint.setLetterSpacing(0.3f);
        paint.setTextSize(1.45f * u);
        String upper = truc.toUpperCase(new java.util.Locale("vi"));
        if (draw) {
            paint.setColor(0xB3F4F1EA);
            c.drawText(upper, x, y, paint);
        }
        x += paint.measureText(upper) + 2.2f * u;
        paint.setLetterSpacing(0);
        x = label(c, "Nên", nen, x, y, u, GOLD, draw);
        x = label(c, "Kiêng", kieng, x + 2.6f * u, y, u, RED, draw);
        return x - left;
    }

    /** "Cúng tế, cầu an, việc nhỏ": only the first item keeps its capital. */
    private static String list(String[] items) {
        StringBuilder b = new StringBuilder(items[0]);
        for (int i = 1; i < items.length; i++) {
            b.append(", ").append(items[i].substring(0, 1).toLowerCase(new java.util.Locale("vi"))).append(items[i].substring(1));
        }
        return b.toString();
    }

    private float label(Canvas c, String head, String body, float x, float y, float u, int color, boolean draw) {
        paint.setTypeface(sans);
        paint.setTextSize(1.75f * u);
        paint.setColor(color);
        if (draw) c.drawText(head, x, y, paint);
        x += paint.measureText(head) + 0.9f * u;
        paint.setColor(0xD9F4F1EA);
        if (draw) c.drawText(body, x, y, paint);
        return x + paint.measureText(body);
    }
}
