package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    @Test public void stockHomeLookupCoversVendorHomeCategories() {
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

    @Test public void bootHomeIsIgnoredForAnotherPackage() {
        assertNull(HomeKeyService.bootHomeActivity("com.other.home",
                "com.coocaa.homepage.vast.HomePageActivity", "com.tianci.movieplatform"));
        assertNull(HomeKeyService.bootHomeActivity("com.tianci.movieplatform", "", "com.tianci.movieplatform"));
        assertNull(HomeKeyService.bootHomeActivity("", "com.coocaa.homepage.vast.HomePageActivity",
                "com.tianci.movieplatform"));
        assertNull(HomeKeyService.bootHomeActivity(null, "com.coocaa.homepage.vast.HomePageActivity",
                "com.tianci.movieplatform"));
    }
}
