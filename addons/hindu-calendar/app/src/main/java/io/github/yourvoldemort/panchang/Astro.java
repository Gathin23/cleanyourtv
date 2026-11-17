package io.github.yourvoldemort.panchang;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Solar and lunar positions, the Lahiri ayanamsa and sunrise/sunset, computed on the TV.
 *
 * The series are the truncated ones from Jean Meeus, Astronomical Algorithms (2nd ed.):
 * chapter 25 for the Sun and chapter 47 for the Moon. Good to about 0.01 degrees, which is
 * well inside the minute-or-two this add-on claims for an end time. Nothing here goes online.
 */
final class Astro {
    private static final double DEG = Math.PI / 180;
    private static final double J2000 = 2451545.0;

    /** Meeus table 47.A: D, M, M', F and the longitude coefficient in millionths of a degree. */
    private static final int[][] MOON_TERMS = {
            {0, 0, 1, 0, 6288774}, {2, 0, -1, 0, 1274027}, {2, 0, 0, 0, 658314},
            {0, 0, 2, 0, 213618}, {0, 1, 0, 0, -185116}, {0, 0, 0, 2, -114332},
            {2, 0, -2, 0, 58793}, {2, -1, -1, 0, 57066}, {2, 0, 1, 0, 53322},
            {2, -1, 0, 0, 45758}, {0, 1, -1, 0, -40923}, {1, 0, 0, 0, -34720},
            {0, 1, 1, 0, -30383}, {2, 0, 0, -2, 15327}, {0, 0, 1, 2, -12528},
            {0, 0, 1, -2, 10980}, {4, 0, -1, 0, 10675}, {0, 0, 3, 0, 10034},
            {4, 0, -2, 0, 8548}, {2, 1, -1, 0, -7888}, {2, 1, 0, 0, -6766},
            {1, 0, -1, 0, -5163}, {1, 1, 0, 0, 4987}, {2, -1, 1, 0, 4036},
            {2, 0, 2, 0, 3994}, {4, 0, 0, 0, 3861}, {2, 0, -3, 0, 3665},
            {0, 1, -2, 0, -2689}, {2, 0, -1, 2, -2602}, {2, -1, -2, 0, 2390},
            {1, 0, 1, 0, -2348}, {2, -2, 0, 0, 2236}, {0, 1, 2, 0, -2120},
            {0, 2, 0, 0, -2069}, {2, -2, -1, 0, 2048}, {2, 0, 1, -2, -1773},
            {2, 0, 0, 2, -1595}, {4, -1, -1, 0, 1215}, {0, 0, 2, 2, -1110},
            {3, 0, -1, 0, -892}, {2, 1, 1, 0, -810}, {4, -1, -2, 0, 759},
            {0, 2, -1, 0, -713}, {2, 2, -1, 0, -700}, {2, 1, -2, 0, 691},
            {2, -1, 0, -2, 596}, {4, 0, 1, 0, 549}, {0, 0, 4, 0, 537},
            {4, -1, 0, 0, 520}, {1, 0, -2, 0, -487}, {2, 1, 0, -2, -399},
            {0, 0, 2, -2, -381}, {1, 1, 1, 0, 351}, {3, 0, -2, 0, -340},
            {4, 0, -3, 0, 330}, {2, -1, 2, 0, 327}, {0, 2, 1, 0, -323},
            {1, 1, -1, 0, 299}, {2, 0, 3, 0, 294},
    };

    static final ZoneOffset UTC = ZoneOffset.UTC;

    private Astro() {}

    static double julianDay(long millis) { return millis / 86_400_000.0 + 2440587.5; }

    static long millis(double jd) { return Math.round((jd - 2440587.5) * 86_400_000.0); }

    /** The angle in [0, 360). */
    static double norm(double degrees) {
        double d = degrees % 360;
        return d < 0 ? d + 360 : d;
    }

    /** The angle in (-180, 180]. */
    static double signed180(double degrees) {
        double d = norm(degrees);
        return d > 180 ? d - 360 : d;
    }

    private static double sin(double degrees) { return Math.sin(degrees * DEG); }
    private static double cos(double degrees) { return Math.cos(degrees * DEG); }

    /** Apparent geocentric longitude of the Sun in degrees. */
    static double sunLongitude(double jd) {
        double t = (jd - J2000) / 36525;
        double meanLongitude = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        double meanAnomaly = 357.52911 + 35999.05029 * t - 0.0001537 * t * t;
        double centre = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(meanAnomaly)
                + (0.019993 - 0.000101 * t) * sin(2 * meanAnomaly)
                + 0.000289 * sin(3 * meanAnomaly);
        double node = 125.04 - 1934.136 * t;
        return norm(meanLongitude + centre - 0.00569 - 0.00478 * sin(node));
    }

    /** Mean longitude of the Sun in degrees, the term sunrise needs for the equation of time. */
    static double sunMeanLongitude(double jd) {
        double t = (jd - J2000) / 36525;
        return 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
    }

    static double obliquity(double jd) {
        double t = (jd - J2000) / 36525;
        return 23.439291 - 0.0130042 * t - 1.64e-7 * t * t + 5.04e-7 * t * t * t;
    }

