package io.github.yourvoldemort.panchang;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import java.text.DateFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The whole month at a glance, a tithi and nakshatra in every cell. OK on a day returns it to the
 * card; Channel Up/Down and the buttons page by month. The day screen stays the place to pick a
 * city or region.
 */
public final class MonthActivity extends Activity {
    static final String EXTRA_CITY = "city", EXTRA_REGION = "region", EXTRA_DATE = "date";

    private City city;
    private Region region;
    private MonthPlan plan;
    private LinearLayout grid;
    private TextView era, title, range, status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        city = City.cityNamed(getIntent().getStringExtra(EXTRA_CITY));
        region = Region.named(getIntent().getStringExtra(EXTRA_REGION));
        LocalDate date = LocalDate.parse(getIntent().getStringExtra(EXTRA_DATE));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(16), dp(28), dp(16));
        root.setBackgroundColor(PanchangPanel.BACKGROUND);

        era = text(16, PanchangPanel.ACCENT);
        era.setSingleLine(true);
        title = text(30, PanchangPanel.TEXT);
        title.setSingleLine(true);
        range = text(14, PanchangPanel.DIM);
        range.setSingleLine(true);
        root.addView(era);
        root.addView(title);
        root.addView(range);

        LinearLayout weekdays = new LinearLayout(this);
        weekdays.setGravity(Gravity.CENTER);
        weekdays.setPadding(0, dp(10), 0, dp(2));
        String[] names = DateFormatSymbols.getInstance(Locale.getDefault()).getShortWeekdays();
        for (int i = 1; i <= 7; i++) {
            TextView day = text(13, PanchangPanel.DIM);
            day.setText(names[i]);
            weekdays.addView(day, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        }
        root.addView(weekdays, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBaselineAligned(false);
        root.addView(grid, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));

        status = text(15, PanchangPanel.SOFT);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setPadding(0, dp(8), 0, 0);
        root.addView(status, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(10), 0, 0);
        button(actions, R.string.previous).setOnClickListener(v -> showMonth(-1));
        button(actions, R.string.today).setOnClickListener(v -> show(LocalDate.now(city.zone)));
        button(actions, R.string.next).setOnClickListener(v -> showMonth(1));
        button(actions, R.string.day).setOnClickListener(v -> finish());
        root.addView(actions);

        setContentView(root);
        show(date);
    }

    /** Draws the month holding that date and focuses its cell. */
    private void show(LocalDate date) {
        plan = MonthPlan.of(date, city, region);
        int year = region.year(plan.span);
        era.setText(getString(R.string.heading, region.label, region.era, region.digits(year)));
        title.setText((plan.span.adhika ? region.adhika : "") + region.month(plan.span));
        range.setText(getString(R.string.month_range, shortDate(plan.first), shortDate(plan.last)));

        grid.removeAllViews();
        View focus = null;
        for (int row = 0; row < MonthPlan.CELLS / 7; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER);
            for (int column = 0; column < 7; column++) {
                MonthPlan.Cell cell = plan.cells[row * 7 + column];
                View view = cell == null ? new View(this) : cellView(cell);
                view.setFocusable(cell != null);
                view.setClickable(cell != null);
                if (cell != null && cell.date.equals(date)) focus = view;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                        LayoutParams.MATCH_PARENT, 1);
                params.setMargins(dp(1), dp(1), dp(1), dp(1));
                line.addView(view, params);
            }
            grid.addView(line, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, 0, 1f));
        }
        if (focus != null) {
            View target = focus;
            target.post(target::requestFocus);
        }
        showStatus(date, plan);
    }

    private View cellView(MonthPlan.Cell cell) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(cellBackground());
        box.setOnClickListener(v -> open(cell.date));
        box.setOnFocusChangeListener((v, focused) -> { if (focused) showStatus(cell.date, plan); });

        TextView number = cellText(19, PanchangPanel.TEXT);
        number.setText(region.digits(cell.dayNumber));
        TextView tithi = cellText(10, cell.marked() ? PanchangPanel.ACCENT : PanchangPanel.SOFT);
        tithi.setText(Panchang.tithiLabel(cell.tithi));
        TextView nakshatra = cellText(10, PanchangPanel.DIM);
        nakshatra.setText(Panchang.NAKSHATRA[cell.nakshatra - 1]);
        box.addView(number);
        box.addView(tithi);
        box.addView(nakshatra);
        return box;
    }

    /** The line under the grid: the full date, the month's day number, the limbs and the mansion. */
    private void showStatus(LocalDate date, MonthPlan month) {
        for (MonthPlan.Cell cell : month.cells) {
            if (cell == null || !cell.date.equals(date)) continue;
            status.setText(getString(R.string.month_day, longDate(date),
                    region.monthLatin(month.span) + " " + region.digits(cell.dayNumber),
                    Panchang.tithiLabel(cell.tithi), Panchang.NAKSHATRA[cell.nakshatra - 1]));
            return;
        }
    }

    private void showMonth(int delta) {
        LocalDate edge = delta < 0 ? plan.first : plan.last;
        show(edge.plusDays(delta < 0 ? -1 : 1));
    }

    private void open(LocalDate date) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_DATE, date.toString()));
        finish();
    }

    private String shortDate(LocalDate date) {
        Locale locale = Locale.getDefault();
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMyyyy");
        return DateTimeFormatter.ofPattern(pattern, locale).format(date);
    }

    private String longDate(LocalDate date) {
        Locale locale = Locale.getDefault();
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy");
        return DateTimeFormatter.ofPattern(pattern, locale).format(date);
    }

    private TextView text(int size, int color) {
        TextView text = new TextView(this);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER);
        text.setIncludeFontPadding(false);
        return text;
    }

    /** A cell's own text turns dark while the focused background is gold. */
    private TextView cellText(int size, int normal) {
        TextView text = text(size, normal);
        text.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{PanchangPanel.BACKGROUND, normal}));
        text.setDuplicateParentStateEnabled(true);
        text.setSingleLine(true);
        return text;
    }

    private StateListDrawable cellBackground() {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, shape(PanchangPanel.ACCENT));
        background.addState(new int[0], shape(0x1affffff));
        return background;
    }

    private Button button(LinearLayout parent, int label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, shape(PanchangPanel.ACCENT));
        background.addState(new int[0], shape(0x33ffffff));
        button.setBackground(background);
        button.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{PanchangPanel.BACKGROUND, PanchangPanel.TEXT}));
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(params);
        parent.addView(button);
        return button;
    }

    private GradientDrawable shape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(6));
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Channel and media keys page the month from anywhere on the screen. */
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        switch (code) {
            case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_PAGE_UP: case KeyEvent.KEYCODE_MEDIA_REWIND:
                showMonth(-1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN: case KeyEvent.KEYCODE_PAGE_DOWN: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                showMonth(1);
                return true;
            default:
                return super.onKeyDown(code, event);
        }
    }
}