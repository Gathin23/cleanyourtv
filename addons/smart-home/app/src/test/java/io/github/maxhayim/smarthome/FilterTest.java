package io.github.maxhayim.smarthome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The show, hide and hide_unavailable filters on the list rows. */
public class FilterTest {
    private static final List<String> NONE = Collections.emptyList();

    private static List<String> rowIds(List<Ha.Entity> all) {
        List<String> ids = new ArrayList<>();
        for (Ha.Entity e : all) if (Ha.inList(e)) ids.add(e.id);
        return ids;
    }

    @Test
    public void showKeepsOnlyThoseInItsOrderWithinASection() throws Exception {
        List<Ha.Entity> all = Ha.parse(HaTest.STATES, NONE,
                Arrays.asList("light.living_room", "scene.good_night", "light.hall"), NONE, false);
        // Scenes come before lights as sections; inside the lights, the order given (Living room before Hall).
        assertEquals(Arrays.asList("scene.good_night", "light.living_room", "light.hall"), rowIds(all));
    }

    @Test
    public void showLeavesTheGlanceHeaderAlone() throws Exception {
        List<Ha.Entity> all = Ha.parse(HaTest.STATES, NONE, Collections.singletonList("light.hall"), NONE, false);
        assertEquals(Ha.Kind.WEATHER, all.get(0).kind);
        assertEquals(2, Ha.glance(all, NONE).prices.size());
    }

    @Test
    public void hideTakesIdsAndPrefixes() throws Exception {
        List<Ha.Entity> all = Ha.parse(HaTest.STATES, NONE, NONE, Arrays.asList("light.*", "lock.front_door"), false);
        List<String> ids = rowIds(all);
        assertFalse(ids.contains("light.hall"));
        assertFalse(ids.contains("light.living_room"));
        assertFalse(ids.contains("lock.front_door"));
        assertTrue(ids.contains("switch.fan_plug"));
    }

    @Test
    public void hideUnavailableDropsThem() throws Exception {
        assertTrue(rowIds(Ha.parse(HaTest.STATES, NONE, NONE, NONE, false)).contains("light.garage"));
        assertFalse(rowIds(Ha.parse(HaTest.STATES, NONE, NONE, NONE, true)).contains("light.garage"));
    }

    @Test
    public void aSceneThatNeverRanStaysAndCanRun() throws Exception {
        String json = "[{\"entity_id\":\"scene.never\",\"state\":\"unknown\",\"attributes\":{\"friendly_name\":\"Never\"}},"
                + "{\"entity_id\":\"scene.gone\",\"state\":\"unavailable\",\"attributes\":{\"friendly_name\":\"Gone\"}},"
                + "{\"entity_id\":\"light.lost\",\"state\":\"unknown\",\"attributes\":{\"friendly_name\":\"Lost\"}}]";
        List<Ha.Entity> all = Ha.parse(json, NONE, NONE, NONE, true);
        assertEquals(Collections.singletonList("scene.never"), rowIds(all));
        assertEquals("Run", all.get(0).value());
        assertTrue(Ha.press(all.get(0)) != null);
    }

    @Test
    public void aShownRowIsNotLostBehindTheCap() throws Exception {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < 120; i++) {
            if (i > 0) b.append(',');
            b.append(String.format("{\"entity_id\":\"switch.s%03d\",\"state\":\"off\",\"attributes\":{\"friendly_name\":\"S%03d\"}}", i, i));
        }
        b.append(']');
        List<Ha.Entity> all = Ha.parse(b.toString(), NONE, Collections.singletonList("switch.s110"), NONE, false);
        assertEquals(Collections.singletonList("switch.s110"), rowIds(all));
        // And with no filter the cap still holds.
        assertEquals(Ha.MAX_ENTITIES, rowIds(Ha.parse(b.toString())).size());
    }
}
