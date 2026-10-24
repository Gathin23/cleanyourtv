package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/** hide_apps_until_down: which key reveals or hides the apps, and the release swallowed after a reveal. */
public class AppsGateTest {
    private static final int DOWN = KeyEvent.ACTION_DOWN, UP = KeyEvent.ACTION_UP;

    @Test
    public void downRevealsAndItsReleaseIsSwallowed() {
        AppsGate gate = new AppsGate(true);
        assertEquals(AppsGate.Action.REVEAL, gate.onKey(KeyEvent.KEYCODE_DPAD_DOWN, DOWN, true));
        assertFalse(gate.hidden());
        assertEquals(AppsGate.Action.CONSUME, gate.onKey(KeyEvent.KEYCODE_DPAD_DOWN, UP, false));
        // The next press is an ordinary one again.
        assertEquals(AppsGate.Action.PASS, gate.onKey(KeyEvent.KEYCODE_DPAD_DOWN, DOWN, false));
    }

    @Test
    public void okRevealsWithoutOpeningTheDockApp() {
        for (int ok : new int[]{KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER}) {
            AppsGate gate = new AppsGate(true);
            assertEquals(AppsGate.Action.REVEAL, gate.onKey(ok, DOWN, true));
            assertEquals(AppsGate.Action.CONSUME, gate.onKey(ok, DOWN, false));   // a held OK repeats
            assertEquals(AppsGate.Action.CONSUME, gate.onKey(ok, UP, false));     // never reaches the tile
            assertEquals(AppsGate.Action.PASS, gate.onKey(ok, DOWN, false));
        }
    }

    @Test
    public void upGoesToTheTopBarAndTheAppsStayHidden() {
        AppsGate gate = new AppsGate(true);
        assertEquals(AppsGate.Action.FOCUS_TOP, gate.onKey(KeyEvent.KEYCODE_DPAD_UP, DOWN, true));
        assertTrue(gate.hidden());
        // In the top bar the keys are the bar's, OK included (it opens the pill, not the apps)...
        assertEquals(AppsGate.Action.PASS, gate.onKey(KeyEvent.KEYCODE_DPAD_UP, UP, false));
        assertEquals(AppsGate.Action.PASS, gate.onKey(KeyEvent.KEYCODE_DPAD_LEFT, DOWN, false));
        assertEquals(AppsGate.Action.PASS, gate.onKey(KeyEvent.KEYCODE_DPAD_CENTER, DOWN, false));
        // ...except Down, which brings the apps up as it does from the gate.
        assertEquals(AppsGate.Action.REVEAL, gate.onKey(KeyEvent.KEYCODE_DPAD_DOWN, DOWN, false));
    }

    @Test
    public void backFromTheTopBarReturnsToThePhotoFrame() {
        AppsGate gate = new AppsGate(true);
        assertEquals(AppsGate.Action.CONSUME, gate.onKey(KeyEvent.KEYCODE_BACK, DOWN, false));
        assertEquals(AppsGate.Action.HIDE, gate.onKey(KeyEvent.KEYCODE_BACK, UP, false));
    }

    @Test
    public void onTheGateSidewaysAndBackDoNothing() {
        AppsGate gate = new AppsGate(true);
        assertEquals(AppsGate.Action.CONSUME, gate.onKey(KeyEvent.KEYCODE_DPAD_LEFT, DOWN, true));
        assertEquals(AppsGate.Action.CONSUME, gate.onKey(KeyEvent.KEYCODE_DPAD_RIGHT, DOWN, true));
        assertEquals(AppsGate.Action.CONSUME, gate.onKey(KeyEvent.KEYCODE_BACK, DOWN, true));
        assertEquals(AppsGate.Action.CONSUME, gate.onKey(KeyEvent.KEYCODE_BACK, UP, true));
        assertTrue(gate.hidden());
        // Keys that are not navigation (volume, the colour keys) go on as usual.
        assertEquals(AppsGate.Action.PASS, gate.onKey(KeyEvent.KEYCODE_VOLUME_UP, DOWN, true));
    }

    @Test
    public void backHidesTheShownApps() {
        AppsGate gate = new AppsGate(false);
        assertEquals(AppsGate.Action.PASS, gate.onKey(KeyEvent.KEYCODE_DPAD_RIGHT, DOWN, false));
        assertEquals(AppsGate.Action.CONSUME, gate.onKey(KeyEvent.KEYCODE_BACK, DOWN, false));
        assertEquals(AppsGate.Action.HIDE, gate.onKey(KeyEvent.KEYCODE_ESCAPE, UP, false));
    }

    @Test
    public void homeOnHomeToggles() {
        AppsGate gate = new AppsGate(true);
        assertEquals(AppsGate.Action.REVEAL, gate.onHomeAgain());   // the photo frame: Home brings the apps up
        assertFalse(gate.hidden());
        assertEquals(AppsGate.Action.HIDE, gate.onHomeAgain());     // and puts them away again
        assertTrue(gate.hidden());
        // Home is not a key the activity sees, so nothing is left to swallow after it.
        assertEquals(AppsGate.Action.REVEAL, gate.onHomeAgain());
        assertEquals(AppsGate.Action.PASS, gate.onKey(KeyEvent.KEYCODE_DPAD_CENTER, UP, false));
    }

    @Test
    public void hideForgetsAPendingSwallow() {
        AppsGate gate = new AppsGate(true);
        gate.onKey(KeyEvent.KEYCODE_DPAD_CENTER, DOWN, true);
        gate.hide();   // Home pressed before OK was released
        assertTrue(gate.hidden());
        assertEquals(AppsGate.Action.REVEAL, gate.onKey(KeyEvent.KEYCODE_DPAD_CENTER, DOWN, true));
    }
}
