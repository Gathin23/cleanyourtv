package com.norma.tvlauncher;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Sunrise/sunset maths and the sky's stretch onto a real day. */
public class SunTest {

    private static final ZoneId VILNIUS = ZoneId.of("Europe/Vilnius");

    @Test
    public void vilniusWinterDayIsShortAndLate() {
        int[] t = Sun.riseAndSet(LocalDate.of(2026, 12, 21), 54.69, 25.28, VILNIUS);
        assertTrue("sunrise " + t[0], t[0] > 8 * 60 + 30 && t[0] < 9 * 60);      // about 08:40
        assertTrue("sunset " + t[1], t[1] > 15 * 60 + 40 && t[1] < 16 * 60 + 5);  // about 15:55
    }

    @Test
    public void vilniusSummerDayIsLongInLocalSummerTime() {
        int[] t = Sun.riseAndSet(LocalDate.of(2026, 6, 21), 54.69, 25.28, VILNIUS);
        assertTrue("sunrise " + t[0], t[0] > 4 * 60 + 30 && t[0] < 4 * 60 + 55);  // about 04:42
        assertTrue("sunset " + t[1], t[1] > 21 * 60 + 50 && t[1] < 22 * 60 + 10); // about 22:00
    }

    @Test
    public void noSunriseOrSunsetAtThePoles() {
        assertNull(Sun.riseAndSet(LocalDate.of(2026, 12, 21), 78.2, 15.6, ZoneId.of("Arctic/Longyearbyen")));
        assertNull(Sun.riseAndSet(LocalDate.of(2026, 6, 21), 78.2, 15.6, ZoneId.of("Arctic/Longyearbyen")));
    }

    @Test
    public void skyGlowsLandOnTheRealSunriseAndSunset() {
        int sunrise = 8 * 60 + 40, sunset = 15 * 60 + 55;
        assertEquals(Sky.at(Sky.REF_SUNRISE).sun, Sky.at(sunrise, sunrise, sunset).sun);
        assertEquals(Sky.at(Sky.REF_SUNSET).bottom, Sky.at(sunset, sunrise, sunset).bottom);
        assertEquals(Sky.at(0).top, Sky.at(0, sunrise, sunset).top);
        assertEquals(Sky.at(1440).top, Sky.at(1440, sunrise, sunset).top);
        // Five o'clock on that winter day is past sunset: the glow has gone down on the right,
        // where the reference day still has the sun in the afternoon sky.
        assertTrue(Sky.at(17 * 60, sunrise, sunset).sunX > Sky.at(17 * 60).sunX + 0.1f);
    }

    @Test
    public void theReferenceDayMapsOntoItself() {
        for (int m = 0; m <= 1440; m += 30) {
            assertEquals(Sky.at(m).bottom, Sky.at(m, Sky.REF_SUNRISE, Sky.REF_SUNSET).bottom);
        }
    }

    @Test
    public void nonsenseSunTimesFallBackToTheReferenceDay() {
        assertEquals(Sky.at(600).bottom, Sky.at(600, 900, 800).bottom);
        assertEquals(Sky.at(600).bottom, Sky.at(600, 0, 1440).bottom);
    }
}
