package com.oneyuv.tv.nepalicalendar;

import static org.junit.Assert.*;

import java.time.LocalDate;
import org.junit.Test;

public class BikramSambatTest {
    /** Known Gregorian and BS pairs. */
    private static final String[][] PAIRS = {
            {"1913-04-13", "1970-01-01"},
            {"1952-01-20", "2008-10-06"},
            {"1956-03-29", "2012-12-16"},
            {"1958-05-12", "2015-01-30"},
            {"1959-07-14", "2016-03-30"},
            {"1964-03-09", "2020-11-26"},
            {"1971-06-20", "2028-03-06"},
            {"1976-04-02", "2032-12-20"},
            {"1977-07-02", "2034-03-18"},
            {"1985-10-29", "2042-07-13"},
            {"1986-04-28", "2043-01-15"},
            {"1993-04-04", "2049-12-22"},
            {"1999-08-03", "2056-04-18"},
            {"2015-01-01", "2071-09-17"},
            {"2032-08-26", "2089-05-10"},
            {"2033-12-31", "2090-09-16"},
    };

    @Test public void convertsKnownDatesBothWays() {
        for (String[] pair : PAIRS) {
            LocalDate gregorian = LocalDate.parse(pair[0]);
            BikramSambat bs = BikramSambat.of(gregorian);
            assertEquals(pair[0], pair[1], bs.toString());
            assertEquals(pair[1], gregorian, bs.toGregorian());
        }
    }

    @Test public void newYears() {
        assertEquals("2082-01-01", BikramSambat.of(LocalDate.of(2025, 4, 14)).toString());
        assertEquals("2083-01-01", BikramSambat.of(LocalDate.of(2026, 4, 14)).toString());
        assertEquals("2082-12-30", BikramSambat.of(LocalDate.of(2026, 4, 13)).toString());
    }

    @Test public void monthLengthsMatchTheTestData() {
        int[] y2081 = {31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31};
        int[] y2090 = {30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30};
        for (int m = 1; m <= 12; m++) {
            assertEquals(y2081[m - 1], BikramSambat.daysInMonth(2081, m));
            assertEquals(y2090[m - 1], BikramSambat.daysInMonth(2090, m));
        }
    }

    @Test public void everyYearIsASolarYear() {
        for (int y = BikramSambat.FIRST_YEAR; y <= BikramSambat.LAST_YEAR; y++) {
            int days = 0;
            for (int m = 1; m <= 12; m++) days += BikramSambat.daysInMonth(y, m);
            assertTrue(y + " has " + days, days == 365 || days == 366);
        }
    }

    @Test public void roundTripsEveryDayInTheTable() {
        LocalDate day = LocalDate.of(1913, 4, 13);
        BikramSambat previous = null;
        for (BikramSambat bs = BikramSambat.of(day); bs != null; day = day.plusDays(1), bs = BikramSambat.of(day)) {
            assertEquals(day, bs.toGregorian());
            if (previous != null && bs.day != 1) assertEquals(previous.day + 1, bs.day);
            previous = bs;
        }
        assertEquals(BikramSambat.LAST_YEAR, previous.year);
        assertEquals(12, previous.month);
    }

    @Test public void outsideTheTableIsNull() {
        assertNull(BikramSambat.of(LocalDate.of(1913, 4, 12)));
        assertNull(BikramSambat.of(LocalDate.of(2040, 1, 1)));
        assertFalse(BikramSambat.supported(BikramSambat.LAST_YEAR + 1));
        assertNull(new BikramSambat(BikramSambat.LAST_YEAR, 12, 1).plusMonths(1));
        assertNull(new BikramSambat(BikramSambat.FIRST_YEAR, 1, 1).plusMonths(-1));
    }

    @Test public void monthStepsCrossYears() {
        assertEquals("2083-01-01", new BikramSambat(2082, 12, 5).plusMonths(1).toString());
        assertEquals("2081-12-01", new BikramSambat(2082, 1, 1).plusMonths(-1).toString());
        assertEquals("2082-07-01", new BikramSambat(2082, 7, 9).plusMonths(0).toString());
    }

    @Test public void clampedMonthsStayInTheTable() {
        BikramSambat now = new BikramSambat(2083, 7, 1);
        assertEquals(0, now.clampMonths(0));
        assertEquals(-5, now.clampMonths(-5));
        for (int months = -2000; months <= 2000; months += 7) {
            assertNotNull(Integer.toString(months), now.plusMonths(now.clampMonths(months)));
        }
        assertEquals("2090-12-01", now.plusMonths(now.clampMonths(Integer.MAX_VALUE / 2)).toString());
        assertEquals("1970-01-01", now.plusMonths(now.clampMonths(Integer.MIN_VALUE / 2)).toString());
    }

    /** An offset kept from browsing to an edge month, after today jumps a year further (#2204). */
    @Test public void offsetKeptAcrossADateJumpStaysOnTheEdgeMonth() {
        BikramSambat before = monthOf(LocalDate.of(2026, 10, 28));
        int offset = 0;
        while (before.plusMonths(offset + 1) != null) offset++;
        assertEquals("2090-12-01", before.plusMonths(offset).toString());
        BikramSambat after = monthOf(LocalDate.of(2027, 10, 28));
        assertNull(after.plusMonths(offset - 1));
        assertEquals("2090-12-01", after.plusMonths(after.clampMonths(offset)).toString());

        offset = 0;
        while (after.plusMonths(offset - 1) != null) offset--;
        assertEquals("1970-01-01", after.plusMonths(offset).toString());
        assertNull(before.plusMonths(offset + 1));
        assertEquals("1970-01-01", before.plusMonths(before.clampMonths(offset)).toString());
    }

    private static BikramSambat monthOf(LocalDate date) {
        BikramSambat today = BikramSambat.of(date);
        return new BikramSambat(today.year, today.month, 1);
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsDaysPastTheMonth() {
        new BikramSambat(2081, 9, 30).toGregorian();
    }
}
