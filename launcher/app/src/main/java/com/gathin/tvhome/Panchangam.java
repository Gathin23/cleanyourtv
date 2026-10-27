package com.gathin.tvhome;

import android.content.res.Resources;

import java.time.LocalDate;

/**
 * A Tamil daily calendar sheet for one day, worked out offline for Chennai: the Tamil and Hijri dates, thithi and
 * nakshatram with the time each ends (in nazhigai after sunrise and by the clock, as the printed sheets give them),
 * sunrise, Rahu, Kuligai and Emagandam, soolam with its remedy, and the good horas. The moon is Jean Meeus' chapter 47
 * series cut to its larger terms, good to a few minutes of an end time. Nalla neram, gowri nalla neram, rasi palan
 * and festival notes are left out: they differ between publishers and follow no rule that can be computed.
 */
final class Panchangam {

    /** Rahu, Kuligai and Emagandam: which 1.5-hour slot from 6 AM each takes, by weekday (0 = Sunday). */
    static final int[] RAHU = {7, 1, 6, 4, 5, 3, 2};
    static final int[] KULIGAI = {6, 5, 4, 3, 2, 1, 0};
    static final int[] EMAGANDAM = {4, 3, 2, 1, 0, 6, 5};
    /** Soolam's direction and its remedy, by weekday: 0 east and curd, 1 west and jaggery, 2 north and milk, 3 south and oil. */
    static final int[] SOOLAM = {1, 0, 2, 2, 3, 1, 0};
    /** The planets in Chaldean order (Saturn, Jupiter, Mars, Sun, Venus, Mercury, Moon) and each weekday's lord. */
    static final int[] DAY_LORD = {3, 6, 2, 5, 1, 4, 0};
    /** The good horas: Jupiter, Venus, Mercury, Moon. */
    static final boolean[] GOOD_HORA = {false, true, false, false, true, true, true};
    /** Tamil Nadu's Hijri date against the tabular one, set from a printed sheet (27-10-2026 is Jumada al-Awwal 15). */
    static final int HIJRI_OFFSET = 0;

    final LocalDate date;
    /** 0 = Sunday. */
    final int weekday;
    final int[] tamil;
    final int[] hijri;
    /** Minutes after midnight. */
    final int sunrise;
    /** Thithi 0-29 (14 = pournami, 29 = amavasai) and nakshatram 0-26 at sunrise, and when each ends, in minutes after
     *  midnight (past 1440 means after midnight; -1 means it lasts past the next sunrise). */
    final int thithi, thithiEnd, nakshatram, nakshatramEnd;

    private Panchangam(LocalDate date) {
        this.date = date;
        weekday = date.getDayOfWeek().getValue() % 7;
        tamil = Tamil.fromGregorian(date);
        long jdn = Lunar.jdFromDate(date.getDayOfMonth(), date.getMonthValue(), date.getYear());
        hijri = hijri(jdn + HIJRI_OFFSET);
        sunrise = Tamil.sunriseAndSunset(date)[0];
        int nextSunrise = 1440 + Tamil.sunriseAndSunset(date.plusDays(1))[0];
        thithi = thithiAt(Tamil.jd(date, sunrise));
        thithiEnd = end(date, sunrise, nextSunrise, true);
        nakshatram = nakshatramAt(Tamil.jd(date, sunrise));
        nakshatramEnd = end(date, sunrise, nextSunrise, false);
    }

    static Panchangam of(LocalDate date) {
        return new Panchangam(date);
    }

    static int thithiAt(double jd) {
        return (int) Math.floor(Tamil.degrees(moonLongitude(jd) - Tamil.sunLongitude(jd)) / 12);
    }

    static int nakshatramAt(double jd) {
        return (int) Math.floor(Tamil.degrees(moonLongitude(jd) - Tamil.ayanamsa(jd)) * 27 / 360);
    }

