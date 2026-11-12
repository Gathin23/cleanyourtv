package io.github.abner97.remotebuttons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

/** {@link KeyMap} against real log lines from the TV and hand-written keys.txt content. */
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

    /** A press counts once: not its release, not a held key's repeats, not the queueing line, not bad input. */
    @Test public void countsOnlyTheFirstDownOfAPress() {
        assertEquals(190, KeyMap.keyCodeOfPress(YOUTUBE_PRESSED));
        assertEquals(KeyMap.NO_PRESS, KeyMap.keyCodeOfPress(YOUTUBE_RELEASED));
        assertEquals(KeyMap.NO_PRESS, KeyMap.keyCodeOfPress(VOLUME_UP_HELD));
        assertEquals(KeyMap.NO_PRESS, KeyMap.keyCodeOfPress(YOUTUBE_QUEUED));
        assertEquals(KeyMap.NO_PRESS, KeyMap.keyCodeOfPress(MALFORMED_REPEAT_COUNT));
        assertEquals(KeyMap.NO_PRESS, KeyMap.keyCodeOfPress(KEY_CODE_TOO_LONG_FOR_AN_INT));
    }

    /** The built-in map parses in full and covers every button measured on the Hisense remote. */
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

    /** Comments, spacing and CRLF are tolerated; each malformed line is dropped on its own. */
    @Test public void badLinesAreSkippedNotFatal() {
        Map<Integer, String> targetsByKeyCode = KeyMap.parseButtonMap(
                "# my remote\n\n190 = com.example.tube   # spaces and a comment\n"
                + "abc=com.example.x\n12=not a package\n=home\n99999=home\n7=home\r\n");
        assertEquals("com.example.tube", targetsByKeyCode.get(190));
        assertEquals("home", targetsByKeyCode.get(7));
        assertEquals(2, targetsByKeyCode.size());
    }

    /** What the card writes is read back as the same map, in the same order. */
    @Test public void formattedMapParsesBackUnchanged() {
        Map<Integer, String> targetsByKeyCode = KeyMap.parseButtonMap(KeyMap.HISENSE_DEFAULT_MAP);
        String mapText = KeyMap.formatButtonMap(targetsByKeyCode);
        assertTrue(mapText.startsWith("#"));
        assertEquals(targetsByKeyCode, KeyMap.parseButtonMap(mapText));
    }

    /** Assigning replaces or adds one button and leaves the original map alone; removing drops just that one. */
    @Test public void assignAndRemoveWorkOnACopy() {
        Map<Integer, String> original = KeyMap.parseButtonMap("190=com.example.tube\n83=settings\n");

        Map<Integer, String> reassigned = KeyMap.withButton(original, 190, "com.example.other");
        assertEquals("com.example.other", reassigned.get(190));
        assertEquals("com.example.tube", original.get(190));

        Map<Integer, String> added = KeyMap.withButton(original, 606, "home");
        assertEquals(3, added.size());

        Map<Integer, String> removed = KeyMap.withoutButton(original, 83);
        assertEquals(1, removed.size());
        assertEquals(2, original.size());
        assertEquals(original, KeyMap.withoutButton(original, 999));
    }

    /** A target that could not have come from keys.txt is refused, not saved. */
    @Test(expected = IllegalArgumentException.class)
    public void assigningAnInvalidTargetIsRefused() {
        KeyMap.withButton(KeyMap.parseButtonMap(""), 190, "com.example; reboot");
    }

    /** App buttons can be assigned; navigation, Back, Home, volume and power cannot. */
    @Test public void onlyNonNavigationKeysAreAssignable() {
        assertTrue(KeyMap.isAssignable(190));   // YouTube
        assertTrue(KeyMap.isAssignable(172));   // Live TV
        assertFalse(KeyMap.isAssignable(4));    // Back
        assertFalse(KeyMap.isAssignable(23));   // OK
        assertFalse(KeyMap.isAssignable(24));   // Volume up
        assertFalse(KeyMap.isAssignable(3));    // Home
    }

    /** Nothing but a package name or a named target can become an intent. */
    @Test public void acceptsOnlyPackageNamesSettingsAndHome() {
        assertTrue(KeyMap.isValidTarget("settings"));
        assertTrue(KeyMap.isValidTarget("home"));
        assertTrue(KeyMap.isValidTarget("com.netflix.ninja"));
        assertFalse(KeyMap.isValidTarget("netflix"));
        assertFalse(KeyMap.isValidTarget("com.example; reboot"));
    }
}
