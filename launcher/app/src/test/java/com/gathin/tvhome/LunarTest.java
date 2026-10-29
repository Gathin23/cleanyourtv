package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
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
    public void lunarNewYear2023() {
        assertArrayEquals(new int[]{1, 1, 2023, 0}, Lunar.fromSolar(22, 1, 2023, 7));
        assertArrayEquals(new int[]{30, 12, 2022, 0}, Lunar.fromSolar(21, 1, 2023, 7));
    }

    @Test
    public void leapSecondMonth2023StartsOn22March() {
        // Month 2 runs from 20 February; the new moon of 21 March 17:23 UTC is 22 March in Vietnam.
        assertArrayEquals(new int[]{1, 2, 2023, 0}, Lunar.fromSolar(20, 2, 2023, 7));
        assertArrayEquals(new int[]{30, 2, 2023, 0}, Lunar.fromSolar(21, 3, 2023, 7));
        assertArrayEquals(new int[]{1, 2, 2023, 1}, Lunar.fromSolar(22, 3, 2023, 7));
        assertArrayEquals(new int[]{1, 3, 2023, 0}, Lunar.fromSolar(20, 4, 2023, 7));
    }

    @Test
    public void vietnamAndChinaDifferIn2007() {
        // The 2007 new year: 17 February in Vietnam, 18 February in China, where 17 February is still 30/12.
        assertArrayEquals(new int[]{1, 1, 2007, 0}, Lunar.fromSolar(17, 2, 2007, 7));
        assertArrayEquals(new int[]{30, 12, 2006, 0}, Lunar.fromSolar(17, 2, 2007, 8));
        assertArrayEquals(new int[]{1, 1, 2007, 0}, Lunar.fromSolar(18, 2, 2007, 8));
    }

    @Test
    public void vietnamAndChinaDifferIn1985() {
        // The 1985 new year: 21 January in Vietnam (UTC+7), 20 February in China (UTC+8).
        assertArrayEquals(new int[]{1, 1, 1985, 0}, Lunar.fromSolar(21, 1, 1985, 7));
        assertArrayEquals(new int[]{1, 1, 1985, 0}, Lunar.fromSolar(20, 2, 1985, 8));
    }

    @Test
    public void koreaAndChinaDifferIn1997And2027() {
        // Seollal falls a day after the Chinese new year when the new moon is just after 15:00 UTC.
        assertArrayEquals(new int[]{1, 1, 1997, 0}, Lunar.fromSolar(7, 2, 1997, 8));
        assertArrayEquals(new int[]{30, 12, 1996, 0}, Lunar.fromSolar(7, 2, 1997, 9));
        assertArrayEquals(new int[]{1, 1, 1997, 0}, Lunar.fromSolar(8, 2, 1997, 9));
        assertArrayEquals(new int[]{1, 1, 2027, 0}, Lunar.fromSolar(6, 2, 2027, 8));
        assertArrayEquals(new int[]{1, 1, 2027, 0}, Lunar.fromSolar(7, 2, 2027, 9));
    }

    @Test
    public void koreanHolidays2026() {
        assertArrayEquals(new int[]{1, 1, 2026, 0}, Lunar.fromSolar(17, 2, 2026, 9));
        assertArrayEquals(new int[]{15, 8, 2026, 0}, Lunar.fromSolar(25, 9, 2026, 9));
    }

    @Test
    public void dayBeforeANewMoonJustAfterMidnightIsTheThirtieth() {
        // The new moon of 26 March 2009 16:06 UTC is 27 March in China; the day before was day 0, not 30/2.
        assertArrayEquals(new int[]{30, 2, 2009, 0}, Lunar.fromSolar(26, 3, 2009, 8));
        assertArrayEquals(new int[]{1, 3, 2009, 0}, Lunar.fromSolar(27, 3, 2009, 8));
        assertArrayEquals(new int[]{30, 3, 2001, 0}, Lunar.fromSolar(23, 4, 2001, 9));
        assertArrayEquals(new int[]{30, 3, 2054, 0}, Lunar.fromSolar(7, 5, 2054, 7));
    }

    @Test
    public void everyDayFrom1900To2099IsDay1To30() {
        for (int tz = 7; tz <= 9; tz++) {
            for (long jd = Lunar.jdFromDate(1, 1, 1900); jd < Lunar.jdFromDate(1, 1, 2100); jd++) {
                java.time.LocalDate d = java.time.LocalDate.ofEpochDay(jd - 2440588);
                int day = Lunar.fromSolar(d.getDayOfMonth(), d.getMonthValue(), d.getYear(), tz)[0];
                assertTrue(d + " at UTC+" + tz + " is day " + day, day >= 1 && day <= 30);
            }
        }
    }

    @Test
    public void offsetFollowsTheLanguageUnlessConfigured() {
        assertEquals(7, Lunar.offsetFor(-1, new Locale("vi")));
        assertEquals(8, Lunar.offsetFor(-1, Locale.SIMPLIFIED_CHINESE));
        assertEquals(9, Lunar.offsetFor(-1, Locale.KOREAN));
        assertEquals(8, Lunar.offsetFor(-1, Locale.ENGLISH));
        assertEquals(8, Lunar.offsetFor(8, Locale.KOREAN));
    }
}
