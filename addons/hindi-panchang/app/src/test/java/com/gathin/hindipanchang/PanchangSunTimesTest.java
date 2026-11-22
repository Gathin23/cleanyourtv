package com.gathin.hindipanchang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import org.junit.Test;

public class PanchangSunTimesTest {

    private static final LocationStore.Place LONDON =
            new LocationStore.Place("London", "United Kingdom", 51.5074, -0.1278, "Europe/London");
    private static final LocationStore.Place DELHI =
            new LocationStore.Place("New Delhi", "Delhi, India", 28.6139, 77.2090, "Asia/Kolkata");
    private static final LocationStore.Place TROMSO =
            new LocationStore.Place("Tromso", "Norway", 69.65, 18.96, "Europe/Oslo");

    @Test
    public void londonMidsummerSunsetIsAfterNinePm() {
        PanchangEngine.DayPanchang d = PanchangEngine.calculate(LocalDate.of(2026, 6, 21), LONDON);
        assertEquals(1281, d.sunsetMin, 3);
        assertNotNull(d.sunsetFormatted);
    }

    @Test
    public void londonMidwinterSunsetIsBeforeFourPm() {
        PanchangEngine.DayPanchang d = PanchangEngine.calculate(LocalDate.of(2025, 12, 21), LONDON);
        assertEquals(953, d.sunsetMin, 3);
    }

    @Test
    public void delhiMidsummerSunTimesAreUnchanged() {
        PanchangEngine.DayPanchang d = PanchangEngine.calculate(LocalDate.of(2026, 6, 21), DELHI);
        assertEquals(323, d.sunriseMin);
        assertEquals(1162, d.sunsetMin);
    }

    @Test
    public void farNorthSolsticesCalculateWithSunsetAfterSunrise() {
        for (LocalDate date : new LocalDate[]{LocalDate.of(2026, 6, 21), LocalDate.of(2026, 12, 21)}) {
            PanchangEngine.DayPanchang d = PanchangEngine.calculate(date, TROMSO);
            assertTrue(date + " sunset " + d.sunsetMin + " sunrise " + d.sunriseMin, d.sunsetMin > d.sunriseMin);
        }
    }
}
