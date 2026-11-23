package com.gathin.hindipanchang;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import org.junit.Test;

public class PanchangPanelNewDayTest {
    private static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    @Test public void followsMidnightWhenOnToday() {
        assertEquals(d(2026, 11, 23),
                PanchangPanel.dayAfterDateChange(d(2026, 11, 22), d(2026, 11, 22), d(2026, 11, 23), 0));
    }

    @Test public void followsMonthEnd() {
        assertEquals(d(2026, 12, 1),
                PanchangPanel.dayAfterDateChange(d(2026, 11, 30), d(2026, 11, 30), d(2026, 12, 1), 0));
    }

    @Test public void keepsOtherDayChosenByUser() {
        assertEquals(d(2026, 11, 10),
                PanchangPanel.dayAfterDateChange(d(2026, 11, 10), d(2026, 11, 22), d(2026, 11, 23), 0));
    }

    @Test public void keepsOtherMonthBrowsedByUser() {
        assertEquals(d(2026, 12, 5),
                PanchangPanel.dayAfterDateChange(d(2026, 12, 5), d(2026, 11, 22), d(2026, 11, 23), 1));
    }

    @Test public void keepsDayWhenDateUnchanged() {
        assertEquals(d(2026, 11, 22),
                PanchangPanel.dayAfterDateChange(d(2026, 11, 22), d(2026, 11, 22), d(2026, 11, 22), 0));
    }

    @Test public void browsedNextMonthAtMonthEnd() {
        assertEquals(0, PanchangPanel.monthOffsetAfterDateChange(1, d(2026, 11, 30), d(2026, 12, 1), false));
    }

    @Test public void browsedNextMonthAtYearEnd() {
        assertEquals(0, PanchangPanel.monthOffsetAfterDateChange(1, d(2026, 12, 31), d(2027, 1, 1), false));
    }

    @Test public void browsedPreviousMonthAtMonthEnd() {
        assertEquals(-2, PanchangPanel.monthOffsetAfterDateChange(-1, d(2026, 11, 30), d(2026, 12, 1), false));
    }

    @Test public void pickedDayInCurrentMonthKeptAcrossMonthEnd() {
        assertEquals(-1, PanchangPanel.monthOffsetAfterDateChange(0, d(2026, 11, 30), d(2026, 12, 1), false));
    }

    @Test public void followsTodayResetsOffset() {
        assertEquals(0, PanchangPanel.monthOffsetAfterDateChange(0, d(2026, 11, 30), d(2026, 12, 1), true));
    }

    @Test public void sameMonthDayChangeKeepsOffset() {
        assertEquals(1, PanchangPanel.monthOffsetAfterDateChange(1, d(2026, 11, 22), d(2026, 11, 23), false));
    }
}
