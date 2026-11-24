package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** When a HOME intent rewinds Home to the first tile instead of returning to the app's tile. */
public class MainActivityTest {

    @Test
    public void aHomePressOnHomeRewinds() {
        assertTrue(MainActivity.rewindsToFirstTile(false, false, false));
    }

    @Test
    public void theRedirectTagsAHomePressOnHomeEvenAfterTheStockHomeStoppedThisOne() {
        assertTrue(MainActivity.rewindsToFirstTile(true, true, false));
    }

    @Test
    public void theReturnFromAnAppKeepsItsTile() {
        assertFalse(MainActivity.rewindsToFirstTile(true, false, false));
    }

    @Test
    public void aReturnFromAnAppWithItsPackageKeepsItsTile() {
        assertFalse(MainActivity.rewindsToFirstTile(true, false, true));
    }

    @Test
    public void aDuplicateHomeIntentWithTheAppPackageDoesNotRewind() {
        assertFalse(MainActivity.rewindsToFirstTile(false, false, true));
    }

    @Test
    public void aTaggedPressRewindsEvenIfHomeWasStillResumed() {
        assertTrue(MainActivity.rewindsToFirstTile(false, true, false));
    }
}
