package io.github.yourvoldemort.panchang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.Test;

/**
 * The five limbs are read at sunrise, as printed panchangs read them. The dates below are festivals
 * whose tithi a panchang states for that morning, so they pin both the arithmetic and the names.
 */
public class PanchangTest {

    private static Panchang at(String date, String city) {
        return Panchang.at(LocalDate.parse(date), City.cityNamed(city));
    }

    private static int minutes(Panchang p, long millis) {
        ZonedDateTime local = Instant.ofEpochMilli(millis).atZone(City.cityNamed("Delhi").zone);
        return local.getHour() * 60 + local.getMinute();
    }

    @Test
    public void diwali2024IsKartikAmavasya() {
        Panchang p = at("2024-11-01", "Delhi");
        assertEquals(30, p.tithi);
        assertEquals("Amavasya", Panchang.tithiName(p.tithi));
        assertTrue(Panchang.krishna(p.tithi));

        MonthSpan month = MonthSpan.of(p.sunrise, City.cityNamed("Delhi"), Region.NORTH);
        assertEquals("Kartik", Region.NORTH.monthLatin(month));
        // The dark fortnight counts Krishna Pratipada as day 16, so Amavasya is day 15.
        assertEquals(15, month.dayNumber(LocalDate.parse("2024-11-01")));
    }

    @Test
    public void holi2025IsPhalgunPurnima() {
        Panchang p = at("2025-03-14", "Delhi");
        assertEquals(15, p.tithi);
        assertEquals("Purnima", Panchang.tithiName(15));
        assertEquals(30, MonthSpan.of(p.sunrise, City.cityNamed("Delhi"), Region.NORTH)
                .dayNumber(LocalDate.parse("2025-03-14")));
    }

    @Test
    public void janmashtami2024IsBhadrapadKrishnaAshtami() {
        Panchang p = at("2024-08-26", "Delhi");
        assertEquals(23, p.tithi);
        assertTrue(Panchang.krishna(p.tithi));
        assertEquals(8, Panchang.lunarDay(p.tithi));
        assertEquals("Bhadrapad", Region.NORTH.monthLatin(
                MonthSpan.of(p.sunrise, City.cityNamed("Delhi"), Region.NORTH)));
    }

    @Test
    public void theOtherLimbsAreReadAtSunriseToo() {
        Panchang p = at("2024-11-01", "Delhi");
        assertEquals("Swati", Panchang.NAKSHATRA[p.nakshatra - 1]);
        assertEquals("Priti", Panchang.YOGA[p.yoga - 1]);
        assertEquals("Naga", Panchang.karanaName(p.karana));
        assertEquals(15, p.nakshatra);
        assertEquals(2, p.yoga);
    }

    @Test
    public void everyLimbEndsAfterTheSunriseThatReadIt() {
        Panchang p = at("2024-11-01", "Delhi");
        long dayAndAHalf = p.sunrise + 36L * 60 * 60 * 1000;
        for (long end : new long[]{p.tithiEnd, p.nakshatraEnd, p.yogaEnd, p.karanaEnd}) {
            assertTrue("a limb ends after sunrise", end > p.sunrise);
            assertTrue("and within a day and a half of it", end < dayAndAHalf);
        }
    }

    @Test
    public void sunriseAndSunsetFollowTheCity() {
        // Delhi, 30 June 2026: 05:26 and 19:23 IST. London the same day: 04:47 and 21:21 BST.
        Panchang delhi = at("2026-06-30", "Delhi");
        assertTrue(delhi.hasSunrise);
        assertTrue(Math.abs(minutes(delhi, delhi.sunrise) - (5 * 60 + 26)) <= 3);
        assertTrue(Math.abs(minutes(delhi, delhi.sunset) - (19 * 60 + 23)) <= 3);

        Panchang london = at("2026-06-30", "London");
        assertTrue(london.hasSunrise);
        ZonedDateTime rise = Instant.ofEpochMilli(london.sunrise)
                .atZone(City.cityNamed("London").zone);
        ZonedDateTime set = Instant.ofEpochMilli(london.sunset)
                .atZone(City.cityNamed("London").zone);
        assertTrue(Math.abs(rise.getHour() * 60 + rise.getMinute() - (4 * 60 + 47)) <= 3);
        assertTrue(Math.abs(set.getHour() * 60 + set.getMinute() - (21 * 60 + 21)) <= 3);
    }

    @Test
    public void karanaRunsThroughItsSevenMovableNames() {
        assertEquals("Kimstughna", Panchang.karanaName(0));
        String[] movable = {"Bava", "Balava", "Kaulava", "Taitila", "Gara", "Vanija", "Vishti"};
        for (int index = 1; index <= 56; index++) {
            assertEquals(movable[(index - 1) % 7], Panchang.karanaName(index));
        }
        assertEquals("Vishti", Panchang.karanaName(56));
        assertEquals("Shakuna", Panchang.karanaName(57));
        assertEquals("Chatushpada", Panchang.karanaName(58));
        assertEquals("Naga", Panchang.karanaName(59));
    }

    @Test
    public void tithiNamesSplitAtPurnima() {
        assertEquals("Pratipada", Panchang.tithiName(1));
        assertEquals("Chaturdashi", Panchang.tithiName(14));
        assertEquals("Purnima", Panchang.tithiName(15));
        assertEquals("Pratipada", Panchang.tithiName(16));
        assertEquals("Chaturdashi", Panchang.tithiName(29));
        assertEquals("Amavasya", Panchang.tithiName(30));
        assertFalse(Panchang.krishna(15));
        assertTrue(Panchang.krishna(16));
        assertEquals("Shukla", Panchang.paksha(1));
        assertEquals("Krishna", Panchang.paksha(22));
    }
}
