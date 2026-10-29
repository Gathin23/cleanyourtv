package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** Known lunar dates (Vietnamese calendar, UTC+7 unless noted). Arrays are {day, month, year, leap}. */
public class LunarTest {

    @Test
    public void lunarNewYear() {
        assertArrayEquals(new int[]{1, 1, 2024, 0}, Lunar.fromSolar(10, 2, 2024, 7));
        assertArrayEquals(new int[]{1, 1, 2025, 0}, Lunar.fromSolar(29, 1, 2025, 7));
        assertArrayEquals(new int[]{1, 1, 2026, 0}, Lunar.fromSolar(17, 2, 2026, 7));
        assertArrayEquals(new int[]{1, 1, 2027, 0}, Lunar.fromSolar(6, 2, 2027, 7));
    }

    @Test
    public void dayBeforeNewYearBelongsToTheOldYear() {
        assertArrayEquals(new int[]{29, 12, 2025, 0}, Lunar.fromSolar(16, 2, 2026, 7));
    }

    @Test
    public void midAutumn() {
        assertArrayEquals(new int[]{15, 8, 2024, 0}, Lunar.fromSolar(17, 9, 2024, 7));
        assertArrayEquals(new int[]{15, 8, 2025, 0}, Lunar.fromSolar(6, 10, 2025, 7));
    }

    @Test
    public void leapSixthMonth2025() {
        assertArrayEquals(new int[]{1, 6, 2025, 1}, Lunar.fromSolar(25, 7, 2025, 7));
        assertArrayEquals(new int[]{1, 6, 2025, 0}, Lunar.fromSolar(25, 6, 2025, 7));
    }

    @Test
    public void vietnamAndChinaDifferIn1985() {
        // The 1985 new year: 21 January in Vietnam (UTC+7), 20 February in China (UTC+8).
        assertArrayEquals(new int[]{1, 1, 1985, 0}, Lunar.fromSolar(21, 1, 1985, 7));
        assertArrayEquals(new int[]{1, 1, 1985, 0}, Lunar.fromSolar(20, 2, 1985, 8));
    }
}
