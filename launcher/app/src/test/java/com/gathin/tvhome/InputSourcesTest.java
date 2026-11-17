package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InputSourcesTest {
    @Test public void mapsTheLastInputComponent() {
        assertEquals(Integer.valueOf(25), InputSources.mapped(
                Collections.singletonList("HW7=25"), "service/.Hdmi/HW7"));
    }

    @Test public void trimsMappingWhitespace() {
        assertEquals(Integer.valueOf(25), InputSources.mapped(
                Collections.singletonList(" HW7 = 25 "), "service/.Hdmi/HW7"));
    }

    @Test public void aDashHidesAnInput() {
        assertEquals(Integer.valueOf(-1), InputSources.mapped(
                Collections.singletonList("HW8=-"), "service/.Hdmi/HW8"));
    }

    @Test public void anUnknownOrMalformedInputIsNotMapped() {
        assertNull(InputSources.mapped(Arrays.asList("HW5=23", "HW7=nope"), "service/.Hdmi/HW6"));
        assertNull(InputSources.mapped(Collections.singletonList("HW7=nope"), "service/.Hdmi/HW7"));
        assertNull(InputSources.mapped(null, "service/.Hdmi/HW7"));
    }

    @Test public void anActivityIsAComponentAndAnActionIsNot() {
        assertTrue(InputSources.isComponent("com.xiaomi.mitv.tvplayer/.ExternalSourceActivity"));
        assertTrue(InputSources.isComponent("com.xiaomi.mitv.tvplayer/com.xiaomi.mitv.tvplayer.ExternalSourceActivity"));
        assertFalse(InputSources.isComponent("com.xgimi.action.hdmiPlayer"));
        assertFalse(InputSources.isComponent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY"));
        assertFalse(InputSources.isComponent(""));
        assertFalse(InputSources.isComponent(null));
        assertFalse(InputSources.isComponent("/.Activity"));
        assertFalse(InputSources.isComponent("com.example/"));
        assertFalse(InputSources.isComponent("a/b/c"));
    }
}
