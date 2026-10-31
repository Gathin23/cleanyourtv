package com.norma.tvlauncher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BannerShapeTest {
    @Test
    public void wideBannersAreKept() {
        assertTrue(AppAdapter.isBannerShaped(320, 180));
        assertTrue(AppAdapter.isBannerShaped(1920, 1080));
    }

    @Test
    public void squareAndTallPicturesAreNotBanners() {
        assertFalse(AppAdapter.isBannerShaped(64, 64));
        assertFalse(AppAdapter.isBannerShaped(256, 256));
        assertFalse(AppAdapter.isBannerShaped(100, 160));
    }

    @Test
    public void drawablesWithoutAnIntrinsicSizeStillFillTheTile() {
        assertTrue(AppAdapter.isBannerShaped(-1, -1));
        assertTrue(AppAdapter.isBannerShaped(0, 0));
    }
}
