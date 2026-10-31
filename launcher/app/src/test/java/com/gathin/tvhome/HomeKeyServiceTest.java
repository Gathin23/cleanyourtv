package com.gathin.tvhome;

import org.junit.Test;
import java.util.Collections;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeKeyServiceTest {
    private static final long SOON = 45_000L;

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

    private static final String LAUNCHERX = "com.google.android.apps.tv.launcherx";
    private static final Set<String> STOCK_HOME = Collections.singleton(LAUNCHERX + ".home.HomeActivity");

    @Test public void theStockHomeIsRedirectedAtOnceByDefault() {
        assertEquals(HomeKeyService.Redirect.NOW,
                HomeKeyService.stockHomeRedirect(STOCK_HOME, LAUNCHERX, LAUNCHERX, LAUNCHERX + ".home.HomeActivity", 0));
    }

    @Test public void withADelayTheStockHomeIsRedirectedLater() {
        assertEquals(HomeKeyService.Redirect.LATER,
                HomeKeyService.stockHomeRedirect(STOCK_HOME, LAUNCHERX, LAUNCHERX, LAUNCHERX + ".home.HomeActivity", 200));
    }

    @Test public void aScreenTheStockLauncherOpensOverItsHomeCancelsTheRedirect() {
        assertEquals(HomeKeyService.Redirect.CANCEL,
                HomeKeyService.stockHomeRedirect(STOCK_HOME, LAUNCHERX, LAUNCHERX, LAUNCHERX + ".profile.chooser.ProfileChooserActivity", 200));
    }

    @Test public void withoutADelayOtherStockScreensMeanNothing() {
        assertEquals(HomeKeyService.Redirect.NONE,
                HomeKeyService.stockHomeRedirect(STOCK_HOME, LAUNCHERX, LAUNCHERX, LAUNCHERX + ".profile.chooser.ProfileChooserActivity", 0));
    }

    @Test public void otherAppsNeverTouchTheRedirect() {
        assertEquals(HomeKeyService.Redirect.NONE,
                HomeKeyService.stockHomeRedirect(STOCK_HOME, LAUNCHERX, "com.netflix.ninja", "com.netflix.ninja.MainActivity", 200));
        assertEquals(HomeKeyService.Redirect.NONE,
                HomeKeyService.stockHomeRedirect(STOCK_HOME, LAUNCHERX, LAUNCHERX, null, 200));
    }

    @Test public void redirectOffMeansNothing() {
        assertEquals(HomeKeyService.Redirect.NONE,
                HomeKeyService.stockHomeRedirect(Collections.emptySet(), "", LAUNCHERX, LAUNCHERX + ".home.HomeActivity", 200));
    }

    @Test public void alwaysGoesHomeAfterScreenOnUntilAKey() {
        assertTrue(HomeKeyService.stillArmed(SOON, SOON + HomeKeyService.SCREEN_ON_WINDOW_MS));
        assertFalse(HomeKeyService.stillArmed(SOON, 0));
    }
}
