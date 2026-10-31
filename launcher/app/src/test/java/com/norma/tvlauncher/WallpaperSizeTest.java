package com.norma.tvlauncher;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WallpaperSizeTest {
    @Test public void veryWidePictureIsRefused() {
        assertFalse(WallpaperSize.accepted(65535, 2159));
    }

    @Test public void fourKPictureIsAccepted() {
        assertTrue(WallpaperSize.accepted(3840, 2160));
        assertTrue(WallpaperSize.accepted(1920, 1080));
    }

    @Test public void unreadablePictureIsRefused() {
        assertFalse(WallpaperSize.accepted(-1, -1));
        assertFalse(WallpaperSize.accepted(0, 1080));
    }

    @Test public void fourKOn1080pSamplesByTwo() {
        assertEquals(2, WallpaperSize.sampleSize(3840, 2160, 1920, 1080));
        assertNull(WallpaperSize.coverScale(1920, 1080, 1920, 1080));
    }

    @Test public void photoBetweenSampleSizesIsScaledToTheScreen() {
        // 3000x2000 keeps sample size 1; the cover is limited by the width, so it decodes 1920 wide.
        assertEquals(1, WallpaperSize.sampleSize(3000, 2000, 1920, 1080));
        assertArrayEquals(new int[]{3000, 1920}, WallpaperSize.coverScale(3000, 2000, 1920, 1080));
    }

    @Test public void widePictureIsScaledByItsHeight() {
        assertArrayEquals(new int[]{2000, 1080}, WallpaperSize.coverScale(4000, 2000, 1920, 1080));
    }

    @Test public void smallPictureIsNotScaled() {
        assertEquals(1, WallpaperSize.sampleSize(1280, 720, 1920, 1080));
        assertNull(WallpaperSize.coverScale(1280, 720, 1920, 1080));
    }

    @Test
    public void aPhoneSizedPhotoIsAcceptedOnceSampledDown() {
        int s = WallpaperSize.sampleSize(4032, 3024, 1920, 1080);
        org.junit.Assert.assertTrue(WallpaperSize.accepted(4032 / s, 3024 / s));
        int t = WallpaperSize.sampleSize(65535, 2159, 1920, 1080);
        org.junit.Assert.assertFalse(WallpaperSize.accepted(65535 / t, 2159 / t));
    }
}
