package com.oneyuv.tv.nepalicalendar;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Names, numerals and the fixed-date national days of the Bikram Sambat calendar. */
final class NepaliDate {
    static final String[] MONTHS = {"बैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज",
            "कार्तिक", "मंसिर", "पौष", "माघ", "फाल्गुन", "चैत"};
    static final String[] MONTHS_LATIN = {"Baishakh", "Jestha", "Asar", "Shrawan", "Bhadra", "Asoj",
            "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chaitra"};
    /** Sunday first, as Nepali calendars print the week. */
    static final String[] WEEKDAYS = {"आइतबार", "सोमबार", "मंगलबार", "बुधबार", "बिहीबार", "शुक्रबार", "शनिबार"};
    static final String[] WEEKDAYS_SHORT = {"आइत", "सोम", "मंगल", "बुध", "बिही", "शुक्र", "शनि"};

    /**
     * National days kept on a fixed BS date, {month, day}, in the order of R.array.national_days.
     * Lunar festivals (Dashain, Tihar, Holi, Teej and others) move every year and are not here.
     */
    private static final int[][] NATIONAL_DAYS = {
            {1, 1},   // Nawa Barsha, New Year
            {1, 11},  // Loktantra Diwas
            {2, 15},  // Ganatantra Diwas, Republic Day
            {6, 3},   // Sambidhan Diwas, Constitution Day
            {9, 27},  // Prithvi Jayanti
            {10, 1},  // Maghe Sankranti
            {10, 16}, // Shahid Diwas, Martyrs' Day
            {11, 7},  // Prajatantra Diwas, Democracy Day
    };

    private NepaliDate() {}

    /** Western digits to Devanagari digits; everything else is kept. */
    static String digits(CharSequence text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c >= '0' && c <= '9' ? (char) (c - '0' + '०') : c);
        }
        return out.toString();
    }

    static String digits(int number) { return digits(Integer.toString(number)); }

    /** Sunday = 0 to Saturday = 6. */
    static int weekday(LocalDate date) { return date.getDayOfWeek().getValue() % 7; }

    /** Saturday and Sunday, shown in red like the holidays. */
    static boolean weekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /** "मंगलबार, कार्तिक १०, २०८३" */
    static String text(BikramSambat bs, LocalDate date) {
        return WEEKDAYS[weekday(date)] + ", " + MONTHS[bs.month - 1] + " " + digits(bs.day) + ", " + digits(bs.year);
    }

    /** "Kartik 10, 2083 BS" */
    static String latin(BikramSambat bs) { return MONTHS_LATIN[bs.month - 1] + " " + bs.day + ", " + bs.year + " BS"; }

    /** "कार्तिक २०८३ · Kartik 2083" */
    static String monthTitle(BikramSambat bs) {
        return MONTHS[bs.month - 1] + " " + digits(bs.year) + " · " + MONTHS_LATIN[bs.month - 1] + " " + bs.year;
    }

    /** 1-based index into R.array.national_days, or 0 when the day is none of them. */
    static int nationalDay(BikramSambat bs) {
        for (int i = 0; i < NATIONAL_DAYS.length; i++) {
            if (NATIONAL_DAYS[i][0] == bs.month && NATIONAL_DAYS[i][1] == bs.day) return i + 1;
        }
        return 0;
    }

    static int nationalDayCount() { return NATIONAL_DAYS.length; }
}
