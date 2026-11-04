package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeHoldPanelTest {

    @Test
    public void opensOnlyWithTheSwitchOnAndNoDashboard() {
        assertTrue(HomeKeyService.holdOpensPanel(true, false));
        assertFalse(HomeKeyService.holdOpensPanel(true, true));    // the TV's own dashboard keeps the press
        assertFalse(HomeKeyService.holdOpensPanel(false, false));  // off by default: nothing changes
    }
}
