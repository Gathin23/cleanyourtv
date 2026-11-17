package io.github.yourvoldemort.panchang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.Test;

/**
 * A TV gives no position, so the owner picks a city from this list and sunrise follows it. The
 * regions are the five calendars the add-on offers, each with its own months, era and numerals.
 */
public class CityAndRegionTest {

    @Test
    public void theCityListCoversTheDiaspora() {
        assertEquals(44, City.ALL.length);
        Set<String> names = new HashSet<>();
        for (City city : City.ALL) {
            assertNotNull(city.name);
            assertTrue("a city has a name", names.add(city.name));
            assertNotNull("a city has a time zone", city.zone);
            assertTrue("a latitude", Math.abs(city.lat) < 90);
            assertTrue("a longitude", Math.abs(city.lon) <= 180);
        }
        assertEquals(4, City.indexOf("Chennai"));
        assertEquals(43, City.indexOf("Suva"));
    }

    @Test
    public void anUnknownCityFallsBackToDelhi() {
        assertEquals("Delhi", City.cityNamed("Nowhere").name);
        assertEquals("Chennai", City.cityNamed("chennai").name);
        assertEquals(City.ALL[1], City.cityNamed("Nowhere"));
    }

    @Test
    public void coordinatesAreWrittenForAScreen() {
        assertEquals("28.6\u00b0N 77.2\u00b0E", City.cityNamed("Delhi").coordinates());
        assertEquals("51.5\u00b0N 0.1\u00b0W", City.cityNamed("London").coordinates());
        assertEquals("33.9\u00b0S 151.2\u00b0E", City.cityNamed("Sydney").coordinates());
    }

    @Test
    public void everyRegionNamesMonthsAndAnEra() {
        assertEquals(5, Region.values().length);
        for (Region region : Region.values()) {
            assertEquals(12, region.months.length);
            assertEquals(12, region.latin.length);
            assertEquals(2, region.paksha.length);
            assertNotNull(region.label);
            assertNotNull(region.era);
            assertNotNull(region.eraNative);
            assertNotNull(region.adhika);
            for (String month : region.latin) assertTrue(month, month.length() > 2);
        }
        assertEquals("Vikram Samvat", Region.NORTH.era);
        assertEquals("Shaka Samvat", Region.MARATHI.era);
        assertEquals("Bangabda", Region.BENGALI.era);
    }

    @Test
    public void regionNamesAndTheirFallback() {
        assertEquals(Region.GUJARATI, Region.named("GUJARATI"));
        assertEquals(Region.BENGALI, Region.named("BENGALI"));
        assertEquals("an unknown name falls back to the north Indian calendar",
                Region.NORTH, Region.named("MARS"));
    }

    @Test
    public void theRegionFollowsTheTvsLanguage() {
        assertEquals(Region.TAMIL, Region.forLocale(Locale.forLanguageTag("ta-IN")));
        assertEquals(Region.BENGALI, Region.forLocale(Locale.forLanguageTag("bn-BD")));
        assertEquals(Region.GUJARATI, Region.forLocale(Locale.forLanguageTag("gu")));
        assertEquals(Region.MARATHI, Region.forLocale(Locale.forLanguageTag("mr-IN")));
        assertEquals("Hindi starts on the north Indian calendar",
                Region.NORTH, Region.forLocale(Locale.forLanguageTag("hi-IN")));
        assertEquals(Region.NORTH, Region.forLocale(Locale.US));
    }

    @Test
    public void yearsAreWrittenInTheScriptsOwnDigits() {
        assertEquals("\u0968\u0966\u096e\u0969", Region.NORTH.digits(2083));
        assertEquals("\u0ae8\u0ae6\u0aee\u0ae9", Region.GUJARATI.digits(2083));
        assertEquals("\u09e8\u09e6\u09ee\u09e9", Region.BENGALI.digits(2083));
        assertEquals("Tamil keeps the digits every TV can draw",
                "2083", Region.TAMIL.digits(2083));
        assertEquals("letters pass through untouched",
                "Chaitra ३", Region.NORTH.digits("Chaitra 3"));
    }

    @Test
    public void solarRegionsAreTheTwoThatFollowTheSun() {
        assertTrue(Region.TAMIL.solar);
        assertTrue(Region.BENGALI.solar);
        assertTrue(Region.NORTH.purnimanta);
        assertTrue(!Region.GUJARATI.purnimanta);
        assertTrue(!Region.MARATHI.purnimanta);
    }
}
