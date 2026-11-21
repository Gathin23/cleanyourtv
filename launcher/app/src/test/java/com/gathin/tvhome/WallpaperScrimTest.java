package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperScrimTest {

    /** Busyness of n samples of the same luminance. */
    private static float flat(double l, int n) {
        return WallpaperScrim.busyness(l * n, l * l * n, n);
    }

    @Test
    public void darkCalmPictureGetsNoScrim() {
        assertEquals(0, WallpaperScrim.alpha(flat(0.10, 768)));
    }

    @Test
    public void brightPictureGetsTheStrongestScrim() {
        assertEquals(Math.round(0.72f * 255), WallpaperScrim.alpha(flat(0.95, 768)));
    }

    @Test
    public void busyDetailCountsLikeBrightness() {
        // Half black, half white: mean 0.5, spread 0.5, as hard to read over as a bright picture.
        float busy = WallpaperScrim.busyness(0.5 * 768, 0.5 * 768, 768);
        assertEquals(0.5f + 1.3f * 0.5f, busy, 1e-4f);
        assertEquals(Math.round(0.72f * 255), WallpaperScrim.alpha(busy));
    }

    @Test
    public void alphaGrowsWithBusyness() {
        int previous = -1;
        for (float busy = 0f; busy <= 1.2f; busy += 0.05f) {
            int a = WallpaperScrim.alpha(busy);
            assertTrue(a >= previous && a >= 0 && a <= 255);
            previous = a;
        }
    }

    @Test
    public void noSamplesMeansNoScrim() {
        assertEquals(0, WallpaperScrim.alpha(WallpaperScrim.busyness(0, 0, 0)));
    }
}
