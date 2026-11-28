package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeKeyServiceTest {
    private static final long SOON = 45_000L;

    @Test public void aKeyOnHomeWithNoHelperBoundDoesNothing() {
        HomeKeyService.running = null;
        HomeKeyService.keyOnHome();
    }

    @Test public void aTapIsAShortPress() {
        assertTrue(HomeKeyService.isShortPress(1_000, 1_120, 400));
    }

    @Test public void holdingPastTheTimeoutIsALongPress() {
        assertFalse(HomeKeyService.isShortPress(1_000, 1_400, 400));
        assertFalse(HomeKeyService.isShortPress(1_000, 3_000, 400));
    }

    @Test public void firstBindOfANewBootProbes() {
        assertTrue(HomeKeyService.firstBindAfterBoot(12, 11, SOON));
        assertTrue(HomeKeyService.firstBindAfterBoot(1, -1, SOON));
    }

    @Test public void aRebindInTheSameBootDoesNot() {
        assertFalse(HomeKeyService.firstBindAfterBoot(12, 12, SOON));
    }

    @Test public void aBindLongAfterBootDoesNot() {
        assertFalse(HomeKeyService.firstBindAfterBoot(12, 11, HomeKeyService.BOOT_WINDOW_MS));
    }

    @Test public void noBootCountMeansNoProbe() {
        assertFalse(HomeKeyService.firstBindAfterBoot(-1, -1, SOON));
    }

    private static final String TV = "com.mediatek.wwtv.tvcenter";

    @Test public void liveTvBeforeAnyKeyGoesHome() {
        assertTrue(HomeKeyService.powerOnRedirect(TV, TV, SOON, HomeKeyService.BOOT_WINDOW_MS));
    }

    @Test public void otherAppsAndInputsAreLeftAlone() {
        assertFalse(HomeKeyService.powerOnRedirect(TV, "com.netflix.ninja", SOON, HomeKeyService.BOOT_WINDOW_MS));
        assertFalse(HomeKeyService.powerOnRedirect(TV, null, SOON, HomeKeyService.BOOT_WINDOW_MS));
    }

    @Test public void afterAKeyOrTheWindowLiveTvStays() {
        assertFalse(HomeKeyService.powerOnRedirect(TV, TV, SOON, 0));
        assertFalse(HomeKeyService.powerOnRedirect(TV, TV, HomeKeyService.BOOT_WINDOW_MS, HomeKeyService.BOOT_WINDOW_MS));
    }

    @Test public void offMeansNothing() {
        assertFalse(HomeKeyService.powerOnRedirect("", TV, SOON, HomeKeyService.BOOT_WINDOW_MS));
    }

    @Test public void alwaysGoesHomeAfterScreenOnUntilAKey() {
        assertTrue(HomeKeyService.stillArmed(SOON, SOON + HomeKeyService.SCREEN_ON_WINDOW_MS));
        assertFalse(HomeKeyService.stillArmed(SOON, 0));
    }

    @Test public void looksAgainOnlyWhileTheStockHomeKeptTheScreen() {
        assertTrue(HomeKeyService.redirectAgain(false, false, 2));
        assertFalse(HomeKeyService.redirectAgain(true, false, 2));
        assertFalse(HomeKeyService.redirectAgain(false, true, 2));
        assertFalse(HomeKeyService.redirectAgain(false, false, 0));
    }

    private static final java.util.Set<String> LAUNCHERX =
            java.util.Collections.singleton("com.google.android.apps.tv.launcherx.home.HomeActivity");
    private static final java.util.Set<String> PATCHWALL = java.util.Collections.singleton("com.mitv.tvhome.atv");

    private static java.util.function.BooleanSupplier never() {
        return () -> { throw new AssertionError("the activity lookup ran"); };
    }

    @Test public void aStockHomeActivityIsMatchedByName() {
        String home = LAUNCHERX.iterator().next();
        assertTrue(HomeKeyService.isStockHomeWindow(LAUNCHERX, false, home, never()));
        assertFalse(HomeKeyService.isStockHomeWindow(LAUNCHERX, false, "com.netflix.ninja.MainActivity", never()));
    }

    @Test public void aHomeWithNoHomeActivityCountsAnyOfItsActivities() {
        assertTrue(HomeKeyService.isStockHomeWindow(PATCHWALL, true, "com.mitv.tvhome.atv.app.PatchWallActivity", () -> true));
    }

    @Test public void itsToastsAndDialogsDoNot() {
        assertFalse(HomeKeyService.isStockHomeWindow(PATCHWALL, true, "android.widget.Toast$TN", () -> false));
    }

    @Test public void noRedirectSetOrNoClassMeansNothing() {
        assertFalse(HomeKeyService.isStockHomeWindow(java.util.Collections.emptySet(), false, "x", never()));
        assertFalse(HomeKeyService.isStockHomeWindow(PATCHWALL, true, null, never()));
    }
}
