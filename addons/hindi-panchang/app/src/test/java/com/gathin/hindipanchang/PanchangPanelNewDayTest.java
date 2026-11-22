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
}
