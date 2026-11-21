package io.github.gagantk.kannadacalendar;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A standalone module card drawn as a wall calendar hanging on its nail: the month as the page, and down its right
 * side the whole of the day the highlight is on, with its festivals as a list marked by kind and its holiday, if
 * any. It comes in a light or a dark look, chosen in the band and remembered. The calendar is the owner's own
 * Bangalore Press copy, downloaded from the link set with setup.sh, at most once a month or when the saved copy no
 * longer reaches today.
 */
public final class CalendarActivity extends Activity {
    private static final String PREFS = "kannadacalendar", KEY_TRY = "last_try", KEY_DARK = "dark";
    private static final long RETRY_MS = 3600_000L;
    /** Which band control to give focus back to after the screen is rebuilt for a new look. */
    private static int refocus;

    private Palette p;
    private Book book;
    private boolean busy, alive, updatedNow;
    private String error = "";
    /** The calendar link pushed with setup.sh, read when the card comes to the front. */
    private String link;
    private MonthView page;
    private ScrollView festScroll;
    private LinearLayout festList;
    private TextView monthName, monthYear, lunar, status, weekday, bigDate, todayTag, header, holiday, festHead, message, moreHint;
    private Tile tithiTile, nakTile, rahuTile;

    /** One of the three facts of the day: an icon and a label over a name, with the time under it. */
    private final class Tile {
        final LinearLayout box;
        final TextView label, name, time, extra;

        Tile(String icon, String text) {
            box = new LinearLayout(CalendarActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(p.tile);
            bg.setCornerRadius(Ui.dp(CalendarActivity.this, 10));
            box.setBackground(bg);
            box.setPadding(Ui.dp(CalendarActivity.this, 10), Ui.dp(CalendarActivity.this, 5),
                    Ui.dp(CalendarActivity.this, 8), Ui.dp(CalendarActivity.this, 5));
            label = Ui.text(CalendarActivity.this, 10, p.muted, true);
            label.setLetterSpacing(0.06f);
            // One line that shrinks (setSingleLine would let it scroll sideways, and it would never shrink).
            label.setMaxLines(1);
            // In dp, not sp, and shrinking to fit: large text on the TV must not cut "NAKSHATRA" short.
            label.setAutoSizeTextTypeUniformWithConfiguration(7, 10, 1, TypedValue.COMPLEX_UNIT_DIP);
            // A TV without a colour emoji font would show an empty box for the icon.
            label.setText(new android.graphics.Paint().hasGlyph(icon) ? icon + " " + text : text);
            name = Ui.text(CalendarActivity.this, 14, p.ink, true);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            // Long names (Uttarabhadra, Trayodashi) shrink to fit the tile before they are cut.
            name.setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, TypedValue.COMPLEX_UNIT_SP);
            name.setPadding(0, Ui.dp(CalendarActivity.this, 2), 0, 0);
            time = Ui.text(CalendarActivity.this, 11, p.accent, false);
            time.setMaxLines(1);
            time.setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, TypedValue.COMPLEX_UNIT_SP);
            extra = Ui.text(CalendarActivity.this, 11, p.muted, false);
            extra.setSingleLine(true);
            extra.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            box.addView(label, wide);
            box.addView(name);
            box.addView(time, new LinearLayout.LayoutParams(wide));
            box.addView(extra);
        }

