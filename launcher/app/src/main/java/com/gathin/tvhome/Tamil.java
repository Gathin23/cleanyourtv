package com.gathin.tvhome;

import android.content.res.Resources;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Tamil solar calendar date, e.g. "ஐப்பசி 10 · பராபவ", without ICU or a network. A month starts when the sun enters
 * the next sidereal sign (Lahiri ayanamsa); by the Tamil rule, a sign entered before sunset makes that day the 1st,
 * one entered after sunset the next day. Sunset is Chennai's, the sun's longitude Jean Meeus' low-precision formula
 * (about 0.01 degree, some fifteen minutes of time), so only a sign change within minutes of sunset can be off a day.
 */
final class Tamil {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final double LATITUDE = 13.08, LONGITUDE = 80.27;

    private Tamil() {
    }

    /** Today's Tamil date in Tamil script. */
    static String today(Resources res) {
        int[] t = fromGregorian(LocalDate.now(IST));
        return res.getString(R.string.tamil_date_format, res.getStringArray(R.array.tamil_months)[t[1]], t[2],
                res.getStringArray(R.array.tamil_years)[t[0]]);
    }

    /** {year in the 60-year cycle (0 = Prabhava), month (0 = Chithirai), day of the month}. */
    static int[] fromGregorian(LocalDate date) {
        int sign = signAtSunset(date);
        LocalDate first = date;
        while (signAtSunset(first.minusDays(1)) == sign) first = first.minusDays(1);
        int day = (int) ChronoUnit.DAYS.between(first, date) + 1;
        // The year starts on Chithirai 1 in April; Margazhi to Panguni in January to April still belong to last year's.
        int startYear = date.getMonthValue() <= 4 && sign >= 8 ? date.getYear() - 1 : date.getYear();
        return new int[]{Math.floorMod(startYear - 1987, 60), sign, day};
    }

    /** The sidereal sign (0 = Mesha) the sun is in at Chennai's sunset on a date. */
    static int signAtSunset(LocalDate date) {
        return (int) Math.floor(siderealLongitude(jd(date, sunriseAndSunset(date)[1])) / 30);
    }

    /** Chennai's sunrise and sunset, in minutes after local midnight. */
    static int[] sunriseAndSunset(LocalDate date) {
        return Sun.riseAndSet(date, LATITUDE, LONGITUDE, IST);
    }

    /** Julian day (UT) of a time in India, given in minutes after midnight of a date (may pass 1440). */
    static double jd(LocalDate date, double minutes) {
        return Lunar.jdFromDate(date.getDayOfMonth(), date.getMonthValue(), date.getYear()) - 0.5 + (minutes - 330) / 1440;
    }

    /** The sun's apparent longitude less the Lahiri ayanamsa, in degrees, at a Julian day (UT). */
    static double siderealLongitude(double jd) {
        return degrees(sunLongitude(jd) - ayanamsa(jd));
    }

    /** The sun's apparent (tropical) longitude in degrees, Meeus chapter 25, low precision. */
    static double sunLongitude(double jd) {
        double t = (jd - 2451545) / 36525, dr = Math.PI / 180;
        double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        double m = (357.52911 + 35999.05029 * t - 0.0001537 * t * t) * dr;
        double c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(m)
                + (0.019993 - 0.000101 * t) * Math.sin(2 * m) + 0.000289 * Math.sin(3 * m);
        return degrees(l0 + c - 0.00569 - 0.00478 * Math.sin((125.04 - 1934.136 * t) * dr));
    }

    /** Lahiri (Chitrapaksha) ayanamsa in degrees. */
    static double ayanamsa(double jd) {
        return 23.853 + 0.0139663 * (jd - 2451545) / 365.25;
    }

    /** An angle brought into [0, 360). */
    static double degrees(double a) {
        return a - 360 * Math.floor(a / 360);
    }
}
