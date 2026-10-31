package io.github.lostininaka.weatherglance;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The wording Home shows, and the line built from it. */
public final class ConditionsTest {

    @Test
    public void everyCodeFamilyHasAWording() {
        assertEquals("Clear", Conditions.describe(0)[1]);
        assertEquals("Partly cloudy", Conditions.describe(2)[1]);
        assertEquals("Overcast", Conditions.describe(3)[1]);
        assertEquals("Fog", Conditions.describe(45)[1]);
        assertEquals("Drizzle", Conditions.describe(55)[1]);
        assertEquals("Rain", Conditions.describe(65)[1]);
        assertEquals("Snow", Conditions.describe(73)[1]);
        assertEquals("Showers", Conditions.describe(81)[1]);
        assertEquals("Snow showers", Conditions.describe(85)[1]);
        assertEquals("Thunderstorm", Conditions.describe(99)[1]);
    }

    @Test
    public void anUnknownCodeStillHasAGlyphAndWording() {
        String[] d = Conditions.describe(42);
        assertEquals("Unknown", d[1]);
        assertEquals(1, d[0].length());
    }

    @Test
    public void theLineIsTheTemperatureThenThePlace() {
        assertEquals("14\u00B0 Drizzle \u00B7 TAKIZAWA", Conditions.line(14, "Drizzle", "Takizawa"));
        // No place yet: no dangling separator.
        assertEquals("14\u00B0 Drizzle", Conditions.line(14, "Drizzle", ""));
        assertEquals("14\u00B0 Drizzle", Conditions.line(14, "Drizzle", null));
        // A temperature below zero.
        assertEquals("-3\u00B0 Clear", Conditions.line(-3, "Clear", ""));
    }

    @Test
    public void thePlaceIsTrimmedAndUppercasedOnce() {
        assertEquals("20\u00B0 Clear \u00B7 NEW YORK", Conditions.line(20, "Clear", "  New York "));
    }
}