    /** Apparent geocentric longitude of the Moon in degrees. */
    static double moonLongitude(double jd) {
        double t = (jd - J2000) / 36525;
        double meanLongitude = 218.3164477 + 481267.88123421 * t - 0.0015786 * t * t
                + t * t * t / 538841 - t * t * t * t / 65194000;
        double elongation = 297.8501921 + 445267.1114034 * t - 0.0018819 * t * t
                + t * t * t / 545868 - t * t * t * t / 113065000;
        double sunAnomaly = 357.5291092 + 35999.0502909 * t - 0.0001536 * t * t + t * t * t / 24490000;
        double moonAnomaly = 134.9633964 + 477198.8675055 * t + 0.0087414 * t * t
                + t * t * t / 69699 - t * t * t * t / 14712000;
        double argument = 93.2720950 + 483202.0175233 * t - 0.0036539 * t * t
                - t * t * t / 3526000 + t * t * t * t / 863310000;
        double sum = 0;
        for (int[] term : MOON_TERMS) {
            sum += term[4] * sin(term[0] * elongation + term[1] * sunAnomaly
                    + term[2] * moonAnomaly + term[3] * argument);
        }
        double venus = 119.75 + 131.849 * t;
        double jupiter = 53.09 + 479264.290 * t;
        sum += 3958 * sin(venus) + 1962 * sin(meanLongitude - argument) + 318 * sin(jupiter);
        return norm(meanLongitude + sum / 1e6);
    }

    /**
     * Lahiri (Chitrapaksha) ayanamsa in degrees: 23 degrees 51 minutes 11 seconds at J2000,
     * moving about 50.29 seconds a year. Good to an arcminute for the years a TV will see.
     */
    static double ayanamsa(double jd) {
        return 23.85306 + (jd - J2000) / 365.25 * 50.2888 / 3600;
    }

    /** Sidereal longitudes in degrees, the frame a panchang names nakshatras and rashis in. */
    static double siderealSun(double jd) { return norm(sunLongitude(jd) - ayanamsa(jd)); }

    static double siderealMoon(double jd) { return norm(moonLongitude(jd) - ayanamsa(jd)); }

    /** Moon minus Sun, 0 at new moon and 180 at full moon, increasing through a lunar month. */
    static double elongation(double jd) { return norm(moonLongitude(jd) - sunLongitude(jd)); }

    interface Angle { double at(double jd); }

    /** Julian day of the last time the angle crossed the target going up, at or before jd. */
    static double previousCrossing(double jd, double target, Angle angle) {
        double after = jd;
        for (int step = 0; step < 170; step++) {
            double before = after - 0.25;
            if (crossed(angle, before, after, target)) return bisect(angle, before, after, target);
            after = before;
        }
        return jd;
    }

    static double nextCrossing(double jd, double target, Angle angle) {
        double before = jd;
        for (int step = 0; step < 170; step++) {
            double after = before + 0.25;
            if (crossed(angle, before, after, target)) return bisect(angle, before, after, target);
            before = after;
        }
        return jd;
    }

    private static boolean crossed(Angle angle, double before, double after, double target) {
        double a = signed180(angle.at(before) - target);
        double b = signed180(angle.at(after) - target);
        return a < 0 && b >= 0 && b - a < 30;
    }

    /** The angle rises about 1 to 15 degrees a day, so 40 halvings beat a second. */
    private static double bisect(Angle angle, double before, double after, double target) {
        for (int i = 0; i < 40; i++) {
            double mid = (before + after) / 2;
            if (signed180(angle.at(mid) - target) < 0) before = mid;
            else after = mid;
        }
        return (before + after) / 2;
    }

    /**
     * Sunrise and sunset as epoch milliseconds on the given local date, or null where the Sun
     * stays up or down all day. NOAA's equations with the standard 90.833 degree zenith.
     */
    static long[] sunriseSunset(LocalDate date, City city) {
        ZonedDateTime noon = date.atTime(12, 0).atZone(city.zone);
        double jd = julianDay(noon.toInstant().toEpochMilli());
        double noonUtcMinutes = 720 - 4 * city.lon - 4 * equationOfTimeDegrees(jd);
        double declination = declination(jd);
        double cosHourAngle = (cos(90.833) - sin(city.lat) * sin(declination))
                / (cos(city.lat) * cos(declination));
        if (cosHourAngle < -1 || cosHourAngle > 1) return null;
        double hourAngle = Math.acos(cosHourAngle) / DEG;
        long midnight = date.atStartOfDay(UTC).toInstant().toEpochMilli();
        long rise = midnight + Math.round((noonUtcMinutes - 4 * hourAngle) * 60_000);
        long set = midnight + Math.round((noonUtcMinutes + 4 * hourAngle) * 60_000);
        rise = onDate(rise, date, city.zone);
        set = onDate(set, date, city.zone);
        if (set <= rise) set += 86_400_000L;
        return new long[]{rise, set};
    }

    private static double declination(double jd) {
        double e = obliquity(jd);
        return Math.asin(Math.sin(e * DEG) * sin(sunLongitude(jd))) / DEG;
    }

    /** Equation of time in degrees: mean longitude minus apparent right ascension. */
    private static double equationOfTimeDegrees(double jd) {
        double e = obliquity(jd);
        double lambda = sunLongitude(jd);
        double rightAscension = Math.atan2(cos(e) * sin(lambda), cos(lambda)) / DEG;
        return signed180(sunMeanLongitude(jd) - 0.0057183 - rightAscension);
    }

    /** The same clock time moved onto the wanted local date, for cities with a large offset. */
    private static long onDate(long millis, LocalDate date, ZoneId zone) {
        LocalDate at = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
        if (at.isBefore(date)) return millis + 86_400_000L;
        if (at.isAfter(date)) return millis - 86_400_000L;
        return millis;
    }
}
