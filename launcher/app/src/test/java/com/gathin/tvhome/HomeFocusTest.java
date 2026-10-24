package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** When a Home press focuses the first tile at once, and when it waits for window focus (#1204). */
public class HomeFocusTest {

    @Test
    public void homeOnScreenWithWindowFocusFocusesAtOnce() {
        HomeFocus focus = new HomeFocus();
        assertTrue(focus.onHomeIntent(false, true));
        assertFalse(focus.onWindowFocusChanged(true));
    }

    @Test
    public void homeWhileStoppedWaitsForWindowFocus() {
        HomeFocus focus = new HomeFocus();
        assertFalse(focus.onHomeIntent(true, false));
        assertFalse(focus.onWindowFocusChanged(false));
        assertTrue(focus.onWindowFocusChanged(true));
        assertFalse(focus.onWindowFocusChanged(true));
    }

    @Test
    public void homeOnScreenWithoutWindowFocusWaitsForWindowFocus() {
        HomeFocus focus = new HomeFocus();
        assertFalse(focus.onHomeIntent(false, false));
        assertTrue(focus.onWindowFocusChanged(true));
    }

    @Test
    public void pauseClearsAPendingHomeFocus() {
        HomeFocus focus = new HomeFocus();
        assertFalse(focus.onHomeIntent(true, false));
        focus.onPause();
        assertFalse(focus.onWindowFocusChanged(true));
    }
}
