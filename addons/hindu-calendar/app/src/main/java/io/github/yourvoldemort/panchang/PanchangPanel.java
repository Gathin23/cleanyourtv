package io.github.yourvoldemort.panchang;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared foreground-only scene. No timers survive the activity or dream stopping. */
final class PanchangPanel extends LinearLayout {
    static final int TEXT = Color.WHITE, DIM = 0xffb0a89c, SOFT = 0xffe6ded2,
            ACCENT = 0xffe9b44c, BACKGROUND = 0xff14100c;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean dream;
    private final TextView era, nativeDate, latinDate, gregorian, sun, note;
    /** Rahu Kala, Yamaganda, Gulika and Abhijit, in the order the two columns read. */
    private final LinearLayout muhurtas;
    private final TextView[] windows = new TextView[4];
    /** Tithi, nakshatra, yoga, karana: the value at sunrise and the moment it ends. */
    private final TextView[] values = new TextView[4];
    private final TextView[] ends = new TextView[4];
    private Region region = Region.NORTH;
    private City city;
    /** The day on screen; null means today, so a TV left on follows the date. */
    private LocalDate selected;
    /** Changes when the date, city or region changes; the clock alone does not refetch. */
    private String shown = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L);
        }
    };

    PanchangPanel(Context context, boolean dream) {
        super(context);
        this.dream = dream;
        city = City.forTimeZone();
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(40), dp(24), dp(40), dp(24));
        setBackgroundColor(BACKGROUND);
        era = line(dream ? 22 : 18, ACCENT);
        nativeDate = line(dream ? 46 : 40, TEXT);
        latinDate = line(dream ? 26 : 21, SOFT);
        gregorian = line(dream ? 22 : 17, DIM);

        LinearLayout table = new LinearLayout(context);
        table.setOrientation(VERTICAL);
        table.setGravity(Gravity.CENTER);
        table.setPadding(0, dp(20), 0, dp(10));
        int[] labels = {R.string.tithi, R.string.nakshatra, R.string.yoga, R.string.karana};
        int size = dream ? 26 : 20;
        for (int i = 0; i < labels.length; i++) {
            LinearLayout row = new LinearLayout(context);
            TextView label = text(size - 3, DIM);
            label.setText(labels[i]);
            label.setPadding(0, dp(4), dp(16), dp(4));
            row.addView(label, new LayoutParams(dp(dream ? 190 : 160), LayoutParams.WRAP_CONTENT));
            values[i] = text(size, TEXT);
            values[i].setPadding(0, dp(4), dp(16), dp(4));
            values[i].setSingleLine(true);
            row.addView(values[i], new LayoutParams(dp(dream ? 330 : 280), LayoutParams.WRAP_CONTENT));
            ends[i] = text(size - 4, SOFT);
            ends[i].setGravity(Gravity.END);
            ends[i].setPadding(0, dp(4), 0, dp(4));
            row.addView(ends[i], new LayoutParams(dp(dream ? 180 : 150), LayoutParams.WRAP_CONTENT));
            table.addView(row);
        }
        addView(table, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        sun = line(dream ? 22 : 17, SOFT);
        sun.setPadding(0, dp(10), 0, 0);

        // Two columns of two, so four windows cost two lines rather than four on a 720p screen.
        muhurtas = new LinearLayout(context);
        muhurtas.setOrientation(HORIZONTAL);
        muhurtas.setGravity(Gravity.CENTER);
        muhurtas.setPadding(0, dp(8), 0, 0);
        int[] names = {R.string.rahu_kala, R.string.yamaganda, R.string.gulika, R.string.abhijit};
        for (int column = 0; column < 2; column++) {
            LinearLayout stack = new LinearLayout(context);
            stack.setOrientation(VERTICAL);
            for (int row = 0; row < 2; row++) {
                int index = column * 2 + row;
                LinearLayout entry = new LinearLayout(context);
                entry.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = text(size - 4, DIM);
                name.setText(names[index]);
                name.setSingleLine(true);
                name.setPadding(0, dp(2), dp(10), dp(2));
                entry.addView(name, new LayoutParams(dp(dream ? 150 : 115), LayoutParams.WRAP_CONTENT));
                windows[index] = text(size - 2, SOFT);
                windows[index].setSingleLine(true);
                windows[index].setPadding(0, dp(2), dp(column == 0 ? 28 : 0), dp(2));
                entry.addView(windows[index]);
                stack.addView(entry);
            }
            muhurtas.addView(stack);
        }
        addView(muhurtas, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        note = line(dream ? 17 : 14, DIM);
    }

    /** The card screen adds its buttons under the readings. */
    void addActions(View actions) { addView(actions); }

    private TextView line(int size, int color) {
        TextView text = text(size, color);
        text.setMaxLines(2);
        text.setPadding(0, dp(3), 0, dp(3));
        addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
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

    int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    City city() { return city; }

    Region region() { return region; }

    void setCity(City city) {
        if (city == null || city == this.city) return;
        this.city = city;
        shown = "";
        refresh();
    }

    void setRegion(Region region) {
        if (region == null || region == this.region) return;
        this.region = region;
        shown = "";
        refresh();
    }

    void start() {
        handler.removeCallbacks(tick);
        tick.run();
    }

    void stop() { handler.removeCallbacksAndMessages(null); }

    /** Moves the screen by whole days; 0 returns to today. */
    void showDay(int delta) {
        LocalDate today = LocalDate.now(city.zone);
        selected = delta == 0 ? null : (selected == null ? today : selected).plusDays(delta);
        refresh();
    }

    /** The day on screen: the chosen one, or today while the screen follows the date. */
    LocalDate date() { return selected == null ? LocalDate.now(city.zone) : selected; }

    /** Shows a particular day, as the month grid asks. */
    void show(LocalDate date) {
        selected = date;
        shown = "";
        refresh();
    }

    private void refresh() {
        ZonedDateTime now = ZonedDateTime.now(city.zone);
        LocalDate date = selected == null ? now.toLocalDate() : selected;
        if (date.equals(shown)) return;
        shown = date.toString();

        Panchang p = Panchang.at(date, city);
        MonthSpan span = MonthSpan.of(p.sunrise, city, region);
        int day = span.dayNumber(date);
        boolean krishna = Panchang.krishna(p.tithi);
        int year = region.year(span);

        era.setText(getContext().getString(R.string.heading, region.label, region.era,
                Integer.toString(year)));
        StringBuilder own = new StringBuilder();
        if (span.adhika) own.append(region.adhika);
        own.append(region.month(span));
        if (!region.solar) own.append(' ').append(region.paksha[krishna ? 1 : 0]);
        own.append(' ').append(region.digits(day));
        nativeDate.setText(own);
        latinDate.setText(region.solar
                ? region.monthLatin(span) + " " + day
                : region.monthLatin(span) + " " + Panchang.tithiLabel(p.tithi));

        Locale locale = Locale.getDefault();
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMyyyy");
        String gregorianText = DateTimeFormatter.ofPattern(pattern, locale).format(date);
        String special = Panchang.specialDay(p.tithi);
        if (special == null) {
            gregorian.setText(gregorianText);
        } else {
            SpannableString marked = new SpannableString(gregorianText + " · " + special);
            marked.setSpan(new ForegroundColorSpan(ACCENT), gregorianText.length(), marked.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            gregorian.setText(marked);
        }

        String tithi = Panchang.tithiLabel(p.tithi);
        values[0].setText(tithi);
        values[1].setText(Panchang.NAKSHATRA[p.nakshatra - 1]);
        values[2].setText(Panchang.YOGA[p.yoga - 1]);
        values[3].setText(Panchang.karanaName(p.karana));
        ends[0].setText(till(p.tithiEnd, date));
        ends[1].setText(till(p.nakshatraEnd, date));
        ends[2].setText(till(p.yogaEnd, date));
        ends[3].setText(till(p.karanaEnd, date));

        sun.setText(p.hasSunrise
                ? getContext().getString(R.string.sun_times, clock(p.sunrise), clock(p.sunset))
                : getContext().getString(R.string.no_sun));
        Muhurta muhurta = Muhurta.of(date, p);
        muhurtas.setVisibility(muhurta == null ? GONE : VISIBLE);
        if (muhurta != null) {
            windows[0].setText(range(muhurta.rahuStart, muhurta.rahuEnd));
            windows[1].setText(range(muhurta.yamagandaStart, muhurta.yamagandaEnd));
            windows[2].setText(range(muhurta.gulikaStart, muhurta.gulikaEnd));
            windows[3].setText(range(muhurta.abhijitStart, muhurta.abhijitEnd));
        }
        note.setText(getContext().getString(R.string.place, city.name, city.coordinates()));
        // Drift in a padded area, keeping all content inside the full-screen panel.
        if (dream) setTranslationY(((now.getMinute() % 5) - 2) * dp(8));
    }

    /** The moment the reading ends, with a +1 when it falls after midnight. */
    private String till(long millis, LocalDate date) {
        ZonedDateTime end = Instant.ofEpochMilli(millis).atZone(city.zone);
        long days = end.toLocalDate().toEpochDay() - date.toEpochDay();
        String suffix = days == 1 ? " +1" : days > 1 ? " +" + days : "";
        return getContext().getString(R.string.till, clock(millis) + suffix);
    }

    private String range(long start, long end) {
        return getContext().getString(R.string.range, clock(start), clock(end));
    }

    private String clock(long millis) {
        Locale locale = Locale.getDefault();
        String pattern = android.text.format.DateFormat.is24HourFormat(getContext())
                ? "HH:mm" : "h:mm a";
        return DateTimeFormatter.ofPattern(pattern, locale)
                .format(Instant.ofEpochMilli(millis).atZone(city.zone));
    }
}
