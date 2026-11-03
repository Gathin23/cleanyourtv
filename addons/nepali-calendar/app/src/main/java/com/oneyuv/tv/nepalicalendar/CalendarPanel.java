package com.oneyuv.tv.nepalicalendar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared foreground-only scene. No timers survive the activity or dream stopping. */
final class CalendarPanel extends LinearLayout {
    static final int TEXT = Color.WHITE, DIM = 0xffa0aab8, SOFT = 0xffdddddd,
            ACCENT = 0xffe8a05a, BACKGROUND = 0xff1c1210, HOLIDAY = 0xffff7b72, TODAY = 0xff8a3a1c;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean dream;
    private final LinearLayout info;
    private final TextView greeting, clock, nepali, latin, gregorian, note;
    private TextView title;
    private final TextView[] cells = new TextView[42];
    /** Months between today's BS month and the one in the grid. */
    private int offset;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L);
        }
    };

    CalendarPanel(Context context, boolean dream) {
        super(context);
        this.dream = dream;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(40), dp(24), dp(40), dp(24));
        setBackgroundColor(BACKGROUND);
        info = new LinearLayout(context);
        info.setOrientation(VERTICAL);
        info.setGravity(Gravity.CENTER);
        addView(info, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        greeting = line(dream ? 28 : 22, ACCENT);
        clock = line(dream ? 92 : 64, TEXT);
        nepali = line(dream ? 34 : 26, TEXT);
        latin = line(dream ? 24 : 20, SOFT);
        gregorian = line(dream ? 24 : 20, SOFT);
        note = line(14, DIM);
        note.setPadding(0, dp(16), 0, 0);
        if (!dream) addView(grid(context), new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    /** The card screen adds its buttons under the dates. */
    void addActions(View actions) { info.addView(actions); }

    private TextView line(int size, int color) {
        TextView text = text(size, color);
        text.setMaxLines(2);
        text.setPadding(0, dp(4), 0, dp(4));
        info.addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return text;
    }

    private TextView text(int size, int color) {
        TextView text = new TextView(getContext());
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER);
        text.setIncludeFontPadding(false);
        return text;
    }

    private View grid(Context context) {
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(VERTICAL);
        grid.setPadding(dp(24), 0, 0, 0);
        title = text(22, TEXT);
        title.setPadding(0, 0, 0, dp(8));
        grid.addView(title);
        LinearLayout header = row(grid);
        for (int i = 0; i < 7; i++) {
            TextView name = cell(header, 15, i == 0 || i == 6 ? HOLIDAY : DIM, dp(28));
            name.setText(NepaliDate.WEEKDAYS_SHORT[i]);
        }
        for (int r = 0; r < 6; r++) {
            LinearLayout week = row(grid);
            for (int c = 0; c < 7; c++) cells[r * 7 + c] = cell(week, 22, TEXT, dp(46));
        }
        return grid;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(getContext());
        row.setBaselineAligned(false); // Empty cells would otherwise push their row down.
        parent.addView(row);
        return row;
    }

    private TextView cell(LinearLayout row, int size, int color, int height) {
        TextView cell = text(size, color);
        LayoutParams params = new LayoutParams(dp(58), height);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(cell, params);
        return cell;
    }

    int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    void start() {
        handler.removeCallbacks(tick);
        tick.run();
    }

    void stop() { handler.removeCallbacksAndMessages(null); }

    /** Moves the grid by whole months; 0 returns to today's month. */
    void showMonth(int delta) {
        offset = delta == 0 ? 0 : offset + delta;
        refresh();
    }

    private void refresh() {
        ZonedDateTime now = ZonedDateTime.now();
        Locale locale = Locale.getDefault();
        LocalDate date = now.toLocalDate();
        BikramSambat today = BikramSambat.of(date);
        clock.setText(DateTimeFormatter.ofPattern(
                android.text.format.DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm a", locale).format(now));
        gregorian.setText(DateTimeFormatter.ofPattern(
                android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy"), locale).format(now));
        if (today == null || !BikramSambat.supported(today.year)) {
            nepali.setVisibility(View.GONE);
            latin.setVisibility(View.GONE);
            greeting.setVisibility(View.GONE);
            note.setText(getContext().getString(R.string.out_of_range, BikramSambat.FIRST_YEAR, BikramSambat.LAST_YEAR));
            note.setVisibility(View.VISIBLE);
            if (title != null) title.setText("");
            for (TextView cell : cells) if (cell != null) { cell.setText(""); cell.setBackground(null); }
            return;
        }
        nepali.setText(NepaliDate.text(today, date));
        latin.setText(NepaliDate.latin(today));
        nepali.setVisibility(View.VISIBLE);
        latin.setVisibility(View.VISIBLE);
        int day = NepaliDate.nationalDay(today);
        greeting.setText(day == 0 ? "" : getResources().getStringArray(R.array.national_days)[day - 1]);
        greeting.setVisibility(day == 0 ? View.GONE : View.VISIBLE);
        note.setVisibility(View.GONE);
        if (!dream) month(today);
        // Drift in a padded area, keeping all content inside the full-screen panel.
        if (dream) info.setTranslationY(((now.getMinute() % 5) - 2) * dp(8));
    }

    private void month(BikramSambat today) {
        BikramSambat current = new BikramSambat(today.year, today.month, 1);
        // Past either end of the table, by Next/Previous or by today moving: stay on the edge month that has data.
        offset = current.clampMonths(offset);
        BikramSambat first = current.plusMonths(offset);
        title.setText(NepaliDate.monthTitle(first));
        LocalDate start = first.toGregorian();
        int lead = NepaliDate.weekday(start), length = BikramSambat.daysInMonth(first.year, first.month);
        for (int i = 0; i < cells.length; i++) {
            TextView cell = cells[i];
            int d = i - lead + 1;
            boolean inMonth = d >= 1 && d <= length;
            cell.setVisibility(i >= 35 && lead + length <= 35 ? View.GONE : View.VISIBLE);
            if (!inMonth) { cell.setText(""); cell.setBackground(null); continue; }
            LocalDate date = start.plusDays(d - 1);
            BikramSambat bs = new BikramSambat(first.year, first.month, d);
            SpannableStringBuilder label = new SpannableStringBuilder(NepaliDate.digits(d));
            int split = label.length();
            label.append('\n').append(Integer.toString(date.getDayOfMonth()));
            label.setSpan(new RelativeSizeSpan(0.5f), split, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            cell.setText(label);
            cell.setTextColor(NepaliDate.weekend(date) || NepaliDate.nationalDay(bs) != 0 ? HOLIDAY : TEXT);
            if (bs.equals(today)) {
                GradientDrawable mark = new GradientDrawable();
                mark.setColor(TODAY);
                mark.setCornerRadius(dp(8));
                cell.setBackground(mark);
            } else {
                cell.setBackground(null);
            }
        }
    }
}
