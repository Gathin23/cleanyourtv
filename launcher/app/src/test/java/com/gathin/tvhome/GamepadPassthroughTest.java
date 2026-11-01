package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GamepadPassthroughTest {
    // Sources as dumpsys input reports them on a TCL G07.
    @Test public void xboxControllerIsAGamepad() {
        assertTrue(GamepadPassthrough.isGamepad(0x01000511));
    }

    @Test public void voiceRemoteWithJoystickIsNot() {
        assertFalse(GamepadPassthrough.isGamepad(0x01000311));
    }

    @Test public void virtualSearchKeyboardIsNot() {
        assertFalse(GamepadPassthrough.isGamepad(0x00000701));
    }

    @Test public void keypadIsNot() {
        assertFalse(GamepadPassthrough.isGamepad(0x00000101));
    }
}
