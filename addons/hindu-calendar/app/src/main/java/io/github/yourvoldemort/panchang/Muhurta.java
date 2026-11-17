package io.github.yourvoldemort.panchang;

import java.time.LocalDate;

/**
 * The day's Rahu Kala, Yamaganda and Gulika, and the Abhijit muhurta.
 *
 * The three inauspicious windows divide the daylight into eight equal parts and give one part to
 * each weekday, in the tables printed panchangs use. Abhijit is the eighth of the day's fifteen
 * muhurtas, the one around solar noon. Everything here comes from the sunrise and sunset already
 * computed for the chosen city, so it needs no further astronomy.
 */
final class Muhurta {
    final long rahuStart, rahuEnd;
    final long yamagandaStart, yamagandaEnd;
    final long gulikaStart, gulikaEnd;
    final long abhijitStart, abhijitEnd;

    /** The eighth of the daylight each weekday gives up, Sunday first, 1 to 8. */
    private static final int[] RAHU = {0, 8, 2, 7, 5, 6, 4, 3};
    private static final int[] YAMAGANDA = {0, 5, 4, 3, 2, 1, 7, 6};
    private static final int[] GULIKA = {0, 7, 6, 5, 4, 3, 2, 1};

    private Muhurta(long sunrise, long sunset, int weekday) {
        long daylight = sunset - sunrise;
        rahuStart = sunrise + daylight * (RAHU[weekday] - 1) / 8;
        rahuEnd = sunrise + daylight * RAHU[weekday] / 8;
        yamagandaStart = sunrise + daylight * (YAMAGANDA[weekday] - 1) / 8;
        yamagandaEnd = sunrise + daylight * YAMAGANDA[weekday] / 8;
        gulikaStart = sunrise + daylight * (GULIKA[weekday] - 1) / 8;
        gulikaEnd = sunrise + daylight * GULIKA[weekday] / 8;
        abhijitStart = sunrise + daylight * 7 / 15;
        abhijitEnd = sunrise + daylight * 8 / 15;
    }

    /** The windows for that date, or null where the Sun does not rise. */
    static Muhurta of(LocalDate date, Panchang panchang) {
        if (!panchang.hasSunrise) return null;
        return new Muhurta(panchang.sunrise, panchang.sunset, sundayFirst(date));
    }

    /** 1 for Sunday through 7 for Saturday, the order the tables are written in. */
    static int sundayFirst(LocalDate date) { return date.getDayOfWeek().getValue() % 7 + 1; }
}