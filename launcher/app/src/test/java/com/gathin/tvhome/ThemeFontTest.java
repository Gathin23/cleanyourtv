package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Plain JVM tests for the theme font: the weights it is built at and the feature strings it accepts. */
public class ThemeFontTest {

    @Test
    public void weightsSnapToHundredsWithinRange() {
        assertEquals(300, ThemeFont.weight(300));
        assertEquals(400, ThemeFont.weight(0));
        assertEquals(500, ThemeFont.weight(549));
        assertEquals(100, ThemeFont.weight(40));
        assertEquals(900, ThemeFont.weight(1000));
    }

    @Test
    public void featuresAreOpenTypeTagsOnly() {
        assertEquals("'ss01'", ThemeFont.features("ss01"));
        assertEquals("'ss01', 'cv05'", ThemeFont.features("ss01, cv05"));
        assertEquals("", ThemeFont.features("ss01; drop"));
        assertEquals("", ThemeFont.features(""));
        assertEquals("", ThemeFont.features(null));
    }
}
