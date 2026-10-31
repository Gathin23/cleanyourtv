package io.github.maxhayim.smarthome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ConfigTest {
    @Test
    public void glanceFromJsonArrayKeepsOrderAndDropsBlanks() throws Exception {
        JSONArray a = new JSONArray();
        a.put("weather.home");
        a.put(" ");
        a.put("sensor.station_e10");
        a.put("weather.home");
        List<String> ids = Config.parseGlance(a);
        assertEquals(Arrays.asList("weather.home", "sensor.station_e10"), ids);
    }

    @Test
    public void glanceFromCommaString() {
        assertEquals(Arrays.asList("weather.home", "sensor.e10"),
                Config.parseGlance("weather.home, sensor.e10"));
        assertTrue(Config.parseGlance("").isEmpty());
        assertTrue(Config.parseGlance(null).isEmpty());
    }
}
