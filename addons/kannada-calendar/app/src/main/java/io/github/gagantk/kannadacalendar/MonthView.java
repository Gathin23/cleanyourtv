package io.github.gagantk.kannadacalendar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * The month as a printed page: weekday heads in English and Kannada, the Sunday column tinted and its dates in
 * red, public holidays printed the same way, a serif date in each square with its tithi under it and its first
 * festivals at the foot, the marks of the main festivals and of Purnima and Amavasya in its corner, and today's date
 * on a saffron badge. It is one focusable view that
 * moves its own highlight: Left and Right step a day, Down a week, and stepping out of the month turns the page;
 * Up from the first week lets focus go to the controls in the band above, OK runs the action set for it, and the
 * number keys go to a date.
 */
final class MonthView extends View {
    interface Listener { void onDay(LocalDate day); }

    private final TextPaint number = paint(Typeface.create(Typeface.SERIF, Typeface.BOLD)), small = paint(Typeface.DEFAULT),
            fest = paint(Typeface.DEFAULT), head = paint(Typeface.DEFAULT_BOLD), second = paint(Typeface.DEFAULT),
            emoji = paint(Typeface.DEFAULT);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Book book;
    private YearMonth month = YearMonth.now();
    private LocalDate selected = LocalDate.now();
    private Listener listener;
    private Runnable onEnter;

