package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/** Gregorian to Solar Hijri: Nowruz, the leap-year Esfand 30, and a couple of fixed reference days. */
public class ShamsiTest {

    @Test
    public void nowruzIsTheFirstOfFarvardin() {
        assertArrayEquals(new int[]{1400, 1, 1}, Shamsi.toJalali(2021, 3, 21));
        assertArrayEquals(new int[]{1403, 1, 1}, Shamsi.toJalali(2024, 3, 20));
        assertArrayEquals(new int[]{1404, 1, 1}, Shamsi.toJalali(2025, 3, 21));
    }

    @Test
    public void theDayBeforeNowruzClosesEsfand() {
        // 1399 and 1403 are leap years (Esfand has 30 days), 1400 is not (29 days).
        assertArrayEquals(new int[]{1399, 12, 30}, Shamsi.toJalali(2021, 3, 20));
        assertArrayEquals(new int[]{1400, 12, 29}, Shamsi.toJalali(2022, 3, 20));
        assertArrayEquals(new int[]{1403, 12, 30}, Shamsi.toJalali(2025, 3, 20));
    }

    @Test
    public void fixedReferenceDays() {
        assertArrayEquals(new int[]{1348, 10, 11}, Shamsi.toJalali(1970, 1, 1));
        assertArrayEquals(new int[]{1378, 10, 11}, Shamsi.toJalali(2000, 1, 1));
    }

    @Test
    public void firstSixMonthsHave31DaysAndTheNextFiveHave30() {
        assertArrayEquals(new int[]{1405, 6, 31}, Shamsi.toJalali(2026, 9, 22));
        assertArrayEquals(new int[]{1405, 7, 1}, Shamsi.toJalali(2026, 9, 23));
        assertArrayEquals(new int[]{1405, 7, 30}, Shamsi.toJalali(2026, 10, 22));
        assertArrayEquals(new int[]{1405, 8, 1}, Shamsi.toJalali(2026, 10, 23));
    }
}
