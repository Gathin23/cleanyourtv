package io.github.pbanddev.tatami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LensTest {
    private static final Look.Post POST = Look.YOJOHAN.post;
    private static final int W = 1920, H = 1080;

    /** The lens formulas, written out in Java: vignette times colour ramp for channel c at p (half heights, y up). */
    private static double gain(double px, double py, int c) {
        double k = smoothstep(0.206, 1.64, Math.hypot((px + 0.071) * 0.574, py - 0.054));
        double[] ramp = {0.045 * px, 0.0, -0.05 * px + 0.03 * py};
        return (1 - 0.42 * k * k * k) * Math.exp(ramp[c]);
    }

    private static double blur(double px, double py) {
        return smoothstep(0.837, 1.157, Math.hypot((px - 0.030) * 0.611, py));
    }

    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    @Test
    public void everyTexelHoldsHalfTheGainAndTheBlurMaskAtItsCentre() {
        int mw = Lens.width(W), mh = Lens.height(H);
        byte[] map = Lens.map(W, H, POST);
        assertEquals(4 * mw * mh, map.length);
        for (int j = 0; j < mh; j += 7) {
            for (int i = 0; i < mw; i += 5) {
                // texel centre in screen pixels, y up (row 0 is the bottom, as GL reads it)
                double x = (i + 0.5) * W / mw, y = (j + 0.5) * H / mh;
                double px = (x - W / 2.0) / (H / 2.0), py = (y - H / 2.0) / (H / 2.0);
                int at = 4 * (j * mw + i);
                for (int c = 0; c < 3; c++) {
                    assertEquals("texel " + i + "," + j + " channel " + c, gain(px, py, c) / 2, (map[at + c] & 0xFF) / 255.0, 0.6 / 255);
                }
                assertEquals("texel " + i + "," + j + " blur", blur(px, py), (map[at + 3] & 0xFF) / 255.0, 0.6 / 255);
            }
        }
    }

    @Test
    public void theCornersDarkenAndTheMiddleStaysSharp() {
        int mw = Lens.width(W), mh = Lens.height(H);
        byte[] map = Lens.map(W, H, POST);
        int corner = 0, middle = 4 * ((mh / 2) * mw + mw / 2);
        double cornerGain = 2 * (map[corner + 1] & 0xFF) / 255.0;
        assertTrue("corner gain " + cornerGain, cornerGain > 0.55 && cornerGain < 0.8);
        assertEquals(0, map[middle + 3] & 0xFF);
        assertEquals(255, map[corner + 3] & 0xFF);
    }

    @Test
    public void theMarginCoversHowFarTheBlurReachesAndKeepsTheSmallCopyWhole() {
        int m = Lens.margin(POST), d = POST.blurDivisor;
        float farthest = 0;
        for (float o : POST.blurOffsets) farthest = Math.max(farthest, o);
        // the 4x4 box reaches 2 px, each blur tap 1 small texel past its offset (bilinear)
        assertTrue("margin " + m, m >= 2 + d * (farthest + 1));
        assertEquals("a whole number of small texels", 0, m % d);
    }

    @Test
    public void thePaddedMarginKeepsTheWiderCopyInWholeGpuTilesWhenItCan() {
        int m = Lens.margin(POST), d = POST.blurDivisor;
        assertEquals(24, Lens.padded(1920, m, d));   // 1968 = 123 tiles of 16
        assertEquals(20, Lens.padded(1080, m, d));   // 1120 = 70 tiles
        assertEquals(m, Lens.padded(1279, m, d));    // an odd size never fits whole tiles: keep the least
        for (int size : new int[] {720, 1280, 1920, 1080, 2160, 3840, 1279}) {
            int p = Lens.padded(size, m, d);
            assertTrue(p >= m && p % d == 0);
        }
    }
}
