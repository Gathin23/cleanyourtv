package io.github.abner97.remotebuttons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

public class KeyMapTest {
    // Lines captured on a Hisense SmartTV 4K FFM (lushan), Google TV 12, while the remote's buttons were pressed.
    private static final String YOUTUBE_DOWN =
            "D/WindowManager(  818): interceptKeyTi keyCode=190 down=true repeatCount=0 keyguardOn=false canceled=false";
    private static final String YOUTUBE_UP =
            "D/WindowManager(  818): interceptKeyTi keyCode=190 down=false repeatCount=0 keyguardOn=false canceled=false";
    private static final String VOLUME_HELD =
            "D/WindowManager(  818): interceptKeyTi keyCode=24 down=true repeatCount=3 keyguardOn=false canceled=false";
    private static final String QUEUEING =
            "D/WindowManager(  818): interceptKeyTq keycode=190 scancode=469 interactive=true keyguardActive=false";

    @Test public void countsOnlyTheFirstDownOfAPress() {
        assertEquals(190, KeyMap.pressed(YOUTUBE_DOWN));
        assertEquals(-1, KeyMap.pressed(YOUTUBE_UP));
        assertEquals(-1, KeyMap.pressed(VOLUME_HELD));
        assertEquals(-1, KeyMap.pressed(QUEUEING));
        assertEquals(-1, KeyMap.pressed(
                "D/WindowManager(  818): interceptKeyTi keyCode=1900 down=true repeatCount=01 x"));
    }

    @Test public void defaultsCoverTheHisenseButtons() {
        Map<Integer, String> m = KeyMap.parse(KeyMap.DEFAULTS);
        assertEquals("settings", m.get(83));
        assertEquals("com.google.android.youtube.tv", m.get(190));
        assertEquals("com.amazon.amazonvideo.livingroom", m.get(295));
        assertEquals("com.disney.disneyplus", m.get(607));
        assertEquals("home", m.get(284));
        assertEquals("com.vt.tv.tvcenter", m.get(172));
        assertEquals(6, m.size());
    }

    @Test public void badLinesAreSkippedNotFatal() {
        Map<Integer, String> m = KeyMap.parse(
                "# my remote\n\n190 = com.example.tube   # spaces and a comment\n"
                + "abc=com.example.x\n12=not a package\n=home\n99999=home\n7=home\r\n");
        assertEquals("com.example.tube", m.get(190));
        assertEquals("home", m.get(7));
        assertEquals(2, m.size());
    }

    @Test public void targets() {
        assertTrue(KeyMap.validTarget("settings"));
        assertTrue(KeyMap.validTarget("com.netflix.ninja"));
        assertFalse(KeyMap.validTarget("netflix"));
        assertFalse(KeyMap.validTarget("com.example; reboot"));
    }
}
