package com.gathin.hindipanchang;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import org.junit.Test;

public class PanchangVikramSamvatTest {

    private static final LocationStore.Place DELHI =
            new LocationStore.Place("New Delhi", "Delhi, India", 28.6139, 77.2090, "Asia/Kolkata");

    private static int samvat(int y, int m, int d) {
        return PanchangEngine.calculate(LocalDate.of(y, m, d), DELHI).vikramSamvat;
    }

    @Test
    public void chaitraDarkHalfInMarchBelongsToPreviousYear() {
        assertEquals(2082, samvat(2026, 3, 10));
    }

    @Test
    public void chaitraDarkHalfInAprilBelongsToPreviousYear() {
        assertEquals(2083, samvat(2027, 4, 3));
    }

    @Test
    public void chaitraBrightHalfStartsNewYear() {
        assertEquals(2083, samvat(2026, 3, 25));
    }

    @Test
    public void maghaIsStillPreviousYear() {
        assertEquals(2082, samvat(2026, 1, 15));
    }

    @Test
    public void vaishakhaIsNewYear() {
        assertEquals(2083, samvat(2026, 4, 10));
    }
}
