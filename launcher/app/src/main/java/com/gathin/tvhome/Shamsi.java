package com.gathin.tvhome;

import java.util.Calendar;

/** Solar Hijri (Shamsi) date in Latin letters, without ICU: the arithmetic is small and stable. */
final class Shamsi {

    private static final String[] MONTHS = {
            "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
            "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"};

    private Shamsi() {
    }

    /** e.g. "27 Mehr 1405" */
    static String today() {
        Calendar c = Calendar.getInstance();
        int[] j = toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        return j[2] + " " + MONTHS[j[1] - 1] + " " + j[0];
    }

    /** Gregorian to Jalali, the 33-year-cycle algorithm used by jalaali-js. Returns {year, month, day}. */
    static int[] toJalali(int gy, int gm, int gd) {
        int[] monthStart = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int gy2 = gm > 2 ? gy + 1 : gy;
        int days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + monthStart[gm - 1];
        int jy = -1595 + 33 * (days / 12053);
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm, jd;
        if (days < 186) {
            jm = 1 + days / 31;
            jd = 1 + days % 31;
        } else {
            jm = 7 + (days - 186) / 30;
            jd = 1 + (days - 186) % 30;
        }
        return new int[]{jy, jm, jd};
    }
}
