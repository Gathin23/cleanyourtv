package io.github.justinnewman.bedtimelock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** PIN format, hashing, and how the remote's keys turn into digits. */
public class PinTest {
    @Test
    public void onlyFourDigitsAreAPin() {
        assertTrue(Pin.valid("0420"));
        assertFalse(Pin.valid("042"));
        assertFalse(Pin.valid("04201"));
        assertFalse(Pin.valid("04a0"));
        assertFalse(Pin.valid(null));
    }

    @Test
    public void theHashDependsOnTheSaltAndThePin() {
        assertEquals(Pin.hash("s", "1234"), Pin.hash("s", "1234"));
        assertNotEquals(Pin.hash("s", "1234"), Pin.hash("t", "1234"));
        assertNotEquals(Pin.hash("s", "1234"), Pin.hash("s", "1235"));
        assertEquals(64, Pin.hash("s", "1234").length());
        assertNotEquals(Pin.newSalt(), Pin.newSalt());
    }

    @Test
    public void numberKeysAndTheKeypadTypeDigits() {
        assertEquals(0, Pin.digitOf(7));      // KEYCODE_0
        assertEquals(9, Pin.digitOf(16));     // KEYCODE_9
        assertEquals(5, Pin.digitOf(149));    // KEYCODE_NUMPAD_5
        assertEquals(-1, Pin.digitOf(19));    // KEYCODE_DPAD_UP
        assertEquals(-1, Pin.digitOf(23));    // KEYCODE_DPAD_CENTER
    }

    @Test
    public void upAndDownCycleThroughTheDigits() {
        assertEquals(0, Pin.up(-1));
        assertEquals(1, Pin.up(0));
        assertEquals(0, Pin.up(9));
        assertEquals(9, Pin.down(-1));
        assertEquals(9, Pin.down(0));
        assertEquals(4, Pin.down(5));
    }
}
