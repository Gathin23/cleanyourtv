package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tuesday 27-10-2026 in Chennai. Dates, sunrise and the weekday times are checked against a printed Tamil daily sheet;
 * the end times against Prokerala's thirukanitham panchangam (7.01 AM, 3.39 PM). The printed sheet is vakya and gives
 * 8.38 AM and 6.11 PM instead.
 */
public class PanchangamTest {

    private static final Panchangam SHEET = Panchangam.of(LocalDate.of(2026, 10, 27));

    private static void near(int expected, int actual, int minutes) {
        assertTrue("expected " + expected + " ±" + minutes + ", was " + actual, Math.abs(expected - actual) <= minutes);
    }

    @Test
    public void sunriseIsSixTwo() {
        near(6 * 60 + 2, SHEET.sunrise, 3);
    }

    @Test
    public void prathamaiEndsAt701() {
        // Krishna paksha prathamai (15).
        assertEquals(15, SHEET.thithi);
        near(7 * 60 + 1, SHEET.thithiEnd, 5);
    }

    @Test
    public void bharaniEndsAt339Pm() {
        assertEquals(1, SHEET.nakshatram);
        near(15 * 60 + 39, SHEET.nakshatramEnd, 5);
    }

    @Test
    public void hijriIsJumadaAlAwwal15() {
        assertArrayEquals(new int[]{1448, 5, 15}, SHEET.hijri);
    }

    @Test
    public void tuesdayTimes() {
        assertEquals(2, SHEET.weekday);
        assertEquals("3-4.30", Panchangam.slot(Panchangam.RAHU[2]));
        assertEquals("12-1.30", Panchangam.slot(Panchangam.KULIGAI[2]));
        assertEquals("9-10.30", Panchangam.slot(Panchangam.EMAGANDAM[2]));
        assertEquals(2, Panchangam.SOOLAM[2]);
    }

    @Test
    public void nazhigaiAsPrinted() {
        assertEquals("6.30", Panchangam.nazhigai(156));
        assertEquals("30.22", Panchangam.nazhigai(729));
    }
}
