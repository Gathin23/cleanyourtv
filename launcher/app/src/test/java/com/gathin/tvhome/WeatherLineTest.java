package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** What the launcher accepts from the Weather add-on for the line under the clock. */
public class WeatherLineTest {
    @Test
    public void aReadingIsShownAsItCame() {
        assertEquals("48°  ☁  Cloudy  ·  H 55°  L 38°", WeatherLine.clean("48°  ☁  Cloudy  ·  H 55°  L 38°"));
    }

    @Test
    public void controlAndFormattingCharactersAreDropped() {
        assertEquals("48° Cloudy", WeatherLine.clean("48°\n‮ Cloudy\t"));
        assertEquals("", WeatherLine.clean(null));
        assertEquals("", WeatherLine.clean("   "));
    }

    @Test
    public void aLongLineIsCut() {
        assertEquals(WeatherLine.MAX_CHARS, WeatherLine.clean("x".repeat(500)).length());
    }
}
