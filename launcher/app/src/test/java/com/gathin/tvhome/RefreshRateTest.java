package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RefreshRateTest {
    @Test
    public void picksTheFastestModeAtTheSameResolution() {
        int[] ids = {1, 2, 3};
        int[] w = {3840, 3840, 3840};
        int[] h = {2160, 2160, 2160};
        float[] r = {30f, 60f, 120f};
        assertEquals(3, RefreshRate.bestModeId(2, ids, w, h, r));
    }

    @Test
    public void neverSwitchesResolutionToGetMoreHertz() {
        int[] ids = {1, 2};
        int[] w = {3840, 1920};
        int[] h = {2160, 1080};
        float[] r = {60f, 120f};
        assertEquals(1, RefreshRate.bestModeId(1, ids, w, h, r));
    }

    @Test
    public void staysPutWhenNothingIsFaster() {
        int[] ids = {1, 2};
        int[] w = {1920, 1920};
        int[] h = {1080, 1080};
        float[] r = {60f, 50f};
        assertEquals(1, RefreshRate.bestModeId(1, ids, w, h, r));
    }

    @Test
    public void ignoresNearlyEqualRates() {
        int[] ids = {1, 2};
        int[] w = {1920, 1920};
        int[] h = {1080, 1080};
        float[] r = {59.94f, 60f};
        assertEquals(1, RefreshRate.bestModeId(1, ids, w, h, r));
    }

    @Test
    public void unknownCurrentModeIsLeftAlone() {
        int[] ids = {1, 2};
        int[] w = {1920, 1920};
        int[] h = {1080, 1080};
        float[] r = {60f, 120f};
        assertEquals(9, RefreshRate.bestModeId(9, ids, w, h, r));
    }
}
