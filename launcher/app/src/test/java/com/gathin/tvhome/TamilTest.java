package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertArrayEquals;

/** Gregorian to Tamil: a printed daily calendar sheet, and the first days of Thai, Chithirai and Aippasi. */
public class TamilTest {

    private static int[] tamil(int y, int m, int d) {
        return Tamil.fromGregorian(LocalDate.of(y, m, d));
    }

    @Test
    public void matchesAPrintedDailySheet() {
        // 27-10-2026, Tuesday: பராபவ ஐப்பசி 10.
        assertArrayEquals(new int[]{39, 6, 10}, tamil(2026, 10, 27));
    }

    @Test
    public void aSignEnteredAfterSunsetStartsTheMonthNextDay() {
        // The sun enters Thula on the evening of 17 October 2026, so Aippasi 1 is the 18th.
        assertArrayEquals(new int[]{39, 5, 31}, tamil(2026, 10, 17));
        assertArrayEquals(new int[]{39, 6, 1}, tamil(2026, 10, 18));
    }

    @Test
    public void newYearOnChithiraiOne() {
        // Puthandu, 14 April 2026: Vishvavasu ends, Parabhava begins.
        assertArrayEquals(new int[]{38, 11, 30}, tamil(2026, 4, 13));
        assertArrayEquals(new int[]{39, 0, 1}, tamil(2026, 4, 14));
    }

    @Test
    public void pongalIsThaiOne() {
        assertArrayEquals(new int[]{38, 8, 29}, tamil(2026, 1, 13));
        assertArrayEquals(new int[]{38, 9, 1}, tamil(2026, 1, 14));
    }
}
