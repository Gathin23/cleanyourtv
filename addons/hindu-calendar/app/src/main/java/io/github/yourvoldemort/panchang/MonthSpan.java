package io.github.yourvoldemort.panchang;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * One month of the calendar the owner picked: a lunar month from new moon to new moon (or full moon
 * to full moon in the Purnimanta regions), or a solar month from one sankranti to the next.
 *
 * The panchang day starts at sunrise, so a lunar month's first date is the first sunrise after the
 * new moon or Purnima that opens it. A solar month is named for the civil date of its sankranti
 * instead, which is how Tamil and Bengali calendars print it.
 */
final class MonthSpan {
    /** Lunar: the month's name. Solar: the sidereal sign (rashi) that opens it. */
    final int name;
    final long startMillis, nextMillis;
    final boolean adhika, solar;
    /** Gregorian year the lunar year's Chaitra, or the solar year's Mesha sankranti, fell in. */
    final int chaitraYear, solarYear;
    final Region region;
    final City city;

    private MonthSpan(Region region, City city, int name, long start, long next,
                      boolean adhika, int chaitraYear, int solarYear) {
        this.region = region;
        this.city = city;
        this.name = name;
        this.startMillis = start;
        this.nextMillis = next;
        this.adhika = adhika;
        this.solar = solarYear != 0;
        this.chaitraYear = chaitraYear;
        this.solarYear = solarYear;
    }

    static MonthSpan of(long millis, City city, Region region) {
        double jd = Astro.julianDay(millis);
        if (region.solar) return solarMonth(jd, city, region);
        MonthSpan amanta = amanta(jd, city, region);
        return region.purnimanta ? purnimanta(jd, city, region, amanta) : amanta;
    }

    /** The month holding that civil date, read at its sunrise as the day screen reads it. */
    static MonthSpan of(LocalDate date, City city, Region region) {
        long[] riseSet = Astro.sunriseSunset(date, city);
        long millis = riseSet != null ? riseSet[0]
                : date.atTime(12, 0).atZone(city.zone).toInstant().toEpochMilli();
        return of(millis, city, region);
    }

    /** New moon to new moon, the month the Moon's cycle gives on its own. */
    private static MonthSpan amanta(double jd, City city, Region region) {
        double newMoon = Astro.previousCrossing(jd, 0, Astro::elongation);
        double nextNewMoon = Astro.nextCrossing(newMoon + 0.01, 0, Astro::elongation);
        return new MonthSpan(region, city, amantaName(newMoon), Astro.millis(newMoon),
                Astro.millis(nextNewMoon), sameSign(newMoon, nextNewMoon), chaitraYear(jd), 0);
    }

    /**
     * The Purnimanta month around jd. It runs Purnima to Purnima and is named for the lunar month
     * that holds its closing Purnima: a Krishna paksha is named for the month that follows it, a
     * Shukla paksha for the one it sits in.
     *
     * An adhika month is the exception, and the reason this is not simply Purnima to Purnima: it is
     * one whole lunar cycle with no sankranti in it, so it keeps its amanta boundaries and label,
     * and the months on either side stop at the new moons that open and close it.
     */
    private static MonthSpan purnimanta(double jd, City city, Region region, MonthSpan amanta) {
        if (amanta.adhika) return amanta;
        boolean krishna = Astro.elongation(jd) >= 180;
        MonthSpan neighbour = krishna ? amantaNext(amanta) : amantaPrevious(amanta);
        double fullMoon = Astro.previousCrossing(jd, 180, Astro::elongation);
        double closing = Astro.nextCrossing(fullMoon + 0.01, 180, Astro::elongation);
        long start, next;
        if (krishna) {
            start = Astro.millis(fullMoon);
            next = neighbour.adhika ? neighbour.startMillis : Astro.millis(closing);
        } else {
            // A Shukla paksha that follows an adhika month starts at its new moon, not at the Purnima.
            start = neighbour.adhika ? amanta.startMillis : Astro.millis(fullMoon);
            next = Astro.millis(closing);
        }
        // A Krishna paksha takes the next month's name, unless that month is adhika: then this side of
        // the seam keeps the month it is still the dark half of.
        int name = krishna && !neighbour.adhika ? neighbour.name : amanta.name;
        return new MonthSpan(region, city, name, start, next, false, amanta.chaitraYear, 0);
    }

    private static MonthSpan amantaNext(MonthSpan amanta) {
        return amanta(Astro.julianDay(amanta.nextMillis + 1_000L), amanta.city, amanta.region);
    }

    private static MonthSpan amantaPrevious(MonthSpan amanta) {
        return amanta(Astro.julianDay(amanta.startMillis - 1_000L), amanta.city, amanta.region);
    }

    MonthSpan next() { return of(nextMillis + 1_000L, city, region); }

    MonthSpan previous() { return of(startMillis - 1_000L, city, region); }

    /** The first civil date that belongs to this month. */
    LocalDate firstDate() {
        LocalDate date = Instant.ofEpochMilli(startMillis).atZone(city.zone).toLocalDate();
        if (solar) return date;
        return startMillis < sunriseOrNoon(date) ? date : date.plusDays(1);
    }

    LocalDate lastDate() { return next().firstDate().minusDays(1); }

    /** The number printed in a grid cell for that day, 1 to 30 (lunar) or 1 to 32 (solar). */
    int dayNumber(LocalDate date) {
        if (solar) return (int) (date.toEpochDay() - firstDate().toEpochDay()) + 1;
        int tithi = Panchang.tithiAt(sunriseOrNoon(date));
        // A Purnimanta month counts Krishna Pratipada as day 1; an adhika month keeps amanta numbering.
        return region.purnimanta && !adhika ? (tithi <= 15 ? 15 + tithi : tithi - 15) : tithi;
    }

