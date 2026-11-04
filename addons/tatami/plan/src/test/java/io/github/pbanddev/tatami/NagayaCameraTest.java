package io.github.pbanddev.tatami;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class NagayaCameraTest {
    private static final Look LOOK = Look.NAGAYA;
    private static final Look.Camera CAM = LOOK.camera;
    private static final double FRAME = 1 / 60.0;
    private static final double[] STARTS = {0, 1_790_000_000.0, 1_793_500_000.0, 1_800_000_000.0};
    private static final int W = 1920, H = 1080;

    /** The lattice at 1080p in plan units: a1 = (a1x, 0), a2 = (a2x, a2y). */
    private static final float UNIT = H * LOOK.plan.unitH;
    private static final double A1X = LOOK.plan.tileWUnits * (double) UNIT;
    private static final double A2X = LOOK.plan.tileWUnits / 2 * (double) UNIT;
    private static final double A2Y = LOOK.plan.tileHUnits * (double) UNIT;

    /** Screen px of plan point (x, y) through a matrix. */
    private static double[] screen(float[] m, double x, double y) {
        double cx = m[0] * x + m[4] * y + m[12], cy = m[1] * x + m[5] * y + m[13];
        return new double[] {(cx + 1) / 2 * W, (1 - cy) / 2 * H};
    }

    /** Where the copy of one fixed plan point that lands nearest to screen point (sx, sy) lands, in px. */
    private static double[] nearestCopy(float[] m, double sx, double sy) {
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int j = -3; j <= 3; j++) {
            for (int i = -3; i <= 3; i++) {
                double[] p = screen(m, 0.37 * A1X + i * A1X + j * A2X, 0.61 * A2Y + j * A2Y);
                double d = Math.hypot(p[0] - sx, p[1] - sy);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return best;
    }

    @Test
    public void rollAndZoomSweepTheirWholeRangesAndNoFurther() {
        NagayaCamera c = new NagayaCamera(LOOK);
        View v = new View();
        float[] m = new float[16];
        for (double start : STARTS) {
            float rollMin = 0, rollMax = -90, zoomMin = 9, zoomMax = 0;
            for (int k = 0; k < 3000; k++) {
                double t = start + k * 0.1;
                c.at(t, W, H, true, v, m);
                assertTrue("t=" + t + " roll " + v.rollDeg, v.rollDeg >= -24f && v.rollDeg <= -16f);
                assertTrue("t=" + t + " zoom " + v.zoom, v.zoom >= 0.78f && v.zoom <= 1f);
                rollMin = Math.min(rollMin, v.rollDeg);
                rollMax = Math.max(rollMax, v.rollDeg);
                zoomMin = Math.min(zoomMin, v.zoom);
                zoomMax = Math.max(zoomMax, v.zoom);
            }
            assertEquals(-24f, rollMin, 0f);
            assertEquals(-16f, rollMax, 0f);
            assertEquals(0.78f, zoomMin, 0f);
            assertEquals(1f, zoomMax, 0f);
        }
    }

    @Test
    public void zoomStartsAndStopsGently() {
        NagayaCamera c = new NagayaCamera(LOOK);
        View v = new View();
        float[] m = new float[16];
        double peak = 0, last = 0, worstStart = 0;
        int starts = 0;
        float before = Float.NaN;
        for (double t = STARTS[2]; t < STARTS[2] + 300; t += FRAME) {
            c.at(t, W, H, true, v, m);
            double step = Float.isNaN(before) ? 0 : Math.abs(v.zoom - before);
            peak = Math.max(peak, step);
            if (last == 0 && step > 0 && !Float.isNaN(before)) {
                starts++;
                worstStart = Math.max(worstStart, step);
            }
            last = step;
            before = v.zoom;
        }
        assertTrue("zoom ramps seen: " + starts, starts >= 2);
        assertTrue("first step " + worstStart + " of a peak " + peak, worstStart < 0.05 * peak);
    }

    @Test
    public void framesASixtiethApartTurnAtMostHalfADegreeASecondAndNeverJumpAcrossLatticeWraps() {
        NagayaCamera c = new NagayaCamera(LOOK);
        // an eased ramp peaks at pi / 2 times the even rate
        double zoomRate = Math.PI / 2 * (CAM.zoomFrom - CAM.zoomTo) / CAM.zoomRampSeconds;
        // the middle and 2 corners: the roll and the zoom move the corners most
        double[][] targets = {{W / 2.0, H / 2.0}, {0, 0}, {W, H}};
        for (double start : new double[] {1_790_000_000.0, 1_800_000_000.0 - 600}) {
            View v0 = new View(), v1 = new View();
            float[] m0 = new float[16], m1 = new float[16];
            c.at(start, W, H, true, v0, m0);
            int wraps = 0;
            for (int k = 1; k <= 600 * 60; k++) {
                double t = start + k * FRAME;
                c.at(t, W, H, true, v1, m1);
                String at = "t=" + t;
                assertTrue(at + " roll " + v0.rollDeg + " to " + v1.rollDeg, Math.abs(v1.rollDeg - v0.rollDeg) <= 0.5 * FRAME);
                assertTrue(at + " zoom " + v0.zoom + " to " + v1.zoom, Math.abs(v1.zoom - v0.zoom) <= zoomRate * FRAME * 1.01);
                assertTrue(at + " centre " + v1.centreX + ", " + v1.centreY + " within a tile of 0",
                        Math.abs(v1.centreX) <= A1X + A2X && Math.abs(v1.centreY) <= A2Y);
                if (Math.abs(v1.centreX - v0.centreX) > A2X / 2 || Math.abs(v1.centreY - v0.centreY) > A2Y / 2) wraps++;
                for (double[] target : targets) {
                    double[] s0 = nearestCopy(m0, target[0], target[1]), s1 = nearestCopy(m1, s0[0], s0[1]);
                    double moved = Math.hypot(s1[0] - s0[0], s1[1] - s0[1]);
                    assertTrue(at + " a plan point near " + target[0] + ", " + target[1] + " moved " + moved + " px", moved <= 2);
                }
                View v = v0;
                v0 = v1;
                v1 = v;
                float[] m = m0;
                m0 = m1;
                m1 = m;
            }
            assertTrue("only " + wraps + " lattice wraps in 10 minutes", wraps >= 5);
        }
    }

    @Test
    public void thePlanDriftsDownTheScreenAndALittleLeft() {
        NagayaCamera c = new NagayaCamera(LOOK);
        float[] m0 = new float[16], m1 = new float[16];
        View v = new View();
        // the plan under the screen's middle, step by step, over 20 minutes
        double start = 1_793_500_000.0, seconds = 1200, step = 0.1, dx = 0, dy = 0;
        c.at(start, W, H, true, v, m0);
        for (int k = 1; k <= seconds / step; k++) {
            c.at(start + k * step, W, H, true, v, m1);
            double[] s0 = nearestCopy(m0, W / 2.0, H / 2.0), s1 = nearestCopy(m1, s0[0], s0[1]);
            dx += s1[0] - s0[0];
            dy += s1[1] - s0[1];
            float[] m = m0;
            m0 = m1;
            m1 = m;
        }
        double heading = Math.toDegrees(Math.atan2(dy, dx)), speedH = Math.hypot(dx, dy) / seconds / H;
        assertEquals(111.6, heading, 3);
        assertTrue("speed " + speedH + " H/s", speedH >= 0.78 * 0.04 && speedH <= 0.04);
    }

    @Test
    public void theSameTimeGivesTheSameViewWhateverCameBefore() {
        // as Home and the screensaver: 2 cameras, one with its own history
        NagayaCamera a = new NagayaCamera(LOOK), b = new NagayaCamera(LOOK);
        View va = new View(), vb = new View();
        float[] ma = new float[16], mb = new float[16];
        Random random = new Random(3);
        for (int i = 0; i < 2000; i++) {
            double t = 1_790_000_000.0 + random.nextDouble() * 1e7;
            a.at(t - random.nextDouble() * 1000, W, H, true, va, ma);
            a.at(t, W, H, true, va, ma);
            b.at(t, W, H, true, vb, mb);
            assertEquals(vb.rollDeg, va.rollDeg, 0f);
            assertEquals(vb.zoom, va.zoom, 0f);
            assertEquals(vb.centreX, va.centreX, 0);
            assertEquals(vb.centreY, va.centreY, 0);
            assertArrayEquals(mb, ma, 0f);
        }
    }

    @Test
    public void cameraOffHoldsTheRestViewAndDoesNotDrift() {
        NagayaCamera c = new NagayaCamera(LOOK);
        View v = new View();
        float[] m = new float[16], first = null;
        for (double t : new double[] {0, 61.3, 1_793_500_000.0, 1_800_000_000.0}) {
            c.at(t, W, H, false, v, m);
            assertEquals(-20f, v.rollDeg, 0f);
            assertEquals(0.89f, v.zoom, 0f);
            assertEquals(Scene.margin(LOOK), v.margin, 0f);
            if (first == null) first = m.clone();
            assertArrayEquals("t=" + t, first, m, 0f);
        }
    }

    @Test
    public void aNagayaSceneOverAFewMinutesNeverLeavesAnythingOut() {
        Scene s = new Scene(LOOK);
        double cycle = 2 * (CAM.rollHoldSeconds + CAM.rollRampSeconds);
        for (int[] size : new int[][] {{1280, 720}, {1920, 1080}, {3840, 2160}}) {
            for (double t = 1_800_000_000.0; t < 1_800_000_000.0 + 2 * cycle; t += 1.0) {
                s.build(t, size[0], size[1], true, true, true);
                String at = size[0] + "x" + size[1] + " t=" + t + " roll " + s.view.rollDeg + " zoom " + s.view.zoom;
                assertTrue(at + ": no cells", s.frame.cellCount > 0);
                assertFalse(at + ": frame clipped", s.frame.clipped);
                assertFalse(at + ": mesh clipped", s.mesh.clipped);
            }
        }
    }
}
