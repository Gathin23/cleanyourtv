package io.github.yourvoldemort.panchang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

/**
 * The month a day belongs to, in each of the five calendars. The Gregorian dates are the ones
 * printed panchangs and wall calendars give for 2023 and 2026, the two years around today that hold
 * an adhika month in the north Indian reckoning.
 */
public class MonthSpanTest {

    private static MonthSpan span(String date, String city, Region region) {
        City place = City.cityNamed(city);
        Panchang p = Panchang.at(LocalDate.parse(date), place);
        return MonthSpan.of(p.sunrise, place, region);
    }

    @Test
    public void purnimantaMonthsRunPurnimaToPurnima() {
        // 2026 in the north: the same table a printed panchang gives, with Jyeshtha twice over.
        assertSpan("2026-03-20", Region.NORTH, 0, "2026-03-04", "2026-04-02", false);
        assertSpan("2026-04-10", Region.NORTH, 1, "2026-04-03", "2026-05-01", false);
        assertSpan("2026-05-10", Region.NORTH, 1, "2026-05-02", "2026-05-16", false);
        assertSpan("2026-05-20", Region.NORTH, 2, "2026-05-17", "2026-06-15", true);
        assertSpan("2026-06-20", Region.NORTH, 2, "2026-06-16", "2026-06-30", false);
        assertSpan("2026-07-10", Region.NORTH, 3, "2026-07-01", "2026-07-29", false);
        assertSpan("2026-08-10", Region.NORTH, 4, "2026-07-30", "2026-08-28", false);
    }

    @Test
    public void adhikaJyeshtha2026IsOneWholeCycle() {
        MonthSpan adhika = span("2026-05-25", "Delhi", Region.NORTH);
        assertTrue(adhika.adhika);
        assertEquals("Jyeshtha", Region.NORTH.monthLatin(adhika));
        assertEquals(LocalDate.parse("2026-05-17"), adhika.firstDate());
        assertEquals(LocalDate.parse("2026-06-15"), adhika.lastDate());

        MonthSpan nija = adhika.next();
        assertFalse(nija.adhika);
        assertEquals("Jyeshtha", Region.NORTH.monthLatin(nija));
        assertEquals(LocalDate.parse("2026-06-16"), nija.firstDate());
        // Krishna Pratipada of that month was a kshaya tithi, so the month opens on Dwitiya, day 17.
        assertEquals(17, nija.dayNumber(LocalDate.parse("2026-06-16")));
    }

    @Test
    public void adhikaShravan2023IsOneWholeCycle() {
        MonthSpan adhika = span("2023-07-25", "Delhi", Region.NORTH);
        assertTrue(adhika.adhika);
        assertEquals("Shravan", Region.NORTH.monthLatin(adhika));
        assertEquals(LocalDate.parse("2023-07-18"), adhika.firstDate());
        assertEquals(LocalDate.parse("2023-08-16"), adhika.lastDate());

        MonthSpan before = adhika.previous();
        assertEquals("Ashadh", Region.NORTH.monthLatin(before));
        assertEquals(LocalDate.parse("2023-07-17"), before.lastDate());
        assertEquals("the month before an adhika one stops at the new moon that opens it",
                LocalDate.parse("2023-07-04"), before.firstDate());

        MonthSpan after = adhika.next();
        assertFalse(after.adhika);
        assertEquals("Shravan", Region.NORTH.monthLatin(after));
        assertEquals(LocalDate.parse("2023-08-17"), after.firstDate());
    }

    @Test
    public void amantaMonthsRunNewMoonToNewMoon() {
        // Gujarati Kartak opens the Gujarati year on 2 November 2024, the day after Diwali.
        MonthSpan kartak = span("2024-11-02", "Ahmedabad", Region.GUJARATI);
        assertEquals("Kartak", Region.GUJARATI.monthLatin(kartak));
        assertEquals(LocalDate.parse("2024-11-02"), kartak.firstDate());
        assertEquals(LocalDate.parse("2024-12-01"), kartak.lastDate());
        assertEquals(1, kartak.dayNumber(LocalDate.parse("2024-11-02")));
        assertEquals(2081, Region.GUJARATI.year(kartak));

        MonthSpan aso = kartak.previous();
        assertEquals("Aso", Region.GUJARATI.monthLatin(aso));
        assertEquals(LocalDate.parse("2024-11-01"), aso.lastDate());
        assertEquals(2080, Region.GUJARATI.year(aso));
    }

