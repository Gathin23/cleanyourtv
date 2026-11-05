package io.github.maxhayim.smarthome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Against the shapes Home Assistant's REST API documents for GET /api/states and the service calls. */
public class HaTest {
    // The documented example, plus one entity of each kind this app shows, as Home Assistant reports them.
    static final String STATES = "["
            + "{\"attributes\":{},\"entity_id\":\"sun.sun\",\"last_changed\":\"2016-05-30T21:43:32.418320+00:00\",\"state\":\"below_horizon\"},"
            + "{\"attributes\":{},\"entity_id\":\"process.Dropbox\",\"last_changed\":\"22016-05-30T21:43:32.418320+00:00\",\"state\":\"on\"},"
            + "{\"entity_id\":\"light.living_room\",\"state\":\"on\",\"attributes\":{\"friendly_name\":\"Living room\",\"brightness\":153}},"
            + "{\"entity_id\":\"light.hall\",\"state\":\"off\",\"attributes\":{\"friendly_name\":\"Hall\",\"brightness\":null}},"
            + "{\"entity_id\":\"switch.fan_plug\",\"state\":\"off\",\"attributes\":{\"friendly_name\":\"Fan plug\"}},"
            + "{\"entity_id\":\"scene.good_night\",\"state\":\"2026-10-22T21:00:00+00:00\",\"attributes\":{\"friendly_name\":\"Good night\"}},"
            + "{\"entity_id\":\"climate.nursery\",\"state\":\"heat\",\"attributes\":{\"friendly_name\":\"Nursery\",\"current_temperature\":21.4,\"temperature\":22}},"
            + "{\"entity_id\":\"sensor.nursery_temperature\",\"state\":\"21.43\",\"attributes\":{\"friendly_name\":\"Nursery temperature\",\"device_class\":\"temperature\",\"unit_of_measurement\":\"°C\"}},"
            + "{\"entity_id\":\"sensor.nursery_humidity\",\"state\":\"48\",\"attributes\":{\"friendly_name\":\"Nursery humidity\",\"device_class\":\"humidity\",\"unit_of_measurement\":\"%\"}},"
            + "{\"entity_id\":\"sensor.power\",\"state\":\"120\",\"attributes\":{\"device_class\":\"power\",\"unit_of_measurement\":\"W\"}},"
            + "{\"entity_id\":\"binary_sensor.front_door\",\"state\":\"off\",\"attributes\":{\"friendly_name\":\"Front door\",\"device_class\":\"door\"}},"
            + "{\"entity_id\":\"binary_sensor.motion\",\"state\":\"on\",\"attributes\":{\"device_class\":\"motion\"}},"
            + "{\"entity_id\":\"lock.front_door\",\"state\":\"locked\",\"attributes\":{\"friendly_name\":\"Front door lock\"}},"
            + "{\"entity_id\":\"light.garage\",\"state\":\"unavailable\",\"attributes\":{\"friendly_name\":\"Garage\"}}"
            + "]";

    private static Ha.Entity find(List<Ha.Entity> all, String id) {
        for (Ha.Entity e : all) if (e.id.equals(id)) return e;
        return null;
    }

    @Test
    public void keepsOnlyWhatTheScreenShowsInSections() throws Exception {
        List<Ha.Entity> all = Ha.parse(STATES);
        assertNull(find(all, "sun.sun"));
        assertNull(find(all, "sensor.power"));
        assertNull(find(all, "binary_sensor.motion"));
        assertEquals(Ha.Kind.SCENE, all.get(0).kind);
        assertEquals(Ha.Kind.LOCK, all.get(all.size() - 1).kind);
        assertEquals(10, all.size());
    }

    @Test
    public void values() throws Exception {
        List<Ha.Entity> all = Ha.parse(STATES);
        assertEquals("On · 60 %", find(all, "light.living_room").value());
        assertEquals("Off", find(all, "light.hall").value());
        assertEquals("21.4° → 22°", find(all, "climate.nursery").value());
        assertEquals("21.4°C", find(all, "sensor.nursery_temperature").value());
        assertEquals("48 %", find(all, "sensor.nursery_humidity").value());
        assertEquals("Closed", find(all, "binary_sensor.front_door").value());
        assertEquals("Locked", find(all, "lock.front_door").value());
        assertEquals("Unavailable", find(all, "light.garage").value());
        assertEquals("Run", find(all, "scene.good_night").value());
    }

    @Test
    public void serviceCalls() throws Exception {
        List<Ha.Entity> all = Ha.parse(STATES);
        Ha.Call scene = Ha.press(find(all, "scene.good_night"));
        assertEquals("/api/services/scene/turn_on", scene.path);
        assertEquals("{\"entity_id\":\"scene.good_night\"}", scene.body);
        assertEquals("/api/services/homeassistant/toggle", Ha.press(find(all, "switch.fan_plug")).path);
        Ha.Call dim = Ha.step(find(all, "light.living_room"), -1);
        assertEquals("/api/services/light/turn_on", dim.path);
        assertEquals("{\"entity_id\":\"light.living_room\",\"brightness_step_pct\":-10}", dim.body);
        Ha.Call warmer = Ha.step(find(all, "climate.nursery"), 1);
        assertEquals("/api/services/climate/set_temperature", warmer.path);
        assertEquals("{\"entity_id\":\"climate.nursery\",\"temperature\":22.5}", warmer.body);
    }

    @Test
    public void sensorsLocksAndUnavailableDoNothing() throws Exception {
        List<Ha.Entity> all = Ha.parse(STATES);
        assertNull("a TV remote never unlocks a door", Ha.press(find(all, "lock.front_door")));
        assertNull(Ha.step(find(all, "lock.front_door"), 1));
        assertNull(Ha.press(find(all, "sensor.nursery_temperature")));
        assertNull(Ha.press(find(all, "light.garage")));
        assertNull("switches don't dim", Ha.step(find(all, "switch.fan_plug"), 1));
    }

    @Test
    public void bigInstallationsAreCapped() throws Exception {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < 200; i++) {
            if (i > 0) b.append(',');
            b.append("{\"entity_id\":\"switch.s").append(i).append("\",\"state\":\"off\",\"attributes\":{}}");
        }
        assertEquals(Ha.MAX_ENTITIES, Ha.parse(b.append(']').toString()).size());
    }

    @Test
    public void addresses() {
        assertEquals("http://192.168.1.10:8123", Ha.normalizeUrl(" 192.168.1.10:8123/ "));
        assertEquals("https://ha.example.com", Ha.normalizeUrl("https://ha.example.com//"));
        assertTrue(Ha.validUrl("http://192.168.1.10:8123"));
        assertTrue(Ha.validUrl("https://abc.ui.nabu.casa"));
        assertFalse(Ha.validUrl("http://"));
        assertFalse(Ha.validUrl("http://a b"));
    }

    @Test
    public void problems() {
        assertTrue(Ha.problem(401).contains("token"));
        assertTrue(Ha.problem(0).contains("Can't reach"));
        assertTrue(Ha.problem(404).contains("isn't"));
    }
}
