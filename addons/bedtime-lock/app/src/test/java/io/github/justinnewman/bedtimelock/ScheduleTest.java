package io.github.justinnewman.bedtimelock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The locked hours, including the window that runs past midnight. */
public class ScheduleTest {
    private static final int NINE_PM = 21 * 60, SEVEN_AM = 7 * 60;

    @Test
    public void overnightWindowCoversLateEveningAndEarlyMorning() {
        assertTrue(Schedule.inWindow(NINE_PM, NINE_PM, SEVEN_AM));
        assertTrue(Schedule.inWindow(23 * 60 + 59, NINE_PM, SEVEN_AM));
        assertTrue(Schedule.inWindow(0, NINE_PM, SEVEN_AM));
        assertTrue(Schedule.inWindow(SEVEN_AM - 1, NINE_PM, SEVEN_AM));
        assertFalse(Schedule.inWindow(SEVEN_AM, NINE_PM, SEVEN_AM));
        assertFalse(Schedule.inWindow(12 * 60, NINE_PM, SEVEN_AM));
        assertFalse(Schedule.inWindow(NINE_PM - 1, NINE_PM, SEVEN_AM));
    }

    @Test
    public void daytimeWindowAndEmptyWindow() {
        assertTrue(Schedule.inWindow(10 * 60, 8 * 60, 15 * 60));
        assertFalse(Schedule.inWindow(16 * 60, 8 * 60, 15 * 60));
        assertFalse(Schedule.inWindow(10 * 60, 10 * 60, 10 * 60));
    }

    @Test
    public void nextChangeIsTheNearerBoundary() {
        assertEquals(60, Schedule.minutesToNextChange(20 * 60, NINE_PM, SEVEN_AM));
        assertEquals(10 * 60, Schedule.minutesToNextChange(NINE_PM, NINE_PM, SEVEN_AM));   // the end, not a day
        assertEquals(7 * 60, Schedule.minutesToNextChange(0, NINE_PM, SEVEN_AM));
        assertEquals(14 * 60, Schedule.minutesToNextChange(SEVEN_AM, NINE_PM, SEVEN_AM));
    }

    @Test
    public void shiftWrapsRoundMidnight() {
        assertEquals(15, Schedule.shift(23 * 60 + 60, 15));
        assertEquals(23 * 60 + 45, Schedule.shift(0, -15));
        assertEquals(NINE_PM + 15, Schedule.shift(NINE_PM, 15));
    }
}
