package io.github.yourvoldemort.panchang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.Test;

/**
 * The three inauspicious windows divide the chosen city's daylight into eight and take the part the
 * weekday gives them; Abhijit is the middle of the fifteen muhurtas of the day.
 */
public class MuhurtaTest {
    private static final City DELHI = City.cityNamed("Delhi");

    private static Panchang at(LocalDate date) { return Panchang.at(date, DELHI); }

    /** Which eighth of the daylight a start falls in, 1 to 8. */
    private static int eighth(Panchang p, long start) {
        return (int) Math.round((start - p.sunrise) * 8.0 / (p.sunset - p.sunrise)) + 1;
    }

    private static int minuteOfDay(long millis) {
        ZonedDateTime time = Instant.ofEpochMilli(millis).atZone(DELHI.zone);
        return time.getHour() * 60 + time.getMinute();
    }

    @Test
    public void theWeekdayPicksTheEighthOfTheDaylight() {
        // 2026-06-28 is a Sunday, so this week runs Sunday to Saturday.
        int[] rahu = {8, 2, 7, 5, 6, 4, 3};
        int[] yamaganda = {5, 4, 3, 2, 1, 7, 6};
        int[] gulika = {7, 6, 5, 4, 3, 2, 1};
        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.of(2026, 6, 28).plusDays(i);
            assertEquals("Sunday is 1", i + 1, Muhurta.sundayFirst(date));
            Panchang p = at(date);
            Muhurta m = Muhurta.of(date, p);
            assertNotNull(m);
            assertEquals(rahu[i], eighth(p, m.rahuStart));
            assertEquals(yamaganda[i], eighth(p, m.yamagandaStart));
            assertEquals(gulika[i], eighth(p, m.gulikaStart));
        }
    }

    @Test
    public void everyWindowSitsInsideTheDaylight() {
        LocalDate date = LocalDate.of(2026, 6, 30); // a Tuesday
        Panchang p = at(date);
        Muhurta m = Muhurta.of(date, p);
        long daylightMinutes = (p.sunset - p.sunrise) / 60_000L;
        long[][] eighths = {{m.rahuStart, m.rahuEnd}, {m.yamagandaStart, m.yamagandaEnd},
                {m.gulikaStart, m.gulikaEnd}};
        for (long[] span : eighths) {
            assertTrue(span[0] >= p.sunrise && span[1] <= p.sunset);
            assertTrue(span[0] < span[1]);
            assertTrue("one eighth of the daylight, to the minute",
                    Math.abs((span[1] - span[0]) / 60_000L - daylightMinutes / 8.0) <= 1);
        }
    }

    @Test
    public void delhiOnATuesdayHasTheTablesUsualClocks() {
        // Delhi, 30 June 2026: sunrise 05:26 and sunset 19:23. Tuesday's parts are Rahu 7,
        // Yamaganda 3 and Gulika 5; Abhijit is the eighth of the day's fifteen muhurtas.
        Panchang p = at(LocalDate.of(2026, 6, 30));
        Muhurta m = Muhurta.of(LocalDate.of(2026, 6, 30), p);
        assertTrue(Math.abs(minuteOfDay(m.rahuStart) - (15 * 60 + 54)) <= 3);
        assertTrue(Math.abs(minuteOfDay(m.rahuEnd) - (17 * 60 + 38)) <= 3);
        assertTrue(Math.abs(minuteOfDay(m.yamagandaStart) - (8 * 60 + 55)) <= 3);
        assertTrue(Math.abs(minuteOfDay(m.yamagandaEnd) - (10 * 60 + 40)) <= 3);
        assertTrue(Math.abs(minuteOfDay(m.gulikaStart) - (12 * 60 + 25)) <= 3);
        assertTrue(Math.abs(minuteOfDay(m.gulikaEnd) - (14 * 60 + 9)) <= 3);
        assertTrue(Math.abs(minuteOfDay(m.abhijitStart) - (11 * 60 + 57)) <= 3);
        assertTrue(Math.abs(minuteOfDay(m.abhijitEnd) - (12 * 60 + 52)) <= 3);
    }

    @Test
    public void abhijitIsCentredOnTheMiddleOfTheDay() {
        LocalDate date = LocalDate.of(2026, 6, 30);
        Panchang p = at(date);
        Muhurta m = Muhurta.of(date, p);
        long midpoint = (p.sunrise + p.sunset) / 2;
        assertTrue(Math.abs((m.abhijitStart + m.abhijitEnd) / 2 - midpoint) <= 2);
        long daylight = p.sunset - p.sunrise;
        assertTrue(Math.abs((m.abhijitStart + m.abhijitEnd) / 2 - midpoint) <= 2);
        assertEquals(daylight * 7 / 15, m.abhijitStart - p.sunrise);
        assertEquals(daylight * 8 / 15, m.abhijitEnd - p.sunrise);
    }
}