    /** The first minute after sunrise at which the thithi (or nakshatram) changes, or -1 if it holds till next sunrise. */
    private static int end(LocalDate date, int from, int to, boolean thithi) {
        int start = thithi ? thithiAt(Tamil.jd(date, from)) : nakshatramAt(Tamil.jd(date, from));
        int lo = from;
        for (int t = from + 20; ; t += 20) {
            int at = Math.min(t, to);
            int now = thithi ? thithiAt(Tamil.jd(date, at)) : nakshatramAt(Tamil.jd(date, at));
            if (now != start) {
                int hi = at;
                while (hi - lo > 1) {
                    int mid = (lo + hi) / 2;
                    int m = thithi ? thithiAt(Tamil.jd(date, mid)) : nakshatramAt(Tamil.jd(date, mid));
                    if (m == start) lo = mid; else hi = mid;
                }
                return hi;
            }
            if (at >= to) return -1;
            lo = at;
        }
    }

    /** The moon's apparent longitude in degrees, Meeus chapter 47 with the terms above 0.002 degree. */
    static double moonLongitude(double jdUt) {
        double t = (jdUt + 69 / 86400.0 - 2451545) / 36525, dr = Math.PI / 180;
        double lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t * t;
        double d = (297.8501921 + 445267.1114034 * t - 0.0018819 * t * t) * dr;
        double m = (357.5291092 + 35999.0502909 * t - 0.0001536 * t * t) * dr;
        double mp = (134.9633964 + 477198.8675055 * t + 0.0087414 * t * t) * dr;
        double f = (93.2720950 + 483202.0175233 * t - 0.0036539 * t * t) * dr;
        double e = 1 - 0.002516 * t - 0.0000074 * t * t;
        double s = 0;
        for (int[] term : MOON) {
            double c = term[4] * (Math.abs(term[1]) == 1 ? e : Math.abs(term[1]) == 2 ? e * e : 1);
            s += c * Math.sin(term[0] * d + term[1] * m + term[2] * mp + term[3] * f);
        }
        s += 3958 * Math.sin((119.75 + 131.849 * t) * dr) + 1962 * Math.sin(lp * dr - f)
                + 318 * Math.sin((53.09 + 479264.290 * t) * dr);
        return Tamil.degrees(lp + s / 1e6 - 0.00478 * Math.sin((125.04 - 1934.136 * t) * dr));
    }

    /** Multiples of D, M, M', F and the coefficient in millionths of a degree (Meeus table 47.A). */
    private static final int[][] MOON = {
            {0, 0, 1, 0, 6288774}, {2, 0, -1, 0, 1274027}, {2, 0, 0, 0, 658314}, {0, 0, 2, 0, 213618},
            {0, 1, 0, 0, -185116}, {0, 0, 0, 2, -114332}, {2, 0, -2, 0, 58793}, {2, -1, -1, 0, 57066},
            {2, 0, 1, 0, 53322}, {2, -1, 0, 0, 45758}, {0, 1, -1, 0, -40923}, {1, 0, 0, 0, -34720},
            {0, 1, 1, 0, -30383}, {2, 0, 0, -2, 15327}, {0, 0, 1, 2, -12528}, {0, 0, 1, -2, 10980},
            {4, 0, -1, 0, 10675}, {0, 0, 3, 0, 10034}, {4, 0, -2, 0, 8548}, {2, 1, -1, 0, -7888},
            {2, 1, 0, 0, -6766}, {1, 0, -1, 0, -5163}, {1, 1, 0, 0, 4987}, {2, -1, 1, 0, 4036},
            {2, 0, 2, 0, 3994}, {4, 0, 0, 0, 3861}, {2, 0, -3, 0, 3665}, {0, 1, -2, 0, -2689},
            {2, 0, -1, 2, -2602}, {2, -1, -2, 0, 2390}, {1, 0, 1, 0, -2348}, {2, -2, 0, 0, 2236},
            {0, 1, 2, 0, -2120}, {0, 2, 0, 0, -2069}};

    /** Tabular Islamic date {year, month 1-12, day} of a Julian day number. */
    static int[] hijri(long jdn) {
        long l = jdn - 1948440 + 10632;
        long n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        long j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
        int month = (int) ((24 * l) / 709);
        int day = (int) (l - (709L * month) / 24);
        return new int[]{(int) (30 * n + j - 30), month, day};
    }

