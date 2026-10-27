package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ButtonMapTest {

    @Test public void buttonsKeepListOrderAndSkipMalformedEntries() {
        Map<Integer, String> buttons = ButtonMap.buttons(Arrays.asList(
                "4137=TCL Home", "4020 = TCL Channel", "Web", "x=Bad", "0=Zero", "4159=", null, "4159=Web"));
        assertEquals(Arrays.asList(4137, 4020, 4159), Arrays.asList(buttons.keySet().toArray()));
        assertEquals("TCL Channel", buttons.get(4020));
    }

    @Test public void noChoiceLeavesAppKeysAlone() {
        List<String> keys = Arrays.asList("4062:com.netflix.ninja");
        assertSame(keys, ButtonMap.apply(keys, Collections.emptyMap()));
    }

    @Test public void aChoiceReplacesOnlyThatButtonsTapEntry() {
        Map<Integer, String> chosen = new LinkedHashMap<>();
        chosen.put(4137, "com.spotify.tv.android");
        List<String> keys = ButtonMap.apply(Arrays.asList("4062:com.netflix.ninja", "4137:ignore",
                "hold:4137:sleep", "41370:com.example"), chosen);
        assertEquals(Arrays.asList("4062:com.netflix.ninja", "hold:4137:sleep", "41370:com.example",
                "4137:com.spotify.tv.android"), keys);
        assertEquals("com.spotify.tv.android", RemoteKeys.parseAppKeys(keys, name -> 0).get(4137));
    }

    @Test public void aButtonWithoutAnAppKeysEntryIsAdded() {
        Map<Integer, String> chosen = Collections.singletonMap(4020, ButtonMap.NOTHING);
        assertEquals(Arrays.asList("4020:ignore"), ButtonMap.apply(Collections.emptyList(), chosen));
    }

    @Test public void nothingIsTheIgnoreAction() {
        assertTrue(RemoteKeys.isAction(ButtonMap.NOTHING));
    }
}
