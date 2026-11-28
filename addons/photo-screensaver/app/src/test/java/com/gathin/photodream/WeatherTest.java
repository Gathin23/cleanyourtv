package com.gathin.photodream;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeatherTest {

    @Test public void aPlaceIsNamedWithTheRegionAndCountryThatAreThere() {
        assertEquals("Springfield, Illinois, United States", Weather.label("Springfield", "Illinois", "United States"));
        assertEquals("Amsterdam, Netherlands", Weather.label("Amsterdam", "", "Netherlands"));
        assertEquals("Luxembourg", Weather.label("Luxembourg", "Luxembourg", "Luxembourg"));
        assertEquals("Monaco", Weather.label(" Monaco ", null, "  "));
    }

    @Test public void coordinatesAreKeptToAboutAKilometre() {
        assertEquals(52.37f, Weather.coarse(52.37403), 0.0001f);
        assertEquals(-122.42f, Weather.coarse(-122.41942), 0.0001f);
    }
}