    /** A 1.5-hour slot from 6 AM, as the sheets print it: "3-4.30". */
    static String slot(int index) {
        int from = 360 + 90 * index;
        return clock(from) + "-" + clock(from + 90);
    }

    /** 12-hour time without AM/PM, minutes only when there are any: "4.30", "12". */
    static String clock(int minutes) {
        int h = (minutes / 60) % 12, m = minutes % 60;
        return (h == 0 ? 12 : h) + (m == 0 ? "" : String.format(java.util.Locale.ROOT, ".%02d", m));
    }

    /** Nazhigai and vinadi after sunrise, as "6.30" (a nazhigai is 24 minutes, a vinadi 24 seconds). */
    static String nazhigai(int minutesAfterSunrise) {
        return (minutesAfterSunrise / 24) + String.format(java.util.Locale.ROOT, ".%02d", (minutesAfterSunrise % 24) * 5 / 2);
    }

    // ---- text ------------------------------------------------------------------------------------------------

    /** "ஐப்பசி", the big day number, and "பராபவ · செவ்வாய்". */
    String month(Resources res) {
        return res.getStringArray(R.array.tamil_months)[tamil[1]];
    }

    String yearAndWeekday(Resources res) {
        return res.getStringArray(R.array.tamil_years)[tamil[0]] + " · " + res.getStringArray(R.array.tamil_weekdays)[weekday];
    }

    /** Thithi, nakshatram, Hijri date and sunrise, one per line. */
    String body(Resources res) {
        String[] thithis = res.getStringArray(R.array.tamil_thithis);
        String[] stars = res.getStringArray(R.array.tamil_nakshatrams);
        return res.getString(R.string.panchangam_thithi) + "  " + until(res, thithiName(thithis, thithi), thithiEnd) + "\n"
                + res.getString(R.string.panchangam_star) + "  " + until(res, stars[nakshatram], nakshatramEnd) + "\n"
                + res.getStringArray(R.array.hijri_months)[hijri[1] - 1] + " " + hijri[2] + "   "
                + res.getString(R.string.panchangam_sunrise, clock(sunrise));
    }

    /** Rahu, Kuligai, Emagandam, soolam, and the good horas. */
    String times(Resources res) {
        StringBuilder horas = new StringBuilder();
        String[] planets = res.getStringArray(R.array.tamil_horas);
        for (int i = 0; i < 7; i++) {
            int planet = (DAY_LORD[weekday] + i) % 7;
            if (!GOOD_HORA[planet]) continue;
            if (horas.length() > 0) horas.append(" · ");
            horas.append(planets[planet]).append(' ').append(clock(360 + 60 * i)).append('-').append(clock(420 + 60 * i));
        }
        return res.getString(R.string.panchangam_rahu, slot(RAHU[weekday])) + "   "
                + res.getString(R.string.panchangam_kuligai, slot(KULIGAI[weekday])) + "\n"
                + res.getString(R.string.panchangam_emagandam, slot(EMAGANDAM[weekday])) + "\n"
                + res.getString(R.string.panchangam_soolam, res.getStringArray(R.array.soolam_directions)[SOOLAM[weekday]],
                res.getStringArray(R.array.soolam_remedies)[SOOLAM[weekday]]) + "\n"
                + res.getString(R.string.panchangam_hora) + "  " + horas;
    }

    private String until(Resources res, String name, int end) {
        if (end < 0) return name;
        String[] parts = res.getStringArray(R.array.panchangam_day_parts);
        int h = (end / 60) % 24;
        String part = parts[h < 4 ? 3 : h < 12 ? 0 : h < 16 ? 1 : h < 19 ? 2 : 3];
        return res.getString(R.string.panchangam_until, name, nazhigai(end - sunrise), part, clock(end));
    }

    private static String thithiName(String[] names, int index) {
        return index == 14 ? names[14] : index == 29 ? names[15] : names[index % 15];
    }
}
