package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherNowTest {
    private static Object[] row(Object place, Object temperature, Object code, Object day, Object updated) {
        return new Object[] {place, temperature, "C", code, day, updated};
    }

    @Test public void noRowIsNoLine() {
        assertNull(WeatherNow.fromRow(null));
    }

    @Test public void rowWithoutTemperatureOrTimeIsSkipped() {
        assertNull(WeatherNow.fromRow(row("Vigo", null, 3, 1, 1_000L)));
        assertNull(WeatherNow.fromRow(row("Vigo", 14, 3, 1, null)));
        assertNull(WeatherNow.fromRow(row("Vigo", 14, 3, 1, 0L)));
    }

    @Test public void readingShowsTemperatureAndPlace() {
        WeatherNow.Reading r = WeatherNow.fromRow(row(" Vigo ", 14, 3, 1, 1_000L));
        assertEquals("14°  Vigo", r.text());
        assertEquals(3, r.code);
        assertTrue(r.day);
        assertEquals(1_000L, r.updatedMs);
    }

    @Test public void noPlaceShowsTheTemperatureAlone() {
        assertEquals("-3°", WeatherNow.fromRow(row(null, -3, 0, 0, 1_000L)).text());
    }

    @Test public void nightIsZero() {
        assertFalse(WeatherNow.fromRow(row("Vigo", 9, 0, 0, 1_000L)).day);
        assertTrue(WeatherNow.fromRow(row("Vigo", 9, 0, null, 1_000L)).day);
    }

    @Test public void codesPickTheirSky() {
        assertEquals("\u2600\uFE0E", WeatherNow.sky(0, true));
        assertEquals("\u263E", WeatherNow.sky(1, false));
        assertEquals("\u26C5\uFE0E", WeatherNow.sky(2, true));
        assertEquals("\u2601\uFE0E", WeatherNow.sky(2, false));
        assertEquals("\u2601\uFE0E", WeatherNow.sky(3, true));
        assertEquals("\u2248", WeatherNow.sky(45, true));
        assertEquals("\u2602\uFE0E", WeatherNow.sky(61, true));
        assertEquals("\u2744\uFE0E", WeatherNow.sky(73, true));
        assertEquals("\u2744\uFE0E", WeatherNow.sky(85, true));
        assertEquals("\u26A1\uFE0E", WeatherNow.sky(95, true));
    }
}
