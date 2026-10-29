package io.github.sumyabazar1.weather;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The tables that turn Open-Meteo's numbers into words and colours. */
public class ForecastTest {
    @Test
    public void aqiBandsFollowTheUsScale() {
        assertEquals(0, Forecast.aqiBand(0));
        assertEquals(0, Forecast.aqiBand(50));
        assertEquals(1, Forecast.aqiBand(51));
        assertEquals(1, Forecast.aqiBand(100));
        assertEquals(2, Forecast.aqiBand(150));
        assertEquals(3, Forecast.aqiBand(151));
        assertEquals(4, Forecast.aqiBand(300));
        assertEquals(5, Forecast.aqiBand(500));
    }

    @Test
    public void onlyTheYellowBandGetsDarkText() {
        assertEquals(0xFF1A1A1A, Forecast.aqiTextColour(55));
        assertEquals(0xFFFFFFFF, Forecast.aqiTextColour(20));
        assertEquals(0xFFFFFFFF, Forecast.aqiTextColour(180));
    }

    @Test
    public void skyWordsAreAsFineAsTheCode() {
        assertEquals(R.string.sky_sunny, Forecast.sky(0, true));
        assertEquals(R.string.sky_clear, Forecast.sky(0, false));
        assertEquals(R.string.sky_partly_cloudy, Forecast.sky(2, true));
        assertEquals(R.string.sky_light_snow, Forecast.sky(71, true));
        assertEquals(R.string.sky_heavy_snow, Forecast.sky(75, true));
        assertEquals(R.string.sky_snow_showers, Forecast.sky(86, true));
        assertEquals(R.string.sky_thunderstorm, Forecast.sky(99, true));
        assertEquals(R.string.sky_cloudy, Forecast.sky(42, true));   // a code WMO does not use
    }

    @Test
    public void rainNearFreezingInDryAirIsSnow() {
        // The Ulaanbaatar blizzard: ICON said light rain (61) at 1.1 °C and 86 % humidity.
        assertEquals(71, Forecast.snowIfCold(61, 1.1, 86));
        assertEquals(73, Forecast.snowIfCold(63, 0.5, 80));
        assertEquals(75, Forecast.snowIfCold(82, -1, 90));
        // Warm rain stays rain; freezing rain stays freezing rain; dry codes are untouched.
        assertEquals(61, Forecast.snowIfCold(61, 6, 90));
        assertEquals(66, Forecast.snowIfCold(66, 0, 95));
        assertEquals(3, Forecast.snowIfCold(3, -5, 60));
    }

    @Test
    public void forecastDaysStayBetweenThreeAndSeven() {
        assertEquals(3, Forecast.forecastDays(3));
        assertEquals(7, Forecast.forecastDays(7));
        assertEquals(3, Forecast.forecastDays(0));
        assertEquals(7, Forecast.forecastDays(16));
    }

    @Test
    public void wetBulbMatchesStull() {
        // Stull (2011): 20 °C and 50 % give a wet-bulb of about 13.7 °C.
        assertEquals(13.7, Forecast.wetBulb(20, 50), 0.1);
        assertEquals(true, Forecast.wetBulb(1.1, 86) < 1.0);
    }
}
