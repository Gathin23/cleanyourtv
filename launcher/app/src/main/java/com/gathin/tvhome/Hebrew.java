package com.gathin.tvhome;

import android.content.res.Resources;

import android.icu.util.HebrewCalendar;

import java.util.Date;

/**
 * Hebrew (Jewish) calendar date under the date line, e.g. "ל׳ בחשוון תשפ״ז".
 * Shown instead of the lunisolar date when lunar_date is on and the locale is Hebrew.
 * The calendar math is the bundled ICU android.icu.util.HebrewCalendar; only the
 * gematria numerals and month names are ours.
 */
final class Hebrew {

    private static final String[] MONTHS = {
            "", "ניסן", "אייר", "סיוון", "תמוז", "אב", "אלול", "תשרי",
            "חשוון", "כסלו", "טבת", "שבט", "אדר", "אדר ב׳"
    };
    private static final int[] VALUES = {
            400, 300, 200, 100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    private static final char[] LETTERS = {
            'ת', 'ש', 'ר', 'ק', 'צ', 'פ', 'ע', 'ס', 'נ', 'מ', 'ל', 'כ', 'י', 'ט', 'ח', 'ז', 'ו', 'ה', 'ד', 'ג', 'ב', 'א'};

    private Hebrew() {
    }

    /** True when the device speaks Hebrew (Java reports "iw", Android 13+ also "he"). */
    static boolean isLocale() {
        String lang = java.util.Locale.getDefault().getLanguage();
        return "iw".equals(lang) || "he".equals(lang);
    }

    /** Today's Hebrew date, formatted with the locale's strings. */
    static String today(Resources res) {
        HebrewCalendar c = new HebrewCalendar();
        c.setTime(new Date());
        int y = c.get(HebrewCalendar.YEAR);
        int m = c.get(HebrewCalendar.MONTH);
        int d = c.get(HebrewCalendar.DAY_OF_MONTH);
        // Month 12 is the only Adar in a plain year but Adar I in a leap year (then 13 is Adar II).
        String month = MONTHS[m];
        if (m == 12 && c.getActualMaximum(HebrewCalendar.MONTH) == 13) month = "אדר א׳";
        return res.getString(R.string.hebrew_date_format, numeral(d), month, numeral(y % 1000));
    }

    /** Gematria numeral: 30 → ל׳, 787 → תשפ״ז. The divine-name pairs יה/יו become טו/טז. */
    static String numeral(int n) {
        if (n <= 0 || n >= 1000) return String.valueOf(n);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < VALUES.length; i++) {
            while (n >= VALUES[i]) {
                s.append(LETTERS[i]);
                n -= VALUES[i];
            }
        }
        String g = s.toString();
        if ("יה".equals(g)) g = "טו";
        if ("יו".equals(g)) g = "טז";
        return g.length() > 1
                ? g.substring(0, g.length() - 1) + "״" + g.substring(g.length() - 1)
                : g + "׳";
    }
}
