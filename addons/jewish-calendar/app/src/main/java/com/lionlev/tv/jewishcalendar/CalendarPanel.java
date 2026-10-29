package com.lionlev.tv.jewishcalendar;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;

/** Shared foreground-only scene. No timers survive the activity or dream stopping. */
final class CalendarPanel extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView greeting, clock, gregorian, hebrew, note;
    private final boolean dream;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 60_000L);
        }
    };

    CalendarPanel(Context context, boolean dream) {
        super(context);
        this.dream = dream;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
        setPadding(dp(32), dp(24), dp(32), dp(24));
        setBackgroundColor(Color.rgb(15, 23, 36));
        greeting = line(28, 0xffabc8ef);
        clock = line(92, Color.WHITE);
        gregorian = line(26, 0xffdddddd);
        hebrew = line(26, 0xffdddddd);
        hebrew.setTransformationMethod(new JewishDate.CompactQuotes());
        if (isHebrew()) gregorian.setTransformationMethod(new JewishDate.CompactQuotes());
        note = line(16, 0xffa0aab8);
        note.setPadding(0, dp(20), 0, 0);
    }

    private TextView line(int size, int color) {
        TextView text = new TextView(getContext());
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER);
        text.setTextDirection(TEXT_DIRECTION_LOCALE);
        text.setMaxLines(2);
        text.setIncludeFontPadding(false);
        text.setPadding(0, dp(5), 0, dp(5));
        addView(text, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return text;
    }

    int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private boolean isHebrew() {
        String language = Locale.getDefault().getLanguage();
        return "he".equals(language) || "iw".equals(language);
    }

    void start() {
        handler.removeCallbacks(tick);
        tick.run();
    }

    void stop() { handler.removeCallbacksAndMessages(null); }

    private void refresh() {
        ZonedDateTime now = ZonedDateTime.now();
        Locale locale = Locale.getDefault();
        boolean he = isHebrew();
        String pattern = he ? "EEEE d 'ב'MMMM" : android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM");
        String date = java.time.format.DateTimeFormatter.ofPattern(pattern, locale).format(now);
        clock.setText(java.time.format.DateTimeFormatter.ofPattern(
                android.text.format.DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm a", locale).format(now));
        clock.setTextDirection(TEXT_DIRECTION_LTR);
        JewishDate calendar = JewishDate.load(getContext().getSharedPreferences("calendar", Context.MODE_PRIVATE));
        LocalDate day = calendar == null ? null : calendar.day(now);
        String alternate = day == null ? "" : JewishDate.text(day, locale);
        gregorian.setText(dream && !alternate.isEmpty() ? date + ", " + alternate : date);
        hebrew.setText(alternate);
        hebrew.setVisibility(dream || alternate.isEmpty() ? View.GONE : View.VISIBLE);
        int holiday = day != null && calendar.greetings ? calendar.greeting(day, locale) : 0;
        greeting.setText(holiday == 0 ? "" : getResources().getStringArray(R.array.jewish_greetings)[holiday - 1]);
        greeting.setVisibility(holiday == 0 ? View.GONE : View.VISIBLE);
        note.setText(calendar == null ? R.string.missing_location : day == null ? R.string.no_sunset : R.string.timing_note);
        note.setVisibility(dream && day != null ? View.GONE : View.VISIBLE);
        // Drift in a padded area, keeping all content inside the full-screen panel.
        if (dream) {
            float scale = getResources().getDisplayMetrics().density;
            float displacement = ((now.getMinute() % 5) - 2) * 8 * scale;
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).setTranslationY(displacement);
        }
    }
}
