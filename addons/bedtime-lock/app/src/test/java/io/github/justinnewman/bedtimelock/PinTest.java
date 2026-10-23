package io.github.justinnewman.bedtimelock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Code format, hashing, and how the remote's keys turn into digits. */
public class PinTest {
    @Test
    public void onlySixDigitsAreACode() {
        assertTrue(Pin.valid("248652"));
        assertFalse(Pin.valid("24865"));
        assertFalse(Pin.valid("2486521"));
        assertFalse(Pin.valid("24a652"));
        assertFalse(Pin.valid(null));
    }

    @Test
    public void theHashDependsOnTheSaltAndTheCode() {
        assertEquals(Pin.hash("s", "248652"), Pin.hash("s", "248652"));
        assertNotEquals(Pin.hash("s", "248652"), Pin.hash("t", "248652"));
        assertNotEquals(Pin.hash("s", "248652"), Pin.hash("s", "248653"));
        assertEquals(64, Pin.hash("s", "248652").length());
        assertNotEquals(Pin.newSalt(), Pin.newSalt());
    }

    @Test
    public void numberKeysAndTheKeypadTypeTheirDigit() {
        assertEquals(0, Pin.digitOf(7));      // KEYCODE_0
        assertEquals(9, Pin.digitOf(16));     // KEYCODE_9
        assertEquals(5, Pin.digitOf(149));    // KEYCODE_NUMPAD_5
    }

    @Test
    public void theArrowsAndOkTypeThePhoneKeypadDigits() {
        assertEquals(2, Pin.digitOf(19));     // DPAD_UP
        assertEquals(4, Pin.digitOf(21));     // DPAD_LEFT
        assertEquals(5, Pin.digitOf(23));     // DPAD_CENTER
        assertEquals(5, Pin.digitOf(66));     // ENTER
        assertEquals(6, Pin.digitOf(22));     // DPAD_RIGHT
        assertEquals(8, Pin.digitOf(20));     // DPAD_DOWN
    }

    @Test
    public void otherKeysTypeNothing() {
        assertEquals(-1, Pin.digitOf(4));     // BACK
        assertEquals(-1, Pin.digitOf(3));     // HOME
        assertEquals(-1, Pin.digitOf(24));    // VOLUME_UP
        assertEquals(-1, Pin.digitOf(67));    // DEL
    }
}
