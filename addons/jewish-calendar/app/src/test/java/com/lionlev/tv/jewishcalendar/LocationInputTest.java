package com.lionlev.tv.jewishcalendar;
import org.junit.Test;
import static org.junit.Assert.*;
public class LocationInputTest {
    @Test public void validatesBothCoordinatesAndAllowsClearing() {
        assertTrue(CalendarActivity.validLocationInput("", ""));
        assertTrue(CalendarActivity.validLocationInput("-33.9", "151.2"));
        assertFalse(CalendarActivity.validLocationInput("", "151.2"));
        assertFalse(CalendarActivity.validLocationInput("NaN", "0"));
        assertFalse(CalendarActivity.validLocationInput("0", "Infinity"));
        assertFalse(CalendarActivity.validLocationInput("91", "0"));
        assertFalse(CalendarActivity.validLocationInput("0", "-181"));
        assertFalse(CalendarActivity.validLocationInput("not a number", "0"));
    }
}
