package io.github.abner97.remotebuttons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

public class KeyMapTest {
    // Lines captured on a Hisense SmartTV 4K FFM (lushan), Google TV 12, while the remote's buttons were pressed.
    private static final String YOUTUBE_PRESSED =
            "D/WindowManager(  818): interceptKeyTi keyCode=190 down=true repeatCount=0 keyguardOn=false canceled=false";
    private static final String YOUTUBE_RELEASED =
            "D/WindowManager(  818): interceptKeyTi keyCode=190 down=false repeatCount=0 keyguardOn=false canceled=false";
    private static final String VOLUME_UP_HELD =
            "D/WindowManager(  818): interceptKeyTi keyCode=24 down=true repeatCount=3 keyguardOn=false canceled=false";
    private static final String YOUTUBE_QUEUED =
            "D/WindowManager(  818): interceptKeyTq keycode=190 scancode=469 interactive=true keyguardActive=false";
    private static final String MALFORMED_REPEAT_COUNT =
            "D/WindowManager(  818): interceptKeyTi keyCode=1900 down=true repeatCount=01 x";
    private static final String KEY_CODE_TOO_LONG_FOR_AN_INT =
            "D/WindowManager(  818): interceptKeyTi keyCode=99999999999 down=true repeatCount=0 x";

    @Test public void countsOnlyTheFirstDownOfAPress() {
        assertEquals(190, KeyMap.keyCodeOfPress(YOUTUBE_PRESSED));
        assertEquals(KeyMap.NO_PRESS, KeyMap.keyCodeOfPress(YOUTUBE_RELEASED));
        assertEquals(KeyMap.NO_PRESS, KeyMap.keyCodeOfPress(VOLUME_UP_HELD));
        assertEquals(KeyMap.NO_PRESS, KeyMap.keyCodeOfPress(YOUTUBE_QUEUED));
        assertEquals(KeyMap.NO_PRESS, KeyMap.keyCodeOfPress(MALFORMED_REPEAT_COUNT));
        assertEquals(KeyMap.NO_PRESS, KeyMap.keyCodeOfPress(KEY_CODE_TOO_LONG_FOR_AN_INT));
    }

    @Test public void defaultMapCoversTheHisenseButtons() {
        Map<Integer, String> targetsByKeyCode = KeyMap.parseButtonMap(KeyMap.HISENSE_DEFAULT_MAP);
        assertEquals("settings", targetsByKeyCode.get(83));
        assertEquals("com.google.android.youtube.tv", targetsByKeyCode.get(190));
        assertEquals("com.amazon.amazonvideo.livingroom", targetsByKeyCode.get(295));
        assertEquals("com.disney.disneyplus", targetsByKeyCode.get(607));
        assertEquals("home", targetsByKeyCode.get(284));
        assertEquals("com.vt.tv.tvcenter", targetsByKeyCode.get(172));
        assertEquals(6, targetsByKeyCode.size());
    }

    @Test public void badLinesAreSkippedNotFatal() {
        Map<Integer, String> targetsByKeyCode = KeyMap.parseButtonMap(
                "# my remote\n\n190 = com.example.tube   # spaces and a comment\n"
                + "abc=com.example.x\n12=not a package\n=home\n99999=home\n7=home\r\n");
        assertEquals("com.example.tube", targetsByKeyCode.get(190));
        assertEquals("home", targetsByKeyCode.get(7));
        assertEquals(2, targetsByKeyCode.size());
    }

    @Test public void acceptsOnlyPackageNamesSettingsAndHome() {
        assertTrue(KeyMap.isValidTarget("settings"));
        assertTrue(KeyMap.isValidTarget("home"));
        assertTrue(KeyMap.isValidTarget("com.netflix.ninja"));
        assertFalse(KeyMap.isValidTarget("netflix"));
        assertFalse(KeyMap.isValidTarget("com.example; reboot"));
    }
}
