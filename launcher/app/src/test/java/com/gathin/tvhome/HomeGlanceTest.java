package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeGlanceTest {
    @Test
    public void emptyTextHidesLine() {
        assertTrue(HomeGlance.fromRow("", "clear").empty());
        assertTrue(HomeGlance.fromRow("   ", "clear").empty());
        assertTrue(HomeGlance.fromRow(null, "clear").empty());
    }

    @Test
    public void trimsAndCapsText() {
        HomeGlance.Line line = HomeGlance.fromRow("  21° · E10 1.7  ", "rain");
        assertEquals("21° · E10 1.7", line.text);
        assertEquals("rain", line.icon);
        String longText = new String(new char[80]).replace('\0', 'x');
        assertEquals(64, HomeGlance.fromRow(longText, "clear").text.length());
    }

    @Test
    public void unknownIconBecomesCloudy() {
        assertEquals("cloudy", HomeGlance.normaliseIcon("partlycloudy"));
        assertEquals("", HomeGlance.normaliseIcon(null));
        assertEquals("", HomeGlance.normaliseIcon(""));
        assertEquals("clear", HomeGlance.normaliseIcon("CLEAR"));
        assertEquals("night", HomeGlance.normaliseIcon("night"));
    }
}
