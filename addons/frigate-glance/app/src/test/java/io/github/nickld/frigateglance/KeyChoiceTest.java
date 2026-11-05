package io.github.nickld.frigateglance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeyChoiceTest {
    @Test
    public void anOrdinaryButtonIsAccepted() {
        assertNull(KeyChoice.problem("KEYCODE_PROG_RED", "KEYCODE_DPAD_RIGHT"));
    }

    @Test
    public void okBackAndHomeAreRefused() {
        assertTrue(KeyChoice.problem("KEYCODE_DPAD_CENTER", "x").contains("OK"));
        assertTrue(KeyChoice.problem("KEYCODE_ENTER", "x").contains("OK"));
        assertTrue(KeyChoice.problem("KEYCODE_BACK", "x").contains("Back"));
        assertTrue(KeyChoice.problem("KEYCODE_HOME", "x").contains("Home"));
    }

    @Test
    public void theOtherCardKeyIsRefused() {
        assertEquals("That button is already the Snooze key.", KeyChoice.problem("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_LEFT", "Snooze"));
    }

    @Test
    public void unknownKeysAreRefused() {
        assertTrue(KeyChoice.problem("KEYCODE_UNKNOWN", "x").contains("can't"));
    }

    @Test
    public void eachOtherCardKeyIsNamed() {
        assertEquals("That button is already the Full screen key.",
                KeyChoice.problem("KEYCODE_DPAD_UP", java.util.Map.of("Snooze", "KEYCODE_DPAD_RIGHT", "Full screen", "KEYCODE_DPAD_UP")));
        assertNull(KeyChoice.problem("KEYCODE_MENU", java.util.Map.of("Snooze", "KEYCODE_DPAD_RIGHT")));
    }
}
