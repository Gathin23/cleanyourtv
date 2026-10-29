package com.oneyuv.tv.nepalicalendar;

import static org.junit.Assert.*;

import java.time.LocalDate;
import org.junit.Test;

public class NepaliDateTest {
    @Test public void devanagariDigits() {
        assertEquals("२०८३", NepaliDate.digits(2083));
        assertEquals("१०:४५ AM", NepaliDate.digits("10:45 AM"));
    }

    @Test public void formatsToday() {
        LocalDate date = LocalDate.of(2026, 10, 27);
        BikramSambat bs = BikramSambat.of(date);
        assertEquals("मंगलबार, कार्तिक १०, २०८३", NepaliDate.text(bs, date));
        assertEquals("Kartik 10, 2083 BS", NepaliDate.latin(bs));
        assertEquals("कार्तिक २०८३ · Kartik 2083", NepaliDate.monthTitle(bs));
    }

    @Test public void weekStartsOnSundayAndTheWeekendIsRed() {
        assertEquals(0, NepaliDate.weekday(LocalDate.of(2026, 10, 25)));
        assertEquals(6, NepaliDate.weekday(LocalDate.of(2026, 10, 31)));
        assertTrue(NepaliDate.weekend(LocalDate.of(2026, 10, 31)));
        assertTrue(NepaliDate.weekend(LocalDate.of(2026, 11, 1)));
        assertFalse(NepaliDate.weekend(LocalDate.of(2026, 11, 2)));
        assertFalse(NepaliDate.weekend(LocalDate.of(2026, 10, 30)));
    }

    @Test public void nationalDays() {
        assertEquals(1, NepaliDate.nationalDay(new BikramSambat(2083, 1, 1)));
        assertEquals(4, NepaliDate.nationalDay(new BikramSambat(2083, 6, 3)));
        assertEquals(6, NepaliDate.nationalDay(new BikramSambat(2083, 10, 1)));
        assertEquals(0, NepaliDate.nationalDay(new BikramSambat(2083, 7, 10)));
    }

    @Test public void everyNationalDayFitsInItsMonth() {
        for (int y = BikramSambat.FIRST_YEAR; y <= BikramSambat.LAST_YEAR; y++) {
            int found = 0;
            for (int m = 1; m <= 12; m++) {
                for (int d = 1; d <= BikramSambat.daysInMonth(y, m); d++) {
                    if (NepaliDate.nationalDay(new BikramSambat(y, m, d)) != 0) found++;
                }
            }
            assertEquals(Integer.toString(y), NepaliDate.nationalDayCount(), found);
        }
    }
}
