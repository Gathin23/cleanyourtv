package com.lionlev.tv.jewishcalendar;

import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.icu.util.HebrewCalendar;
import android.icu.util.ULocale;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;

/** Optional civil display, computed offline; sunset is approximate, not a source of religious times. */
final class JewishDate {
    // One-based indices into jewish_greetings; zero keeps the ordinary greeting.
    static final int NEW_YEAR = 1, KIPPUR = 2, HANUKKAH = 3, PURIM = 4, HOLIDAY = 5, SHABBAT = 6;
    final boolean greetings;
    private final double latitude, longitude;
    private final String region;

    private JewishDate(boolean greetings, String region, double latitude, double longitude) {
        this.greetings = greetings;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    static JewishDate load(SharedPreferences preferences) {
        try {
            double latitude = Double.parseDouble(preferences.getString("latitude", ""));
            double longitude = Double.parseDouble(preferences.getString("longitude", ""));
            if (!validLocation(latitude, longitude)) return null;
            return new JewishDate(preferences.getBoolean("greetings", true),
                    preferences.getString("region", "auto"), latitude, longitude);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean validLocation(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
    }

    /** Null when there is no sunset; never silently substitute another city's sunset. */
    LocalDate day(ZonedDateTime now) {
        return day(now, latitude, longitude);
    }

    static LocalDate day(ZonedDateTime now, double latitude, double longitude) {
        if (!validLocation(latitude, longitude)) return null;
        LocalDate date = now.toLocalDate();
        int[] sun = Sun.riseAndSet(date, latitude, longitude, now.getZone());
        if (sun == null) return null;
        return now.getHour() * 60 + now.getMinute() >= sun[1] ? date.plusDays(1) : date;
    }

    static boolean israel(Locale locale, String region) {
        if ("israel".equals(region)) return true;
        if ("diaspora".equals(region)) return false;
        return "IL".equals(locale.getCountry()) || (locale.getCountry().isEmpty()
                && ("he".equals(locale.getLanguage()) || "iw".equals(locale.getLanguage())));
    }

    private static HebrewCalendar calendar(LocalDate day) {
        HebrewCalendar calendar = new HebrewCalendar(android.icu.util.TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(day.toEpochDay() * 86400000L + 43200000L);
        return calendar;
    }

    static String text(LocalDate day, Locale locale) {
        boolean he = "he".equals(locale.getLanguage()) || "iw".equals(locale.getLanguage());
        SimpleDateFormat format = new SimpleDateFormat(he ? "d בMMMM y" : "d MMMM y",
                he ? new ULocale("he_IL@calendar=hebrew;numbers=hebr")
                        : new ULocale(locale.toString() + "@calendar=hebrew"));
        HebrewCalendar calendar = calendar(day);
        format.setCalendar(calendar);
        String text = format.format(calendar.getTime());
        return he ? withoutMillennium(text) : text;
    }

    /** Keep the proper Hebrew punctuation, but trim its font's oversized side bearings. */
    static final class CompactQuotes extends android.text.method.SingleLineTransformationMethod {
        @Override public CharSequence getTransformation(CharSequence source, android.view.View view) {
            CharSequence text = super.getTransformation(source, view);
            if (text == null) return null;
            android.text.SpannableString result = new android.text.SpannableString(text);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '״') result.setSpan(new Quote(), i, i + 1,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return result;
        }
    }

    private static final class Quote extends android.text.style.ReplacementSpan {
        private final android.graphics.Rect bounds = new android.graphics.Rect();

        @Override public int getSize(android.graphics.Paint paint, CharSequence text, int start, int end,
                android.graphics.Paint.FontMetricsInt metrics) {
            paint.getTextBounds(text.toString(), start, end, bounds);
            return bounds.width();
        }

        @Override public void draw(android.graphics.Canvas canvas, CharSequence text, int start, int end,
                float x, int top, int y, int bottom, android.graphics.Paint paint) {
            canvas.drawText(text, start, end, x - bounds.left, y, paint);
        }
    }

    /** Omit only a year's thousands prefix; preserve day 5 and Adar I/II markers. */
    static String withoutMillennium(String text) {
        return text.replaceFirst(" [א-ט]׳(?=[א-ת׳״]+$)", " ");
    }

    int greeting(LocalDate day, Locale locale) {
        int today = holidayOn(day, locale);
        if (today != 0) return today;
        return preferredGreeting(0, holidayOn(day.plusDays(1), locale), day);
    }

    static int preferredGreeting(int holiday, int eve, LocalDate day) {
        if (holiday != 0) return holiday;
        if (eve != 0) return eve;
        // Epoch day 2 was Saturday; Java's signed remainder is -5 for pre-1970 Saturdays.
        long weekday = day.toEpochDay() % 7;
        return weekday == 2 || weekday == -5 ? SHABBAT : 0;
    }

    private int holidayOn(LocalDate day, Locale locale) {
        HebrewCalendar c = calendar(day);
        int month = c.get(HebrewCalendar.MONTH), date = c.get(HebrewCalendar.DAY_OF_MONTH);
        // Kislev may have 29 or 30 days; Hanukkah always lasts eight days.
        c.set(HebrewCalendar.MONTH, HebrewCalendar.KISLEV);
        c.set(HebrewCalendar.DAY_OF_MONTH, 1);
        return holiday(month, date, c.getActualMaximum(HebrewCalendar.DAY_OF_MONTH), israel(locale, region));
    }

    /** ICU month numbers: Tishri=0, Kislev=2, Tevet=3, Adar/Adar II=6, Nisan=7, Sivan=9. */
    static int holiday(int month, int day, int kislevDays, boolean israel) {
        if (month == 0) {
            if (day == 1 || day == 2) return NEW_YEAR;
            if (day == 10) return KIPPUR;
            if (day >= 15 && day <= 21) return HOLIDAY;
            if (day == 22) return HOLIDAY;
            if (day == 23 && !israel) return HOLIDAY;
        }
        if ((month == 2 && day >= 25 && day <= kislevDays)
                || (month == 3 && day >= 1 && day <= 32 - kislevDays)) return HANUKKAH;
        if (month == 6 && (day == 14 || day == 15)) return PURIM;
        if (month == 7 && day >= 15 && day <= (israel ? 21 : 22)) return HOLIDAY;
        if (month == 9 && (day == 6 || (day == 7 && !israel))) return HOLIDAY;
        return 0;
    }
}
