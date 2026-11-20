package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GuardKeepTest {
    @Test public void anotherLauncherAsHomeStopsTheGuard() {
        assertTrue(GuardKeep.otherHome("com.google.android.apps.tv.launcherx", "com.gathin.tvhome", ""));
    }

    @Test public void thisLauncherTheChooserOrNoAnswerKeepTheGuard() {
        assertFalse(GuardKeep.otherHome("com.gathin.tvhome", "com.gathin.tvhome", ""));
        assertFalse(GuardKeep.otherHome("android", "com.gathin.tvhome", ""));
        assertFalse(GuardKeep.otherHome(null, "com.gathin.tvhome", ""));
    }

    @Test public void theStockHomeRedirectLauncherKeepsTheGuard() {
        // stock_home_redirect needs HomeKeyService while the stock launcher is still Android's Home.
        assertFalse(GuardKeep.otherHome("com.google.android.apps.tv.launcherx", "com.gathin.tvhome",
                "com.google.android.apps.tv.launcherx"));
    }

    private static final String ME = "com.gathin.tvhome";
    private static final String OTHER = "com.google.android.apps.tv.launcherx";

    @Test public void thisLauncherAsHomeRepairsAndAdds() {
        assertTrue(GuardKeep.mayRepair(ME, ME, ""));
        assertTrue(GuardKeep.mayAdd(true, false, ME, ME, ""));
    }

    @Test public void anotherThirdPartyHomeNeitherRepairsNorAdds() {
        assertFalse(GuardKeep.mayRepair(OTHER, ME, ""));
        assertFalse(GuardKeep.mayAdd(true, false, OTHER, ME, ""));
    }

    @Test public void stockHomeWithRedirectOnStillRepairsAndAdds() {
        assertTrue(GuardKeep.mayRepair(OTHER, ME, OTHER));
        assertTrue(GuardKeep.mayAdd(true, false, OTHER, ME, OTHER));
    }

    @Test public void theAddNeedsTheOptInAndNoSwitchOff() {
        assertFalse(GuardKeep.mayAdd(false, false, ME, ME, ""));
        assertFalse(GuardKeep.mayAdd(true, true, ME, ME, ""));
    }
}