    @Test
    public void tamilMonthsOpenOnTheSankrantiDate() {
        MonthSpan chithirai = span("2026-04-14", "Chennai", Region.TAMIL);
        assertEquals("Chithirai", Region.TAMIL.monthLatin(chithirai));
        assertEquals(LocalDate.parse("2026-04-14"), chithirai.firstDate());
        assertEquals(LocalDate.parse("2026-05-14"), chithirai.lastDate());
        assertEquals(1, chithirai.dayNumber(LocalDate.parse("2026-04-14")));
        assertEquals(1948, Region.TAMIL.year(chithirai));

        MonthSpan panguni = span("2026-04-13", "Chennai", Region.TAMIL);
        assertEquals("Panguni", Region.TAMIL.monthLatin(panguni));
        assertEquals(LocalDate.parse("2026-03-15"), panguni.firstDate());
        assertEquals(LocalDate.parse("2026-04-13"), panguni.lastDate());
        assertEquals(1947, Region.TAMIL.year(panguni));
    }

    @Test
    public void bengaliMonthsStartTheMorningAfterTheSankranti() {
        // 14 April 2026 is Choitro 31 in Bengal; Boishakh 1 and Bangabda 1433 are the 15th.
        MonthSpan choitro = span("2026-04-14", "Kolkata", Region.BENGALI);
        assertEquals("Choitro", Region.BENGALI.monthLatin(choitro));
        assertEquals(LocalDate.parse("2026-04-14"), choitro.lastDate());
        assertEquals(1432, Region.BENGALI.year(choitro));

        MonthSpan boishakh = span("2026-04-15", "Kolkata", Region.BENGALI);
        assertEquals("Boishakh", Region.BENGALI.monthLatin(boishakh));
        assertEquals(LocalDate.parse("2026-04-15"), boishakh.firstDate());
        assertEquals(1, boishakh.dayNumber(LocalDate.parse("2026-04-15")));
        assertEquals(1433, Region.BENGALI.year(boishakh));
    }

    @Test
    public void theSolarYearTurnsAtMesha() {
        // Thai and Maasi still belong to the year that opened on 14 April before them.
        assertEquals(1947, Region.TAMIL.year(span("2026-01-14", "Chennai", Region.TAMIL)));
        assertEquals(1948, Region.TAMIL.year(span("2026-04-20", "Chennai", Region.TAMIL)));
        assertEquals(1949, Region.TAMIL.year(span("2027-04-20", "Chennai", Region.TAMIL)));
        assertEquals(1432, Region.BENGALI.year(span("2026-01-15", "Kolkata", Region.BENGALI)));
        assertEquals(1433, Region.BENGALI.year(span("2026-04-20", "Kolkata", Region.BENGALI)));
    }

    @Test
    public void lunarYearsDifferByRegionOnTheSameDay() {
        // 25 March 2026: four calendars, four different year numbers over one Gregorian date.
        assertEquals(2083, Region.NORTH.year(span("2026-03-25", "Delhi", Region.NORTH)));
        assertEquals(2082, Region.GUJARATI.year(span("2026-03-25", "Ahmedabad", Region.GUJARATI)));
        assertEquals(1948, Region.MARATHI.year(span("2026-03-25", "Pune", Region.MARATHI)));
        assertEquals(1947, Region.TAMIL.year(span("2026-03-25", "Chennai", Region.TAMIL)));
        assertEquals(1432, Region.BENGALI.year(span("2026-03-25", "Kolkata", Region.BENGALI)));
    }

    @Test
    public void monthsFollowOneAnotherWithoutAGap() {
        for (Region region : Region.values()) {
            City city = City.cityNamed(region.solar ? "Chennai" : "Delhi");
            LocalDate date = MonthSpan.of(Panchang.at(LocalDate.parse("2025-01-01"), city).sunrise,
                    city, region).firstDate();
            for (int step = 0; step < 24; step++) {
                Panchang p = Panchang.at(date, city);
                MonthSpan month = MonthSpan.of(p.sunrise, city, region);
                assertEquals(region + " on " + date, date, month.firstDate());
                MonthSpan next = month.next();
                assertEquals(region + " on " + date, month.lastDate().plusDays(1), next.firstDate());
                assertTrue(region + " day numbers stay positive", month.dayNumber(date) >= 1);
                date = next.firstDate();
            }
        }
    }

    private static void assertSpan(String date, Region region, int name, String first, String last,
                                   boolean adhika) {
        MonthSpan month = span(date, "Delhi", region);
        assertEquals(region + " month on " + date, name, month.name);
        assertEquals(region + " first date", LocalDate.parse(first), month.firstDate());
        assertEquals(region + " last date", LocalDate.parse(last), month.lastDate());
        assertEquals(region + " adhika on " + date, adhika, month.adhika);
    }
}
