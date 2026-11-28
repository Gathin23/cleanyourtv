package com.gathin.photodream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The pure parts of the sharp option. */
public class MediaSharpTest {

    @Test public void coverTakesTheCentreInTheShapeOfTheTarget() {
        assertArrayEquals(new int[]{0, 120, 3840, 2280}, Media.cover(3840, 2400, 3840, 2160));    // 3:2 loses top and bottom
        assertArrayEquals(new int[]{0, 0, 3840, 2160}, Media.cover(3840, 2160, 3840, 2160));
        assertArrayEquals(new int[]{1333, 0, 6666, 3000}, Media.cover(8000, 3000, 3840, 2160));   // a panorama loses its sides
    }

    @Test public void regionSampleHalvesWhileTheTargetStaysCoveredAndHoldsToThePixelLimit() {
        assertEquals(1, Media.regionSample(3840, 2160, 3840, 2160, 40_000_000L));
        assertEquals(2, Media.regionSample(7680, 4320, 3840, 2160, 40_000_000L));
        assertEquals(1, Media.regionSample(6000, 3375, 3840, 2160, 40_000_000L));   // a 24 MP camera original, whole
        assertEquals(2, Media.regionSample(7600, 4275, 3840, 2160, 20_000_000L));   // over the limit: halved
        assertEquals(1, Media.regionSample(1920, 1080, 3840, 2160, 40_000_000L));   // smaller than the target
    }
}
