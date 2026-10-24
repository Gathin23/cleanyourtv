package com.gathin.tvhome;

import android.content.res.Resources;

import java.util.Calendar;
import java.util.Locale;

/**
 * Lunisolar (Vietnamese / Chinese) date, e.g. "2/9 âm lịch · Bính Ngọ" or "Lunar 2/9 · Year of the Horse", without ICU.
 * The astronomy is Hồ Ngọc Đức's public algorithm (new moons and solar terms from Jean Meeus' formulas); the only
 * difference between the Vietnamese and the Chinese calendar is the time zone it is computed for (UTC+7 or UTC+8),
 * which now and then moves a new moon, and with it the first day of a month, by one day.
 */
final class Lunar {

    private Lunar() {
    }

    /** Hours east of UTC used for the calendar: lunar_utc_offset, or 7 for Vietnamese and 8 otherwise. */
    static int offsetFor(int configured, Locale locale) {
        if (configured >= 0) return configured;
        return "vi".equals(locale.getLanguage()) ? 7 : 8;
    }

    /** Today's lunar date, formatted with the locale's strings. */
    static String today(Resources res, int utcOffset) {
        Calendar c = Calendar.getInstance();
        int[] l = fromSolar(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR), utcOffset);
        String month = l[3] == 1 ? res.getString(R.string.lunar_month_leap, l[1]) : String.valueOf(l[1]);
        String[] stems = res.getStringArray(R.array.lunar_stems);
        String[] branches = res.getStringArray(R.array.lunar_branches);
        String year = res.getString(R.string.lunar_year, stems[Math.floorMod(l[2] + 6, 10)], branches[Math.floorMod(l[2] + 8, 12)]);
        return res.getString(R.string.lunar_date_format, l[0], month, year);
    }

    /** Gregorian date to {day, month, year, leap (1 = leap month)} of the lunar calendar at UTC+tz. */
    static int[] fromSolar(int dd, int mm, int yy, double tz) {
        long dayNumber = jdFromDate(dd, mm, yy);
        int k = (int) Math.floor((dayNumber - 2415021.076998695) / 29.530588853);
        long monthStart = newMoonDay(k + 1, tz);
        if (monthStart > dayNumber) monthStart = newMoonDay(k, tz);
        long a11 = lunarMonth11(yy, tz);
        long b11 = a11;
        int lunarYear;
        if (a11 >= monthStart) {
            lunarYear = yy;
            a11 = lunarMonth11(yy - 1, tz);
        } else {
            lunarYear = yy + 1;
            b11 = lunarMonth11(yy + 1, tz);
        }
        int lunarDay = (int) (dayNumber - monthStart + 1);
        int diff = (int) Math.floor((monthStart - a11) / 29.0);
        int leap = 0;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapDiff = leapMonthOffset(a11, tz);
            if (diff >= leapDiff) {
                lunarMonth = diff + 10;
                if (diff == leapDiff) leap = 1;
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12;
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1;
        return new int[]{lunarDay, lunarMonth, lunarYear, leap};
    }

    static long jdFromDate(int dd, int mm, int yy) {
        int a = (14 - mm) / 12;
        long y = yy + 4800 - a;
        int m = mm + 12 * a - 3;
        long jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        if (jd < 2299161) jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        return jd;
    }

    /** Day number of the k-th new moon after 1900-01-01, in local time. */
    static long newMoonDay(int k, double tz) {
        double t = k / 1236.85, t2 = t * t, t3 = t2 * t, dr = Math.PI / 180;
        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3;
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr);
        double m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3;
        double mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3;
        double f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3;
        double c1 = (0.1734 - 0.000393 * t) * Math.sin(m * dr) + 0.0021 * Math.sin(2 * dr * m);
        // Term by term as in Hồ Ngọc Đức's code, each with its own sign: folding two terms into one "c1 -= a + b"
        // flips the second one, which moved new moons by hours and put the 2023 leap month a month early.
        c1 = c1 - 0.4068 * Math.sin(mpr * dr) + 0.0161 * Math.sin(dr * 2 * mpr);
        c1 = c1 - 0.0004 * Math.sin(dr * 3 * mpr);
        c1 = c1 + 0.0104 * Math.sin(dr * 2 * f) - 0.0051 * Math.sin(dr * (m + mpr));
        c1 = c1 - 0.0074 * Math.sin(dr * (m - mpr)) + 0.0004 * Math.sin(dr * (2 * f + m));
        c1 = c1 - 0.0004 * Math.sin(dr * (2 * f - m)) - 0.0006 * Math.sin(dr * (2 * f + mpr));
        c1 += 0.0010 * Math.sin(dr * (2 * f - mpr)) + 0.0005 * Math.sin(dr * (2 * mpr + m));
        double deltaT = t < -11
                ? 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
                : -0.000278 + 0.000265 * t + 0.000262 * t2;
        return (long) Math.floor(jd1 + c1 - deltaT + 0.5 + tz / 24);
    }

    /** Which of the 12 thirty-degree sectors the sun's longitude is in at the start of the day. */
    static int sunLongitude(long jdn, double tz) {
        double t = (jdn - 2451545.5 - tz / 24) / 36525, t2 = t * t, dr = Math.PI / 180;
        double m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2;
        double l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2;
        double dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(dr * m);
        dl += (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.000290 * Math.sin(dr * 3 * m);
        double l = (l0 + dl) * dr;
        l -= Math.PI * 2 * Math.floor(l / (Math.PI * 2));
        return (int) Math.floor(l / Math.PI * 6);
    }

    /** First day of the 11th lunar month (the one holding the winter solstice) of a Gregorian year. */
    static long lunarMonth11(int yy, double tz) {
        long off = jdFromDate(31, 12, yy) - 2415021;
        int k = (int) Math.floor(off / 29.530588853);
        long nm = newMoonDay(k, tz);
        if (sunLongitude(nm, tz) >= 9) nm = newMoonDay(k - 1, tz);
        return nm;
    }

    /** Index of the leap month after month 11 in a 13-month year: the first month with no solar term. */
    static int leapMonthOffset(long a11, double tz) {
        int k = (int) Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        int i = 1;
        int arc = sunLongitude(newMoonDay(k + i, tz), tz);
        int last;
        do {
            last = arc;
            i++;
            arc = sunLongitude(newMoonDay(k + i, tz), tz);
        } while (arc != last && i < 14);
        return i - 1;
    }
}
