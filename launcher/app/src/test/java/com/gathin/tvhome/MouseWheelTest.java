package com.gathin.tvhome;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MouseWheelTest {
    @Test public void wheelTowardsYouIsDownAndAwayIsUp() {
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, MouseWheel.keyFor(-1f, 0f, false, false));
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, MouseWheel.keyFor(1f, 0f, false, false));
        // A right-to-left grid keeps its rows: Up and Down do not mirror.
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, MouseWheel.keyFor(-1f, 0f, false, true));
    }

    @Test public void singleRowWalksTheRowInReadingOrder() {
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, MouseWheel.keyFor(-1f, 0f, true, false));
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, MouseWheel.keyFor(1f, 0f, true, false));
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, MouseWheel.keyFor(-1f, 0f, true, true));
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, MouseWheel.keyFor(1f, 0f, true, true));
    }

    @Test public void horizontalWheelMovesTheWayItTurnsAndTheLargerAxisWins() {
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, MouseWheel.keyFor(0f, 1f, false, false));
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, MouseWheel.keyFor(0f, -1f, false, true));
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, MouseWheel.keyFor(-0.2f, 0.9f, false, false));
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, MouseWheel.keyFor(-0.9f, 0.2f, false, false));
        assertEquals(0, MouseWheel.keyFor(0f, 0f, false, false));
    }

    @Test public void eachNotchIsOnePress() {
        MouseWheel wheel = new MouseWheel();
        assertEquals(1, wheel.presses(KeyEvent.KEYCODE_DPAD_DOWN, 1f));
        assertEquals(1, wheel.presses(KeyEvent.KEYCODE_DPAD_DOWN, 1f));
        assertEquals(0, wheel.presses(0, 0f));
    }

    @Test public void touchpadFractionsAddUpToWholePresses() {
        MouseWheel wheel = new MouseWheel();
        int total = 0;
        for (int i = 0; i < 10; i++) total += wheel.presses(KeyEvent.KEYCODE_DPAD_DOWN, 0.1f);
        assertEquals(1, total);
        assertEquals(0, wheel.presses(KeyEvent.KEYCODE_DPAD_DOWN, 0.4f));
        assertEquals(1, wheel.presses(KeyEvent.KEYCODE_DPAD_DOWN, 0.6f));
    }

    @Test public void turningBackStartsAgainFromNothing() {
        MouseWheel wheel = new MouseWheel();
        assertEquals(0, wheel.presses(KeyEvent.KEYCODE_DPAD_DOWN, 0.9f));
        assertEquals(0, wheel.presses(KeyEvent.KEYCODE_DPAD_UP, 0.5f));
        assertEquals(1, wheel.presses(KeyEvent.KEYCODE_DPAD_UP, 0.5f));
    }

    @Test public void aFastFlingIsCapped() {
        MouseWheel wheel = new MouseWheel();
        assertEquals(MouseWheel.MAX_PRESSES, wheel.presses(KeyEvent.KEYCODE_DPAD_DOWN, 12f));
        // The capped-off rest does not keep moving the focus on the next notch.
        assertEquals(1, wheel.presses(KeyEvent.KEYCODE_DPAD_DOWN, 1f));
    }
}
