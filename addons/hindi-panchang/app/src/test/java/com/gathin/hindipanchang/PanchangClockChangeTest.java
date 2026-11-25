package com.gathin.hindipanchang;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import org.junit.Test;

public class PanchangClockChangeTest {

    private static final LocationStore.Place CHICAGO = new LocationStore.Place(
            "Chicago", "Illinois, USA", 41.8781, -87.6298, "America/Chicago");
    private static final LocationStore.Place DELHI = new LocationStore.Place(
            "New Delhi", "Delhi, India", 28.6139, 77.2090, "Asia/Kolkata");

    private static int nakshatra(LocalDate date, LocationStore.Place place) {
        return PanchangEngine.calculate(date, place).nakshatraIndex;
    }

    @Test
    public void fallBackDaySunriseIsLocalClockTime() {
        assertEquals("6:23 AM", PanchangEngine.calculate(LocalDate.of(2025, 11, 2), CHICAGO).sunriseFormatted);
    }

    @Test
    public void fallBackDayUsesRealSunriseForNakshatra() {
        assertEquals(25, nakshatra(LocalDate.of(2025, 11, 2), CHICAGO));
    }

    @Test
    public void dayBeforeFallBackIsUnchanged() {
        assertEquals(23, nakshatra(LocalDate.of(2025, 11, 1), CHICAGO));
    }

    @Test
    public void dayAfterFallBackIsUnchanged() {
        assertEquals(26, nakshatra(LocalDate.of(2025, 11, 3), CHICAGO));
    }

    @Test
    public void springForwardDayNakshatra() {
        assertEquals(15, nakshatra(LocalDate.of(2026, 3, 8), CHICAGO));
    }

    @Test
    public void cityWithoutClockChangeIsUnchanged() {
        assertEquals(24, nakshatra(LocalDate.of(2025, 11, 2), DELHI));
    }
}