        void set(String n, String t, String e) {
            name.setText(n.isEmpty() ? "—" : n);
            time.setText(t);
            time.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);
            extra.setText(e);
            extra.setVisibility(e.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Palette.current = prefs.getBoolean(KEY_DARK, false) ? Palette.DARK : Palette.LIGHT;
        p = Palette.current;
        alive = true;
        // Also makes the app's folder on shared storage, where setup.sh puts the link.
        link = Cache.link(this);

        FrameLayout root = new FrameLayout(this);
        root.addView(new Wall.Back(this));
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(Wall.paper(this));
        sheet.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        sheet.setElevation(Ui.dp(this, 18));
        sheet.setPadding(Ui.dp(this, 22), Ui.dp(this, 24), Ui.dp(this, 22), Ui.dp(this, 14));
        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        sheetParams.setMargins(Ui.dp(this, Wall.SIDE), Ui.dp(this, Wall.TOP), Ui.dp(this, Wall.SIDE), Ui.dp(this, Wall.BOTTOM));
        root.addView(sheet, sheetParams);
        Wall.Rings rings = new Wall.Rings(this);
        rings.setElevation(Ui.dp(this, 20));
        root.addView(rings);

        sheet.addView(band());
        LinearLayout body = new LinearLayout(this);
        body.setPadding(0, Ui.dp(this, 6), 0, 0);
        page = new MonthView(this);
        page.setId(R.id.page);
        body.addView(page, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 15f));
        View rule = new View(this);
        rule.setBackgroundColor(p.line);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(Math.max(1, Ui.dp(this, 1)), ViewGroup.LayoutParams.MATCH_PARENT);
        ruleParams.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 18), 0);
        body.addView(rule, ruleParams);
        body.addView(panel(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 10f));
        sheet.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        book = Cache.load(this);
        page.setBook(book);
        page.setListener(this::showDay);
        page.setOnEnter(() -> {
            if (festList.getChildCount() > 0) festScroll.requestFocus();
        });
        LocalDate start = LocalDate.now();
        if (state != null && state.getString("day") != null) start = LocalDate.parse(state.getString("day"));
        page.select(start);
        View back = refocus == 0 ? null : root.findViewById(refocus);
        refocus = 0;
        if (back != null) back.requestFocus();
        else page.requestFocus();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("day", page.selected().toString());
    }

    /**
     * The band printed across the top: month and year with the arrows that turn the page, the lunar months and
     * ritus the page spans, and the small controls; a line under them only while an update runs or has failed.
     */
    private View band() {
        LinearLayout band = new LinearLayout(this);
        band.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable red = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{p.bandTop, p.bandBottom});
        red.setCornerRadius(Ui.dp(this, 3));
        band.setBackground(red);
        band.setPadding(Ui.dp(this, 26), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4));

        monthName = Ui.text(this, 32, p.bandText, true);
        monthName.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        monthYear = Ui.text(this, 32, p.bandSub, false);
        monthYear.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        monthYear.setPadding(Ui.dp(this, 14), 0, 0, 0);
        band.addView(monthName);
        band.addView(monthYear);
        // Turn the page without Channel keys, which not every remote has.
        ImageButton prev = icon(R.drawable.prev, getString(R.string.prev_month));
        prev.setOnClickListener(v -> page.select(page.selected().minusMonths(1)));
        LinearLayout.LayoutParams pp = iconParams();
        pp.setMarginStart(Ui.dp(this, 16));
        band.addView(prev, pp);
        ImageButton next = icon(R.drawable.next, getString(R.string.next_month));
        next.setOnClickListener(v -> page.select(page.selected().plusMonths(1)));
        band.addView(next, iconParams());

        lunar = Ui.text(this, 14, p.bandSub, false);
        lunar.setMaxLines(2);
        lunar.setGravity(Gravity.END);
        lunar.setLineSpacing(Ui.dp(this, 2), 1f);
        band.addView(lunar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View divider = new View(this);
        divider.setBackgroundColor(0x55FFFFFF);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Math.max(1, Ui.dp(this, 1)), Ui.dp(this, 40));
        dp.setMargins(Ui.dp(this, 20), 0, Ui.dp(this, 14), 0);
        band.addView(divider, dp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.END);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView today = chip(getString(R.string.today));
        today.setOnClickListener(v -> {
            page.select(LocalDate.now());
            page.requestFocus();
        });
        row.addView(today);
        TextView upcoming = chip(getString(R.string.upcoming));
        upcoming.setOnClickListener(v -> upcoming());
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        up.setMarginStart(Ui.dp(this, 8));
        row.addView(upcoming, up);
        ImageButton theme = icon(p.dark ? R.drawable.sun : R.drawable.moon,
                p.dark ? "Light look" : "Dark look");
        theme.setId(R.id.look);
        theme.setOnClickListener(v -> toggle(KEY_DARK, R.id.look));
        row.addView(theme, iconParams());
        ImageButton update = icon(R.drawable.refresh, getString(R.string.update));
        update.setOnClickListener(v -> refresh(true));
        row.addView(update, iconParams());
        ImageButton info = icon(R.drawable.info, getString(R.string.about));
        info.setOnClickListener(v -> about());
        row.addView(info, iconParams());
        controls.addView(row);
        status = Ui.text(this, 11, (p.bandSub & 0x00FFFFFF) | 0xCC000000, false);
        status.setGravity(Gravity.END);
        status.setPadding(0, Ui.dp(this, 4), 0, 0);
        controls.addView(status);
        band.addView(controls);
        bandControls = controls;
        // A narrow screen or large text: the lunar months give way first, then the month shrinks, so that every
        // control stays on the screen.
        band.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> band.post(() -> fitBand(band)));
        return band;
    }

    private View bandControls;
    private boolean lunarFits = true;

    private void fitBand(View band) {
        if (lunarFits && lunar.getWidth() < Ui.dp(this, 110)) {
            lunarFits = false;
            lunar.setText("");
            lunar.setVisibility(View.INVISIBLE);
        } else if (bandControls.getRight() > band.getWidth() - band.getPaddingRight()
                && monthName.getTextSize() > Ui.dp(this, 20)) {
            monthName.setTextSize(TypedValue.COMPLEX_UNIT_PX, monthName.getTextSize() * 0.9f);
            monthYear.setTextSize(TypedValue.COMPLEX_UNIT_PX, monthYear.getTextSize() * 0.9f);
        }
    }

    /**
     * The ⓘ card: the remote keys on the left; on the right what the dot colours mean, where the calendar comes
     * from and how recent the saved copy is, and who made the add-on. OK or Back closes it.
     */
    private void about() {
        Dialog dialog = new Dialog(this);
        LinearLayout card = card(R.string.about_title);
        LinearLayout columns = new LinearLayout(this);
        LinearLayout left = new LinearLayout(this), right = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        right.setOrientation(LinearLayout.VERTICAL);
        columns.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rp.setMarginStart(Ui.dp(this, 28));
        columns.addView(right, rp);
        card.addView(columns);

        left.addView(aboutHead(R.string.about_keys));
        String[][] keys = {
                {"◀  ▶", getString(R.string.key_day)},
                {"▲  ▼", getString(R.string.key_week)},
                {"0 – 9", getString(R.string.key_digits)},
                {"OK", getString(R.string.key_ok)},
                {"‹  ›", getString(R.string.key_month)},
                {"Back", getString(R.string.key_back)},
        };
        for (String[] k : keys) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
            TextView key = Ui.text(this, 12, p.accent, true);
            key.setText(k[0]);
            row.addView(key, new LinearLayout.LayoutParams(Ui.dp(this, 76), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView what = Ui.text(this, 12, p.ink, false);
            what.setText(k[1]);
            row.addView(what, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            left.addView(row);
        }

        right.addView(aboutHead(R.string.about_dots));
        right.addView(legend());

        right.addView(aboutHead(R.string.about_source));
        TextView source = Ui.text(this, 12, p.ink, false);
        String saved = "";
        if (book != null && Cache.file(this).isFile()) {
            saved = " " + getString(R.string.about_saved, shortDate(Cache.file(this).lastModified()),
                    book.last() == null ? "—" : book.last().getDayOfMonth() + " "
                            + book.last().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + book.last().getYear());
        }
        source.setText(getString(R.string.about_source_text) + saved);
        source.setLineSpacing(Ui.dp(this, 2), 1f);
        right.addView(source);

        right.addView(aboutHead(R.string.about_made));
        TextView made = Ui.text(this, 12, p.ink, false);
        made.setText(R.string.about_made_text);
        right.addView(made);

        TextView close = closeButton(dialog);
        card.addView(close, closeParams());
        show(dialog, card, 760, close);
    }

    /** A card on the paper colours with a serif title, for the ⓘ and Upcoming dialogs. */
    private LinearLayout card(int title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.paperTop);
        bg.setStroke(Math.max(1, Ui.dp(this, 1)), p.line);
        bg.setCornerRadius(Ui.dp(this, 12));
        card.setBackground(bg);
        card.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 14));
        TextView t = Ui.text(this, 20, p.ink, true);
        t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        t.setText(title);
        card.addView(t);
        return card;
    }

    private LinearLayout.LayoutParams closeParams() {
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.END;
        cp.topMargin = Ui.dp(this, 12);
        return cp;
    }

    private void show(Dialog dialog, View card, int widthDp, View first) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(card);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            // Never wider than the screen leaves room for, on a TV set to a large display size.
            int most = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            w.setLayout(Math.min(Ui.dp(this, widthDp), most), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setOnShowListener(d -> first.requestFocus());
        dialog.show();
    }

    private static String shortDate(long millis) {
        LocalDate d = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        return d.getDayOfMonth() + " " + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    private TextView closeButton(Dialog dialog) {
        TextView close = new TextView(this);
        close.setText(R.string.close);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        close.setFocusable(true);
        close.setPadding(Ui.dp(this, 16), Ui.dp(this, 5), Ui.dp(this, 16), Ui.dp(this, 5));
        GradientDrawable on = new GradientDrawable();
        on.setColor(p.bandTop);
        on.setCornerRadius(Ui.dp(this, 14));
        GradientDrawable off = new GradientDrawable();
        off.setStroke(Math.max(1, Ui.dp(this, 1)), p.muted);
        off.setCornerRadius(Ui.dp(this, 14));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, on);
        states.addState(new int[0], off);
        close.setBackground(states);
        close.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{p.bandText, p.ink}));
        close.setOnClickListener(v -> dialog.dismiss());
        return close;
    }

    /** The focus look of a row in the dialogs: a soft fill and a saffron edge, as on the page. */
    private StateListDrawable rowFocus() {
        GradientDrawable on = new GradientDrawable();
        on.setColor(p.focusFill);
        on.setStroke(Ui.dp(this, 2), p.saffron);
        on.setCornerRadius(Ui.dp(this, 6));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[0], new ColorDrawable(0));
        return s;
    }

    /**
     * Upcoming: on the left the holidays and main festivals of the next 30 days, on the right every public
     * holiday from today to the end of the saved calendar. OK on a line goes to that day.
     */
    private void upcoming() {
        Dialog dialog = new Dialog(this);
        LinearLayout card = card(R.string.upcoming_title);
        LinearLayout columns = new LinearLayout(this);
        LinearLayout soon = new LinearLayout(this), holidays = new LinearLayout(this);
        soon.setOrientation(LinearLayout.VERTICAL);
        holidays.setOrientation(LinearLayout.VERTICAL);
        LocalDate today = LocalDate.now();
        int max = Ui.dp(this, 330);
        columns.addView(column(R.string.upcoming_soon, soon, max), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 11f));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 9f);
        hp.setMarginStart(Ui.dp(this, 24));
        columns.addView(column(R.string.upcoming_holidays, holidays, max), hp);
        card.addView(columns);

        View first = null;
        if (book != null) {
            for (LocalDate d = today; book.last() != null && !d.isAfter(book.last()); d = d.plusDays(1)) {
                Day day = book.day(d);
                if (d.isBefore(today.plusDays(30))) {
                    Festival.Ranked r = Festival.ranked(day);
                    List<String> main = new ArrayList<>(r.names.subList(0, r.holidays));
                    for (String n : r.names.subList(r.holidays, r.names.size())) {
                        if (Festival.kind(n, day) == Festival.Kind.FESTIVAL) main.add(n);
                    }
                    if (!main.isEmpty()) {
                        View row = upcomingRow(dialog, d, TextUtils.join(", ", main), r.holidays > 0);
                        soon.addView(row);
                        if (first == null) first = row;
                    }
                }
                if (!day.publicHolidays.isEmpty()) {
                    holidays.addView(upcomingRow(dialog, d, TextUtils.join(", ", Festival.shown(day.publicHolidays)), true));
                }
            }
        }
        if (soon.getChildCount() == 0) soon.addView(note(R.string.upcoming_none));
        if (holidays.getChildCount() == 0) holidays.addView(note(R.string.upcoming_none));

        TextView close = closeButton(dialog);
        card.addView(close, closeParams());
        show(dialog, card, 820, first != null ? first : close);
    }

    private View column(int title, LinearLayout list, int maxHeight) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(aboutHead(title));
        ScrollView scroll = new ScrollView(this) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Ui.dp(this, 20));
        scroll.addView(list);
        col.addView(scroll);
        return col;
    }

    private TextView note(int text) {
        TextView t = Ui.text(this, 12, p.muted, false);
        t.setText(text);
        return t;
    }

    /** One line of Upcoming: "Sat 15 Jan" and the names; a public holiday in red. OK goes to the day. */
    private View upcomingRow(Dialog dialog, LocalDate d, String names, boolean holiday) {
        LinearLayout row = new LinearLayout(this);
        row.setFocusable(true);
        row.setBackground(rowFocus());
        row.setPadding(Ui.dp(this, 6), Ui.dp(this, 3), Ui.dp(this, 6), Ui.dp(this, 3));
        TextView date = Ui.text(this, 12, holiday ? p.red : p.muted, true);
        date.setText(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.getDayOfMonth() + " "
                + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
        row.addView(date, new LinearLayout.LayoutParams(Ui.dp(this, 82), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView what = Ui.text(this, 13, holiday ? p.red : p.ink, holiday);
        what.setText(names);
        what.setMaxLines(2);
        what.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(what, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(v -> {
            dialog.dismiss();
            page.select(d);
            page.requestFocus();
        });
        return row;
    }

    private TextView aboutHead(int text) {
        TextView t = Ui.text(this, 11, p.red, true);
        t.setLetterSpacing(0.14f);
        t.setText(getString(text).toUpperCase(Locale.ENGLISH));
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        return t;
    }

    private void toggle(String key, int id) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply();
        refocus = id;
        recreate();
    }

    private LinearLayout.LayoutParams iconParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28));
        lp.setMarginStart(Ui.dp(this, 8));
        return lp;
    }

    private ColorStateList chipText() {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{p.bandBottom, p.bandText});
    }

    private TextView chip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setFocusable(true);
        chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        chip.setBackground(pill());
        chip.setTextColor(chipText());
        chip.setNextFocusDownId(R.id.page);
        return chip;
    }

    private ImageButton icon(int drawable, String description) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(drawable);
        b.setContentDescription(description);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
        b.setBackground(pill());
        b.setImageTintList(chipText());
        b.setNextFocusDownId(R.id.page);
        return b;
    }

    /** A small pill: outlined on the red band, filled when the remote is on it. */
    private StateListDrawable pill() {
        GradientDrawable on = new GradientDrawable();
        on.setColor(p.bandText);
        on.setCornerRadius(Ui.dp(this, 14));
        GradientDrawable off = new GradientDrawable();
        off.setColor(0x22FFFFFF);
        off.setStroke(Math.max(1, Ui.dp(this, 1)), 0x88FFFFFF);
        off.setCornerRadius(Ui.dp(this, 14));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[0], off);
        return s;
    }

    /** The column down the right of the page: the day in full. */
    private View panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, Ui.dp(this, 2), Ui.dp(this, 4), 0);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        bigDate = Ui.text(this, 44, p.ink, true);
        bigDate.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        bigDate.setIncludeFontPadding(false);
        top.addView(bigDate);
        // Beside the date: the weekday, and the masa and paksha (the month and year are in the band already).
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(Ui.dp(this, 14), 0, 0, 0);
        weekday = Ui.text(this, 14, p.muted, true);
        weekday.setLetterSpacing(0.15f);
        // "TODAY" beside the weekday when the highlight is on today.
        todayTag = Ui.text(this, 10, 0xFFFFFFFF, true);
        todayTag.setText(getString(R.string.today).toUpperCase(Locale.ENGLISH));
        todayTag.setLetterSpacing(0.1f);
        todayTag.setPadding(Ui.dp(this, 7), Ui.dp(this, 1), Ui.dp(this, 7), Ui.dp(this, 1));
        GradientDrawable tagBg = new GradientDrawable();
        tagBg.setColor(p.today);
        tagBg.setCornerRadius(Ui.dp(this, 8));
        todayTag.setBackground(tagBg);
        LinearLayout dayRow = new LinearLayout(this);
        dayRow.setGravity(Gravity.CENTER_VERTICAL);
        dayRow.addView(weekday);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMarginStart(Ui.dp(this, 10));
        dayRow.addView(todayTag, tp);
        header = Ui.text(this, 14, p.accent, true);
        header.setMaxLines(1);
        header.setEllipsize(TextUtils.TruncateAt.END);
        // "Karthika Maasa · Krishna Paksha" shrinks a little rather than lose its end.
        header.setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        header.setPadding(0, Ui.dp(this, 2), 0, 0);
        side.addView(dayRow);
        side.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        top.addView(side, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(top);

        holiday = Ui.text(this, 12, p.red, true);
        holiday.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 2));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(0, Ui.dp(this, 6), 0, 0);
        panel.addView(holiday, hp);

        LinearLayout tiles = new LinearLayout(this);
        tiles.setPadding(0, Ui.dp(this, 8), 0, 0);
        tithiTile = new Tile("🌙", getString(R.string.tithi).toUpperCase(Locale.ENGLISH));
        nakTile = new Tile("✨", getString(R.string.nakshatra).toUpperCase(Locale.ENGLISH));
        rahuTile = new Tile("⏳", getString(R.string.rahu).toUpperCase(Locale.ENGLISH));
        Tile[] all = {tithiTile, nakTile, rahuTile};
        for (int i = 0; i < all.length; i++) {
            // All three as tall as the tallest: Rahukalam has no "next day" line under its time.
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.setMarginStart(Ui.dp(this, 6));
            tiles.addView(all[i].box, lp);
        }
        panel.addView(tiles);

        festHead = Ui.text(this, 12, p.red, true);
        festHead.setLetterSpacing(0.14f);
        festHead.setText(getString(R.string.festivals).toUpperCase(Locale.ENGLISH));
        festHead.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        panel.addView(festHead);
        message = Ui.text(this, 14, p.muted, false);
        panel.addView(message);

        // The list takes the rest of the column. OK on a day brings the remote here to scroll a long one; Left or
        // Back returns to the page.
        festScroll = new ScrollView(this);
        festScroll.setFocusable(true);
        // Also after a tap on the page, where a touch panel or an air mouse has put the TV in touch mode.
        festScroll.setFocusableInTouchMode(true);
        festScroll.setVerticalFadingEdgeEnabled(true);
        festScroll.setFadingEdgeLength(Ui.dp(this, 24));
        GradientDrawable ring = new GradientDrawable();
        ring.setStroke(Ui.dp(this, 2), p.saffron);
        ring.setCornerRadius(Ui.dp(this, 8));
        StateListDrawable focusRing = new StateListDrawable();
        focusRing.addState(new int[]{android.R.attr.state_focused}, ring);
        focusRing.addState(new int[0], new ColorDrawable(0));
        festScroll.setBackground(focusRing);
        festScroll.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
        festScroll.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_BACK)) {
                page.requestFocus();
                return true;
            }
            return false;
        });
        festScroll.setOnFocusChangeListener((v, has) -> updateHint());
        festList = new LinearLayout(this);
        festList.setOrientation(LinearLayout.VERTICAL);
        // Count what is below the fold only once the new list has been laid out.
        festList.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateHint());
        festScroll.addView(festList);
        panel.addView(festScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        moreHint = Ui.text(this, 11, p.accent, false);
        moreHint.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), 0, 0);
        panel.addView(moreHint);
        return panel;
    }

    private static int dotColour(Festival.Kind kind) {
        switch (kind) {
            case FESTIVAL: return 0xFFD0201A;
            case TEMPLE: return 0xFF8E5BB5;
            case SAINT: return 0xFFE8793A;
            case VRATA: return 0xFFC9A227;
            case DAY: return 0xFF4F8AA0;
            default: return 0xFF9A8A74;
        }
    }

    private String kindName(Festival.Kind kind) {
        switch (kind) {
            case FESTIVAL: return getString(R.string.kind_festival);
            case TEMPLE: return getString(R.string.kind_temple);
            case SAINT: return getString(R.string.kind_saint);
            case VRATA: return getString(R.string.kind_vrata);
            case DAY: return getString(R.string.kind_day);
            default: return getString(R.string.kind_other);
        }
    }

    private GradientDrawable dot(int colour, int size) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(colour);
        d.setSize(size, size);
        return d;
    }

    /**
     * One festival: a dot in the colour of its kind, then the full name, wrapping to a second line if it must; the
     * day's public holiday in bold.
     */
    private TextView entry(String name, Day day, boolean holiday) {
        TextView v = new TextView(this);
        v.setText(name);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setTextColor(holiday ? p.red : p.ink);
        if (holiday) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setMaxLines(2);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        v.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(dotColour(Festival.kind(name, day)), Ui.dp(this, 8)), null, null, null);
        v.setCompoundDrawablePadding(Ui.dp(this, 10));
        return v;
    }

    /** Says how many festivals are below the fold, and how to reach them. */
    private void updateHint() {
        festScroll.post(() -> {
            int room = festScroll.getHeight() - festScroll.getPaddingTop() - festScroll.getPaddingBottom();
            if (festList.getHeight() <= room) {
                moreHint.setVisibility(View.GONE);
                return;
            }
            int below = 0;
            int bottom = festScroll.getScrollY() + room;
            for (int i = 0; i < festList.getChildCount(); i++) if (festList.getChildAt(i).getBottom() > bottom) below++;
            moreHint.setVisibility(View.VISIBLE);
            if (festScroll.hasFocus()) {
                moreHint.setText(getString(R.string.list_focused));
            } else {
                moreHint.setText(getResources().getQuantityString(R.plurals.more_below, below, below));
            }
        });
    }

    /** What the dot colours mean, for the ⓘ card. */
    private View legend() {
        Flow row = new Flow(this, 6, 3);
        for (Festival.Kind k : Festival.Kind.values()) {
            TextView v = Ui.text(this, 12, p.ink, false);
            v.setText(kindName(k));
            v.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(dotColour(k), Ui.dp(this, 8)), null, null, null);
            v.setCompoundDrawablePadding(Ui.dp(this, 6));
            v.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
            row.addView(v);
        }
        return row;
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_DATE_CHANGED);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clock, f);
        // Read once here, not on every key: finding the app's folder on shared storage touches the disk.
        link = Cache.link(this);
        checkDate();
        refresh(false);
    }

    @Override protected void onPause() {
        unregisterReceiver(clock);
        super.onPause();
    }

    @Override protected void onDestroy() {
        alive = false;
        super.onDestroy();
    }

    private String monthName(YearMonth ym) {
        return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private void showDay(LocalDate day) {
        YearMonth ym = YearMonth.from(day);
        monthName.setText(monthName(ym));
        monthYear.setText(Integer.toString(ym.getYear()));
        if (lunarFits) lunar.setText(lunarMonths(ym));
        boolean sunday = day.getDayOfWeek() == DayOfWeek.SUNDAY;
        Day d = book == null ? null : book.day(day);
        boolean any = d != null && !d.isEmpty();
        // A public holiday reads like a Sunday here too.
        boolean red = sunday || (any && !d.publicHolidays.isEmpty());
        weekday.setText(day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ENGLISH));
        weekday.setTextColor(red ? p.red : p.muted);
        bigDate.setText(Integer.toString(day.getDayOfMonth()));
        bigDate.setTextColor(red ? p.red : p.ink);
        todayTag.setVisibility(day.equals(LocalDate.now()) ? View.VISIBLE : View.GONE);

        // The ritu is already in the band across the top; the column keeps the masa and paksha.
        String[] parts = any ? d.header.split(" · ") : new String[0];
        String head = parts.length == 3 ? parts[1] + " · " + parts[2] : any ? d.header : "";
        header.setText(head);
        header.setVisibility(head.isEmpty() ? View.GONE : View.VISIBLE);
        showHoliday(any ? d : null);
        fill(tithiTile, any ? Sunrise.tithi(book, day) : null);
        fill(nakTile, any ? Sunrise.nakshatra(book, day) : null);
        rahu(any ? d.rahu : "");

        festList.removeAllViews();
        festScroll.scrollTo(0, 0);
        int n = 0;
        if (any) {
            // The day's public holiday first, then the main festivals, then the rest as published.
            Festival.Ranked ranked = Festival.ranked(d);
            for (int i = 0; i < ranked.names.size(); i++) festList.addView(entry(ranked.names.get(i), d, i < ranked.holidays));
            n = ranked.names.size();
        }
        festHead.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        updateHint();

        String note;
        if (book == null && link == null) note = getString(R.string.not_configured);
        else if (book == null) note = busy ? getString(R.string.updating) : getString(R.string.no_copy, error);
        else if (!any) note = getString(R.string.no_data);
        else if (n == 0) note = getString(R.string.no_festivals);
        else note = "";
        message.setText(note);
        message.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
        updateStatus();
    }

    /** The publisher's holiday lists: a public holiday as a red label, a restricted one as a quiet outline. */
    private void showHoliday(Day d) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 12));
        if (d != null && !d.publicHolidays.isEmpty()) {
            bg.setColor(p.red);
            holiday.setTextColor(0xFFFFFFFF);
            holiday.setText(getString(R.string.public_holiday, TextUtils.join(", ", Festival.shown(d.publicHolidays))));
        } else if (d != null && !d.restrictedHolidays.isEmpty()) {
            bg.setStroke(Math.max(1, Ui.dp(this, 1)), p.muted);
            holiday.setTextColor(p.muted);
            holiday.setText(getString(R.string.restricted_holiday, TextUtils.join(", ", Festival.shown(d.restrictedHolidays))));
        } else {
            holiday.setVisibility(View.GONE);
            return;
        }
        holiday.setBackground(bg);
        holiday.setVisibility(View.VISIBLE);
    }

    /**
     * A tithi or nakshatra tile: the one in force at sunrise and when it ends, then what follows it the same day
     * or, when it runs past midnight, "next day".
     */
    private void fill(Tile tile, Sunrise.Span span) {
        if (span == null) tile.set("", "", "");
        else tile.set(span.name, zoned(span.till), span.after);
    }

    /**
     * A clock time from the calendar, which is Bengaluru's: marked "IST" when the TV keeps another time zone, so a
     * TV abroad does not pass it off as local time.
     */
    private static String zoned(String time) { return zoned(time, java.time.ZoneId.systemDefault()); }

    static String zoned(String time, java.time.ZoneId tv) {
        java.time.Instant now = java.time.Instant.now();
        boolean india = tv.getRules().getOffset(now).equals(IcsReader.INDIA.getRules().getOffset(now));
        return india || time.chars().noneMatch(Character::isDigit) ? time : time + " IST";
    }

    /** Rahukalam as its start over its end: "9:12 AM" / "to 10:38 AM". */
    private void rahu(String value) {
        if (value.isEmpty()) {
            rahuTile.set("", "", "");
            return;
        }
        int dash = value.indexOf('-');
        if (dash < 0) {
            rahuTile.set(value, "", "");
            return;
        }
        String from = value.substring(0, dash).trim(), to = value.substring(dash + 1).trim();
        rahuTile.set(from, zoned("to " + to), "");
    }

    /**
     * The lunar months (maasas) and ritus the page spans, from the headings of its days: "Ashwayuja · Karthika"
     * over "Sharad · Hemanta Ritu" when the ritu turns during the month.
     */
    private String lunarMonths(YearMonth ym) {
        if (book == null) return "";
        if (ym.equals(lunarMonth) && book == lunarBook) return lunarText;
        lunarMonth = ym;
        lunarBook = book;
        lunarText = lunarMonthsOf(ym);
        return lunarText;
    }

    /** The last month {@link #lunarMonths} worked out, so a key press within the month does not redo it. */
    private YearMonth lunarMonth;
    private Book lunarBook;
    private String lunarText;

    private String lunarMonthsOf(YearMonth ym) {
        List<String> names = new ArrayList<>(), ritus = new ArrayList<>();
        for (int i = 1; i <= ym.lengthOfMonth(); i++) {
            String[] parts = book.day(ym.atDay(i)).header.split(" · ");
            if (parts.length < 3) continue;
            String ritu = parts[0].replaceAll("(?i)\\s+r?i?tu$", "");
            if (!ritus.contains(ritu)) ritus.add(ritu);
            String masa = parts[1].replaceAll("(?i)\\s+maa?sa$", "");
            if (!names.contains(masa)) names.add(masa);
        }
        if (names.isEmpty()) return "";
        return TextUtils.join(" · ", names) + "\n" + TextUtils.join(" · ", ritus) + " Ritu";
    }

    /** The line under the band's controls: only while an update runs, just after one, or when one failed. */
    private void updateStatus() {
        String text;
        if (busy) text = getString(R.string.updating_short);
        else if (!error.isEmpty() && book != null) text = getString(R.string.update_failed_short);
        else if (updatedNow && book != null) text = getString(R.string.updated, shortDate(Cache.file(this).lastModified()));
        else text = "";
        status.setText(text);
        status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** The date shown as today, to notice midnight while the calendar stays open. */
    private LocalDate shownToday = LocalDate.now();

    private final BroadcastReceiver clock = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { checkDate(); }
    };

    /** After midnight: today's badge moves, and a highlight left on yesterday's today follows it. */
    private void checkDate() {
        LocalDate now = LocalDate.now();
        if (now.equals(shownToday)) return;
        boolean follow = page.selected().equals(shownToday);
        shownToday = now;
        if (follow) page.select(now);
        else {
            page.invalidate();
            showDay(page.selected());
        }
        refresh(false);
    }

    /** Downloads the calendar when it is needed (or asked for), once an hour at most when it keeps failing. */
    private void refresh(boolean force) {
        if (busy || link == null) {
            showDay(page.selected());
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!force) {
            if (!Cache.stale(this, book, LocalDate.now())) return;
            if (now - prefs.getLong(KEY_TRY, 0) < RETRY_MS) return;
        }
        prefs.edit().putLong(KEY_TRY, now).apply();
        busy = true;
        error = "";
        showDay(page.selected());
        final LocalDate today = LocalDate.now();
        new Thread(() -> {
            Book fresh = null;
            String failure = "";
            try {
                fresh = Cache.download(getApplicationContext(), link, today);
            } catch (Exception e) {
                failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final Book result = fresh;
            final String why = failure;
            runOnUiThread(() -> {
                busy = false;
                if (!alive) return;
                if (result != null) {
                    book = result;
                    page.setBook(book);
                    updatedNow = true;
                }
                error = why;
                showDay(page.selected());
            });
        }, "kannadacalendar-download").start();
    }

    /** Channel and media keys turn the page from anywhere on the screen. */
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        switch (code) {
            case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_PAGE_UP: case KeyEvent.KEYCODE_MEDIA_REWIND:
                page.select(page.selected().minusMonths(1));
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN: case KeyEvent.KEYCODE_PAGE_DOWN: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                page.select(page.selected().plusMonths(1));
                return true;
            default:
                return super.onKeyDown(code, event);
        }
    }
}
