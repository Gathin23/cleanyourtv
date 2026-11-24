package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
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

    @Test public void stockHomeLookupCoversVendorHomeCategories() {
        // Coocaa OS declares its home under HOME.CC / HOME.CC_MAIN and never under CATEGORY_HOME (#1351).
        assertEquals(3, HomeKeyService.STOCK_HOME_CATEGORIES.length);
        assertEquals("android.intent.category.HOME", HomeKeyService.STOCK_HOME_CATEGORIES[0]);
        assertEquals("android.intent.category.HOME.CC", HomeKeyService.STOCK_HOME_CATEGORIES[1]);
        assertEquals("android.intent.category.HOME.CC_MAIN", HomeKeyService.STOCK_HOME_CATEGORIES[2]);
    }

    @Test public void bootHomeComesFromTheHomepagePackageNotTheClassPrefix() {
        // #1351: the boot activity com.coocaa.homepage.vast.HomePageActivity does not start with the
        // stock package com.tianci.movieplatform, yet persist.service.homepage.pkg names that package.
        assertEquals("com.coocaa.homepage.vast.HomePageActivity",
                HomeKeyService.bootHomeActivity("com.tianci.movieplatform",
                        "com.coocaa.homepage.vast.HomePageActivity", "com.tianci.movieplatform"));
    }

    @Test public void aBootHomeForAnotherPackageIsLeftOut() {
        assertEquals("", HomeKeyService.bootHomeActivity("com.other.stuff",
                "com.coocaa.homepage.vast.HomePageActivity", "com.tianci.movieplatform"));
    }

    @Test public void noHomepagePairNamesNoBootHome() {
        assertEquals("", HomeKeyService.bootHomeActivity("", "com.coocaa.homepage.vast.HomePageActivity",
                "com.tianci.movieplatform"));
        assertEquals("", HomeKeyService.bootHomeActivity("com.tianci.movieplatform", "",
                "com.tianci.movieplatform"));
        assertEquals("", HomeKeyService.bootHomeActivity(null, null, "com.tianci.movieplatform"));
    }
}