    MonthView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        line.setStrokeWidth(Math.max(1, Ui.dp(c, 1)));
    }

    private static TextPaint paint(Typeface face) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(face);
        return p;
    }

    void setListener(Listener l) { listener = l; }

    /** What OK on a day does: here, take the remote into the day's festival list. */
    void setOnEnter(Runnable r) { onEnter = r; }

    void setBook(Book b) {
        book = b;
        invalidate();
    }

    /**
     * The printed page without the highlight, drawn once for the month and kept: laying out the text of every square
     * again on each key press is far too slow for a TV box. Drawn again for a new month, calendar, size or day.
     */
    private Bitmap printed;
    private YearMonth printedMonth;
    private LocalDate printedToday;
    private Book printedBook;

    LocalDate selected() { return selected; }

    void select(LocalDate day) {
        selected = day;
        month = YearMonth.from(day);
        invalidate();
        if (listener != null) listener.onDay(day);
    }

    private int lead() { return month.atDay(1).getDayOfWeek().getValue() % 7; }

    private int rows() { return (lead() + month.lengthOfMonth() + 6) / 7; }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: select(selected.minusDays(1)); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: select(selected.plusDays(1)); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                // The first week hands focus to the band's controls; Channel Up turns back a month.
                if ((lead() + selected.getDayOfMonth() - 1) / 7 == 0) return false;
                select(selected.minusWeeks(1));
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: select(selected.plusWeeks(1)); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                if (onEnter != null) onEnter.run();
                return true;
            default:
                if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                    digit(code - KeyEvent.KEYCODE_0);
                    return true;
                }
                return super.onKeyDown(code, event);
        }
    }

    private int typed;
    private long typedAt;

    /**
     * The number keys go straight to a date of the month on the page: 1 then 5 is the 15th. A second digit
     * pressed within a moment and still a date of the month joins the first.
     */
    private void digit(int d) {
        long now = android.os.SystemClock.uptimeMillis();
        int joined = typed * 10 + d;
        typed = now - typedAt < 1500 && typed > 0 && joined <= month.lengthOfMonth() ? joined : d;
        typedAt = now;
        if (typed >= 1 && typed <= month.lengthOfMonth()) select(month.atDay(typed));
    }

    @Override protected void onDraw(Canvas canvas) {
        Context c = getContext();
        Palette p = Palette.current;
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float w = getWidth(), h = getHeight();
        float headH = Ui.dp(c, 42);
        float cw = w / 7f, ch = (h - headH) / rows();
        int lead = lead();

        // Under the highlight: the Sunday column tinted the whole way down, as printed calendars do, and the public
        // holidays printed like Sundays.
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(p.sundayTint);
        canvas.drawRect(0, 0, cw, h, fill);
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            int i = lead + d - 1, col = i % 7;
            Day day = book == null ? null : book.day(month.atDay(d));
            if (col != 0 && day != null && !day.publicHolidays.isEmpty()) {
                canvas.drawRect(col * cw, headH + (i / 7) * ch, (col + 1) * cw, headH + (i / 7 + 1) * ch, fill);
            }
        }

        // The highlight.
        int si = lead + selected.getDayOfMonth() - 1;
        float sx = (si % 7) * cw, sy = headH + (si / 7) * ch;
        boolean focus = hasFocus();
        rect.set(sx + 3, sy + 3, sx + cw - 3, sy + ch - 3);
        fill.setColor(focus ? p.focusFill : (p.focusFill & 0x00FFFFFF) | 0x66000000);
        canvas.drawRoundRect(rect, 8, 8, fill);
        if (focus) {
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(Ui.dp(c, 3));
            fill.setColor(p.saffron);
            canvas.drawRoundRect(rect, 8, 8, fill);
        }

        // Over it, the printed page.
        LocalDate today = LocalDate.now();
        if (printed == null || printed.getWidth() != getWidth() || printed.getHeight() != getHeight()
                || !month.equals(printedMonth) || !today.equals(printedToday) || book != printedBook) {
            if (printed == null || printed.getWidth() != getWidth() || printed.getHeight() != getHeight()) {
                if (printed != null) printed.recycle();
                printed = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            } else {
                printed.eraseColor(0);
            }
            print(new Canvas(printed), today);
            printedMonth = month;
            printedToday = today;
            printedBook = book;
        }
        canvas.drawBitmap(printed, 0, 0, null);
    }

    /** The page as printed: weekday heads, rules, and each day's date, marks, tithi and festivals. */
    private void print(Canvas canvas, LocalDate today) {
        Context c = getContext();
        Palette p = Palette.current;
        float w = getWidth(), h = getHeight();
        float headH = Ui.dp(c, 42);
        int rows = rows();
        float cw = w / 7f, ch = (h - headH) / rows;
        float pad = Ui.dp(c, 8);

        // Weekday heads: English, with the Kannada name under it.
        head.setTextSize(Ui.dp(c, 14));
        head.setLetterSpacing(0.14f);
        second.setTextSize(Ui.dp(c, 11));
        for (int col = 0; col < 7; col++) {
            DayOfWeek dow = DayOfWeek.SUNDAY.plus(col);
            int color = col == 0 ? p.red : p.muted;
            head.setColor(color);
            second.setColor(color);
            String english = dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ENGLISH);
            String first = english, under = Kn.WEEK_SHORT[col];
            canvas.drawText(first, col * cw + (cw - head.measureText(first)) / 2, Ui.dp(c, 19), head);
            // A cut-down firmware without a Kannada font would print empty boxes: the English head is enough.
            if (second.hasGlyph("ಭ")) {
                canvas.drawText(under, col * cw + (cw - second.measureText(under)) / 2, Ui.dp(c, 35), second);
            }
        }
        line.setColor(p.ink);
        canvas.drawLine(0, headH, w, headH, line);
        line.setColor(p.line);
        for (int r = 1; r < rows; r++) canvas.drawLine(0, headH + r * ch, w, headH + r * ch, line);
        for (int col = 1; col < 7; col++) canvas.drawLine(col * cw, headH, col * cw, h, line);

        int lead = lead();
        number.setTextSize(Math.min(ch * 0.30f, Ui.dp(c, 28)));
        small.setTextSize(Ui.dp(c, 12));
        fest.setTextSize(Ui.dp(c, 11));
        emoji.setTextSize(Ui.dp(c, 14));
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            int i = lead + d - 1, r = i / 7, col = i % 7;
            float x = col * cw, y = headH + r * ch;
            Day day = book == null ? null : book.day(date);
            // A public holiday is printed like a Sunday: its date in red.
            boolean holiday = day != null && !day.publicHolidays.isEmpty();
            float baseline = y + pad / 2 - number.ascent();
            String num = Integer.toString(d);
            if (date.equals(today)) {
                // Today's date sits on a solid badge, white on saffron, so it is found at a glance.
                Paint.FontMetrics fm = number.getFontMetrics();
                float bx = Ui.dp(c, 5), by = Ui.dp(c, 1);
                rect.set(x + pad - bx, baseline + fm.ascent * 0.78f - by,
                        x + pad + number.measureText(num) + bx, baseline + fm.descent * 0.35f + by);
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(p.today);
                canvas.drawRoundRect(rect, Ui.dp(c, 6), Ui.dp(c, 6), fill);
                number.setColor(0xFFFFFFFF);
            } else {
                number.setColor(col == 0 || holiday ? p.red : p.ink);
            }
            canvas.drawText(num, x + pad, baseline, number);
            if (day == null || day.isEmpty()) continue;

            // The corner: the moon on Purnima and Amavasya, then up to two festival marks, right to left.
            String tithiEn = Sunrise.tithi(book, date).name;
            String lower = tithiEn.toLowerCase(Locale.ROOT);
            float mark = Ui.dp(c, 16), right = x + cw - pad, my = y + pad / 2 + mark / 2 + Ui.dp(c, 2);
            boolean full = lower.startsWith("poorn") || lower.startsWith("purn"), dark = lower.startsWith("amav");
            if (full || dark) {
                moon(canvas, right - mark / 2, my, mark * 0.45f, dark);
                right -= mark + Ui.dp(c, 4);
            }
            for (String m : Symbols.of(day)) {
                if (right - mark < x + pad + number.measureText(num) + Ui.dp(c, 4)) break;
                if (m.equals(Symbols.KARNATAKA)) {
                    Symbols.karnataka(canvas, right - mark / 2, my, mark * 1.1f, p.line);
                } else if (m.equals(Symbols.GANESHA)) {
                    Symbols.ganesha(canvas, right - mark / 2, my, mark * 1.25f, p.saffron, p.red, p.dark ? 0xFF2A241E : 0xFFFFFFFF);
                } else if (emoji.hasGlyph(m)) {
                    canvas.drawText(m, right - emoji.measureText(m), my - (emoji.ascent() + emoji.descent()) / 2, emoji);
                } else if (m.equals(Symbols.LAMP)) {
                    // Android 9 and older TVs have no 🪔: a lamp drawn here instead.
                    Symbols.lamp(canvas, right - mark / 2, my, mark * 1.1f, p.saffron, 0xFFF5C518);
                } else {
                    // Without the TV's emoji for it, the mark is left out rather than shown as an empty box.
                    continue;
                }
                right -= mark + Ui.dp(c, 4);
            }

            // Dates are figures, which have no descenders: the tithi can sit close under them.
            float ty = baseline + number.descent() * 0.4f - small.ascent();
            float from = ty + small.descent() + Ui.dp(c, 1);
            // As many festival lines as the square has room for, two at most.
            int lines = Math.min(2, (int) ((y + ch - Ui.dp(c, 2) - from) / fest.getFontSpacing()));
            if (lines < 1 && holiday) {
                // A six-week month leaves no room under the tithi: the holiday's name takes the tithi's place
                // (the tithi is still in the side column).
                from = ty + small.ascent();
                lines = 1;
            } else {
                small.setColor(p.muted);
                canvas.drawText(TextUtils.ellipsize(tithiEn, small, cw - 2 * pad, TextUtils.TruncateAt.END).toString(),
                        x + pad, ty, small);
            }
            if (!day.festivals.isEmpty() && lines >= 1) {
                // Festivals in ink; red is kept for the date of a holiday and the holiday's own name, put first,
                // then the main festivals, as in the side column.
                fest.setColor(p.ink);
                Festival.Ranked ranked = Festival.ranked(day);
                SpannableStringBuilder text = new SpannableStringBuilder();
                if (ranked.holidays > 0) {
                    text.append(TextUtils.join(", ", ranked.names.subList(0, ranked.holidays)));
                    text.setSpan(new ForegroundColorSpan(p.red), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (ranked.names.size() > ranked.holidays) text.append(", ");
                }
                text.append(TextUtils.join(", ", ranked.names.subList(ranked.holidays, ranked.names.size())));
                StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), fest, (int) (cw - 2 * pad))
                        .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build();
                canvas.save();
                canvas.translate(x + pad, from);
                layout.draw(canvas);
                canvas.restore();
            }
        }
    }

    private void moon(Canvas canvas, float x, float y, float r, boolean dark) {
        Palette p = Palette.current;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(dark ? (p.dark ? 0xFF000000 : p.ink) : p.moonLight);
        canvas.drawCircle(x, y, r, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Ui.dp(getContext(), 2));
        fill.setColor(p.ink);
        canvas.drawCircle(x, y, r, fill);
    }

    /**
     * For the touch panels and air-mouse remotes some projectors and boxes have: a tap picks a day, and a tap on the
     * picked day opens its festival list, as OK does.
     */
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float headH = Ui.dp(getContext(), 42);
        if (e.getY() < headH || getWidth() <= 0) return true;
        int col = (int) (e.getX() / (getWidth() / 7f)), row = (int) ((e.getY() - headH) / ((getHeight() - headH) / rows()));
        int d = row * 7 + col - lead() + 1;
        if (col < 0 || col > 6 || d < 1 || d > month.lengthOfMonth()) return true;
        requestFocus();
        LocalDate date = month.atDay(d);
        if (date.equals(selected)) performClick();
        else select(date);
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        if (onEnter != null) onEnter.run();
        return true;
    }

    @Override protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        invalidate();
    }
}
