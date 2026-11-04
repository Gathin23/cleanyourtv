package com.gathin.tvhome;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemoteKeysTest {
    private static Map<Integer, String> parse(String... entries) {
        // Key names resolved by a stand-in: android.view.KeyEvent isn't available in JVM tests.
        return RemoteKeys.parseAppKeys(java.util.Arrays.asList(entries),
                name -> name.equals("KEYCODE_BUTTON_3") ? 190 : 0);
    }

    @Test public void keyNamesWorkLikeNumbers() {
        Map<Integer, String> keys = parse("KEYCODE_BUTTON_3:com.google.android.youtube.tv", "KEYCODE_NOT_A_KEY:a.b");
        assertEquals(1, keys.size());
        assertEquals("com.google.android.youtube.tv", keys.get(190));
    }

    @Test public void keycodeAndPackage() {
        Map<Integer, String> keys = parse(
                "190:com.google.android.youtube.tv", " 193 : com.netflix.ninja ");
        assertEquals(2, keys.size());
        assertEquals("com.google.android.youtube.tv", keys.get(190));
        assertEquals("com.netflix.ninja", keys.get(193));
    }

    @Test public void badEntriesAreSkipped() {
        Map<Integer, String> keys = parse(
                "com.google.android.youtube.tv", "youtube:com.google.android.youtube.tv", "190:", "");
        assertTrue(keys.isEmpty());
    }

    @Test public void theLastEntryForAKeycodeWins() {
        Map<Integer, String> keys = parse("190:a.b", "190:c.d");
        assertEquals("c.d", keys.get(190));
    }

    @Test public void scancodeEntries() {
        java.util.List<String> entries = java.util.Arrays.asList(
                "scan:490:com.wbd.hbomax", " scan : 491 : a.b ", "scan:x:a.b", "scan:12:", "190:c.d", "scan:491:e.f");
        Map<Integer, String> scans = RemoteKeys.parseAppScancodes(entries);
        assertEquals(2, scans.size());
        assertEquals("com.wbd.hbomax", scans.get(490));
        assertEquals("e.f", scans.get(491));
        // and the keycode parser leaves scancode entries alone
        assertEquals(1, parse(entries.toArray(new String[0])).size());
    }
}
