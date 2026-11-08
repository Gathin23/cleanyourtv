package io.github.nottoobright.koipond;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimeOfDayTest {

    private static float hm(int h, int m) {
        return h * 60 + m;
    }

    @Test
    public void anchorsGiveThePureKeyPalette() {
        assertEquals(TimeOfDay.palette(TimeOfDay.NIGHT), TimeOfDay.at(hm(4, 30)));
        assertEquals(TimeOfDay.palette(TimeOfDay.DAWN), TimeOfDay.at(hm(6, 0)));
        assertEquals(TimeOfDay.palette(TimeOfDay.DAY), TimeOfDay.at(hm(8, 0)));
        assertEquals(TimeOfDay.palette(TimeOfDay.DAY), TimeOfDay.at(hm(12, 0)));
        assertEquals(TimeOfDay.palette(TimeOfDay.DAY), TimeOfDay.at(hm(16, 0)));
        assertEquals(TimeOfDay.palette(TimeOfDay.GOLDEN), TimeOfDay.at(hm(17, 30)));
        assertEquals(TimeOfDay.palette(TimeOfDay.DUSK), TimeOfDay.at(hm(19, 0)));
        assertEquals(TimeOfDay.palette(TimeOfDay.NIGHT), TimeOfDay.at(hm(20, 30)));
        assertEquals(TimeOfDay.palette(TimeOfDay.NIGHT), TimeOfDay.at(0f));
        assertEquals(TimeOfDay.palette(TimeOfDay.NIGHT), TimeOfDay.at(hm(2, 0)));
    }

    @Test
    public void betweenAnchorsIsABlend() {
        Palette p = TimeOfDay.at(hm(18, 15));
        assertNotEquals(TimeOfDay.palette(TimeOfDay.GOLDEN), p);
        assertNotEquals(TimeOfDay.palette(TimeOfDay.DUSK), p);
    }

    @Test
    public void dominantKey() {
        assertEquals(TimeOfDay.DAWN, TimeOfDay.key(hm(6, 0)));
        assertEquals(TimeOfDay.DAY, TimeOfDay.key(hm(12, 0)));
        assertEquals(TimeOfDay.GOLDEN, TimeOfDay.key(hm(17, 30)));
        assertEquals(TimeOfDay.DUSK, TimeOfDay.key(hm(19, 0)));
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.key(hm(23, 0)));
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.key(hm(5, 0)));
        assertEquals(TimeOfDay.DAWN, TimeOfDay.key(hm(5, 30)));
        assertEquals(TimeOfDay.DAY, TimeOfDay.key(hm(7, 30)));
    }

    @Test
    public void blendingIsContinuousAllDayIncludingMidnight() {
        int maxStep = 4;
        for (int m = 0; m < TimeOfDay.MINUTES; m++) {
            int[] a = TimeOfDay.at(m).colors();
            int[] b = TimeOfDay.at(m + 1).colors();
            for (int i = 0; i < a.length; i++) {
                for (int shift = 0; shift < 32; shift += 8) {
                    int d = Math.abs(((a[i] >>> shift) & 0xFF) - ((b[i] >>> shift) & 0xFF));
                    assertTrue("jump of " + d + " at minute " + m + ", colour " + i, d <= maxStep);
                }
            }
        }
    }

    @Test
    public void palettesAreOpaque() {
        for (int k = TimeOfDay.DAWN; k <= TimeOfDay.NIGHT; k++) {
            for (int c : TimeOfDay.palette(k).colors()) assertEquals(0xFF, c >>> 24);
        }
        for (int m = 0; m < TimeOfDay.MINUTES; m += 7) {
            for (int c : TimeOfDay.at(m).colors()) assertEquals(0xFF, c >>> 24);
        }
    }

    @Test
    public void nightIsDarkerThanDay() {
        assertTrue(luma(TimeOfDay.palette(TimeOfDay.NIGHT).ambient) < luma(TimeOfDay.palette(TimeOfDay.DUSK).ambient));
        assertTrue(luma(TimeOfDay.palette(TimeOfDay.DUSK).ambient) < luma(TimeOfDay.palette(TimeOfDay.DAY).ambient));
        assertTrue(luma(TimeOfDay.palette(TimeOfDay.NIGHT).caustic) < luma(TimeOfDay.palette(TimeOfDay.DAWN).caustic));
    }

    @Test
    public void fireflies() {
        assertEquals(0f, TimeOfDay.fireflies(hm(12, 0)), 0f);
        assertEquals(0f, TimeOfDay.fireflies(hm(6, 0)), 0f);
        assertEquals(0f, TimeOfDay.fireflies(hm(19, 0)), 0f);
        assertEquals(1f, TimeOfDay.fireflies(hm(20, 30)), 0f);
        assertEquals(1f, TimeOfDay.fireflies(hm(23, 0)), 0f);
        assertEquals(1f, TimeOfDay.fireflies(hm(2, 0)), 0f);
        assertEquals(1f, TimeOfDay.fireflies(0f), 0f);
        float f = TimeOfDay.fireflies(hm(19, 45));
        assertTrue(f > 0f && f < 1f);
        float dawn = TimeOfDay.fireflies(hm(5, 15));
        assertTrue(dawn > 0f && dawn < 1f);
    }

    @Test
    public void lerpEndpointsAndClamping() {
        int a = 0xFF102030, b = 0x80F0E0D0;
        assertEquals(a, TimeOfDay.lerp(a, b, 0f));
        assertEquals(b, TimeOfDay.lerp(a, b, 1f));
        assertEquals(a, TimeOfDay.lerp(a, b, -3f));
        assertEquals(b, TimeOfDay.lerp(a, b, 7f));
        int mid = TimeOfDay.lerp(0xFF000000, 0xFFFFFFFF, 0.5f);
        for (int shift = 0; shift < 24; shift += 8) {
            int c = (mid >>> shift) & 0xFF;
            assertTrue(c == 127 || c == 128);
        }
        assertEquals(0xFF, mid >>> 24);
    }

    @Test
    public void paletteLerpEndpoints() {
        Palette day = TimeOfDay.palette(TimeOfDay.DAY);
        Palette night = TimeOfDay.palette(TimeOfDay.NIGHT);
        assertEquals(day, Palette.lerp(day, night, 0f));
        assertEquals(night, Palette.lerp(day, night, 1f));
        assertEquals(day, Palette.lerp(day, night, -1f));
        assertEquals(night, Palette.lerp(day, night, 2f));
    }

    @Test
    public void minutesOutsideTheDayWrap() {
        assertEquals(TimeOfDay.at(hm(12, 0)), TimeOfDay.at(hm(12, 0) + TimeOfDay.MINUTES));
        assertEquals(TimeOfDay.at(hm(18, 15)), TimeOfDay.at(hm(18, 15) - TimeOfDay.MINUTES));
        assertEquals(TimeOfDay.at(hm(23, 0)), TimeOfDay.at(-60f));
        assertEquals(TimeOfDay.palette(TimeOfDay.NIGHT), TimeOfDay.at(1440f));
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.key(-60f));
        assertEquals(TimeOfDay.DAY, TimeOfDay.key(hm(12, 0) + 3 * TimeOfDay.MINUTES));
        assertEquals(1f, TimeOfDay.fireflies(hm(2, 0) + TimeOfDay.MINUTES), 0f);
        assertEquals(0f, TimeOfDay.fireflies(hm(12, 0) - TimeOfDay.MINUTES), 0f);
        assertEquals(TimeOfDay.fireflies(hm(19, 45)), TimeOfDay.fireflies(hm(19, 45) - 2 * TimeOfDay.MINUTES), 1e-4f);
    }

    private static int luma(int c) {
        return ((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF);
    }
}
