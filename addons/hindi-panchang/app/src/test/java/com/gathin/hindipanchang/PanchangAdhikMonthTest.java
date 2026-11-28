package com.gathin.hindipanchang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import org.junit.Test;

public class PanchangAdhikMonthTest {

    private static final LocationStore.Place NEW_DELHI =
            new LocationStore.Place("New Delhi", "Delhi, India", 28.6139, 77.2090, "Asia/Kolkata");

    private static PanchangEngine.DayPanchang on(int year, int month, int day) {
        return PanchangEngine.calculate(LocalDate.of(year, month, day), NEW_DELHI);
    }

    @Test
    public void darkHalfOfAdhikJyeshthaKeepsAdhikJyeshthaName() {
        PanchangEngine.DayPanchang r = on(2026, 6, 5);
        assertEquals(2, r.monthIndex);
        assertTrue(r.isAdhikMaas);
        assertEquals("Adhik Jyeshtha", r.monthEn);
    }

    @Test
    public void brightHalfOfAdhikJyeshthaIsAdhikJyeshtha() {
        PanchangEngine.DayPanchang r = on(2026, 5, 20);
        assertEquals(2, r.monthIndex);
        assertTrue(r.isAdhikMaas);
    }

    @Test
    public void darkHalfOfAdhikShravanKeepsAdhikShravanName() {
        PanchangEngine.DayPanchang r = on(2023, 8, 5);
        assertEquals(4, r.monthIndex);
        assertTrue(r.isAdhikMaas);
    }

    @Test
    public void darkHalfBeforeAdhikMonthTakesComingMonthName() {
        PanchangEngine.DayPanchang r = on(2026, 5, 10);
        assertEquals(2, r.monthIndex);
        assertFalse(r.isAdhikMaas);
    }

    @Test
    public void brightHalfOfNijaJyeshthaIsNotAdhik() {
        PanchangEngine.DayPanchang r = on(2026, 6, 20);
        assertEquals(2, r.monthIndex);
        assertFalse(r.isAdhikMaas);
    }

    @Test
    public void darkHalfBeforeAdhikShravanTakesShravanName() {
        PanchangEngine.DayPanchang r = on(2023, 7, 10);
        assertEquals(4, r.monthIndex);
        assertFalse(r.isAdhikMaas);
    }
}
