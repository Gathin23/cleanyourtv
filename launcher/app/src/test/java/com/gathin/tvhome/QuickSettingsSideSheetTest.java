package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuickSettingsSideSheetTest {
    @Test
    public void switchOffAlwaysKeepsTheSmallPanel() {
        assertFalse(QuickSettingsActivity.opensSideSheet(false, false));
        assertFalse(QuickSettingsActivity.opensSideSheet(false, true));
    }

    @Test
    public void switchOnOpensTheSheetOverAnApp() {
        assertTrue(QuickSettingsActivity.opensSideSheet(true, false));
    }

    @Test
    public void switchOnKeepsTheSmallPanelOverHome() {
        assertFalse(QuickSettingsActivity.opensSideSheet(true, true));
    }
}