    /**
     * A solar month: one sidereal sign, opened by its sankranti. Tamil months take the sankranti's
     * own civil date as day 1; in Bengal the sankranti day still closes the old month, so the new
     * one starts the morning after.
     */
    private static MonthSpan solarMonth(double jd, City city, Region region) {
        int sign = (int) Math.floor(Astro.siderealSun(jd) / 30);
        LocalDate today = Instant.ofEpochMilli(Astro.millis(jd)).atZone(city.zone).toLocalDate();
        LocalDate start = sankrantiDate(
                Astro.previousCrossing(jd, boundary(sign), Astro::siderealSun), city, region);
        // Bengal starts the month the morning after its sankranti, so on the sankranti day itself
        // the new sign is already overhead but the old month still owns the date.
        if (start.isAfter(today)) {
            sign = (sign + 11) % 12;
            start = sankrantiDate(Astro.previousCrossing(
                    Astro.julianDay(start.atStartOfDay(city.zone).toInstant().toEpochMilli()) - 0.01,
                    boundary(sign), Astro::siderealSun), city, region);
        }
        LocalDate next = nextSankrantiDate(sign, start, city, region);
        // The sankranti that closes this month can fall later today, after the sunrise that gave
        // us jd: the new month opens today all the same.
        if (!next.isAfter(today)) {
            sign = (sign + 1) % 12;
            start = next;
            next = nextSankrantiDate(sign, start, city, region);
        }
        LocalDate mesha = meshaDate(start.getYear(), city, region);
        int solarYear = start.isBefore(mesha) ? start.getYear() - 1 : start.getYear();
        return new MonthSpan(region, city, sign,
                start.atStartOfDay(city.zone).toInstant().toEpochMilli(),
                next.atStartOfDay(city.zone).toInstant().toEpochMilli(),
                false, 0, solarYear);
    }

    private static LocalDate nextSankrantiDate(int sign, LocalDate start, City city, Region region) {
        double from = Astro.julianDay(
                start.atStartOfDay(city.zone).toInstant().toEpochMilli()) + 0.01;
        return sankrantiDate(Astro.nextCrossing(from, boundary(sign + 1), Astro::siderealSun),
                city, region);
    }

    /** The civil date a solar month opens on, from the moment of its sankranti. */
    private static LocalDate sankrantiDate(double crossing, City city, Region region) {
        LocalDate date = Instant.ofEpochMilli(Astro.millis(crossing)).atZone(city.zone).toLocalDate();
        return region == Region.BENGALI ? date.plusDays(1) : date;
    }

    /** 0 and 12 are both 360 degrees: the crossing into Mesha. */
    private static double boundary(int sign) {
        int wrapped = sign % 12;
        return wrapped == 0 ? 360 : wrapped * 30;
    }

    /** The civil date the Mesha sankranti of that Gregorian year opened the solar year on. */
    private static LocalDate meshaDate(int year, City city, Region region) {
        double april = Astro.julianDay(LocalDate.of(year, 4, 1)
                .atStartOfDay(city.zone).toInstant().toEpochMilli());
        return sankrantiDate(Astro.nextCrossing(april - 10, 360, Astro::siderealSun), city, region);
    }

    static int yearOf(double jd) {
        return Instant.ofEpochMilli(Astro.millis(jd)).atZone(ZoneOffset.UTC).getYear();
    }

    private long sunriseOrNoon(LocalDate date) {
        long[] riseSet = Astro.sunriseSunset(date, city);
        return riseSet != null ? riseSet[0]
                : date.atTime(12, 0).atZone(city.zone).toInstant().toEpochMilli();
    }

    /** Chaitra in the year of the Sun's sign at the new moon: Pisces opens Chaitra. */
    private static int amantaName(double jd) {
        return (int) (Math.floor(Astro.siderealSun(jd + 0.01) / 30) + 1) % 12;
    }

    /**
     * Two new moons in the sign that holds the whole month mean no sankranti fell in it: it is
     * adhika. The offsets are a few seconds, so a sankranti minutes after a new moon still counts as
     * falling in the month it opens.
     */
    private static boolean sameSign(double start, double end) {
        double tick = 1e-4;
        int a = (int) Math.floor(Astro.siderealSun(start + tick) / 30);
        int b = (int) Math.floor(Astro.siderealSun(end - tick) / 30);
        return a == b;
    }

    /**
     * Gregorian year of the nija (not adhika) Chaitra that opened the lunar year of jd: the first new
     * moon after the Sun entered Pisces, which is the month Chaitra takes its name from.
     */
    private static int chaitraYear(double jd) {
        int year = yearOf(jd);
        double ingress = piscesIngress(year);
        if (ingress > jd) ingress = piscesIngress(year - 1);
        double newMoon = chaitra(ingress);
        if (newMoon > jd) {
            // Between the Sun entering Pisces and the new moon that follows it the year has not turned.
            newMoon = chaitra(piscesIngress(yearOf(ingress) - 1));
        }
        return yearOf(newMoon);
    }

    /** The moment the Sun enters Pisces that year, around 14 March. */
    private static double piscesIngress(int year) {
        double march = Astro.julianDay(LocalDate.of(year, 3, 1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        return Astro.nextCrossing(march - 10, 330, Astro::siderealSun);
    }

    /** The first new moon after a Pisces ingress that is not adhika: an adhika month opens no year. */
    private static double chaitra(double ingress) {
        double newMoon = Astro.nextCrossing(ingress - 0.01, 0, Astro::elongation);
        for (int guard = 0; guard < 3; guard++) {
            double next = Astro.nextCrossing(newMoon + 0.01, 0, Astro::elongation);
            if (!sameSign(newMoon, next)) return newMoon;
            newMoon = next;
        }
        return newMoon;
    }
}
