package io.github.pbanddev.tatami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ViewTest {

    /** Screen px of a plan point through a matrix. */
    private static float[] screen(float[] m, float x, float y, float w, float h) {
        float cx = m[0] * x + m[4] * y + m[12], cy = m[1] * x + m[5] * y + m[13];
        return new float[] {(cx + 1) / 2 * w, (1 - cy) / 2 * h};
    }

    @Test
    public void matrixPutsTheCentreMidScreenAndTurnsAndScales() {
        View v = new View();
        v.set(1920, 1080, 30f, 0.8f, 500.0, -200.0, 0f);
        float[] m = new float[16];
        v.matrix(m);
        float[] mid = screen(m, 500, -200, 1920, 1080);
        assertEquals(960, mid[0], 1e-2);
        assertEquals(540, mid[1], 1e-2);
        // one plan unit along x: 0.8 px, turned 30 degrees clockwise on screen (y down)
        float[] step = screen(m, 600, -200, 1920, 1080);
        assertEquals(80 * Math.cos(Math.toRadians(30)), step[0] - mid[0], 1e-2);
        assertEquals(80 * Math.sin(Math.toRadians(30)), step[1] - mid[1], 1e-2);
    }

    @Test
    public void yojohanViewAgreesWithItsCameraMatrix() {
        Scene s = new Scene(Look.YOJOHAN);
        float[] m = new float[16];
        for (double t : new double[] {0, 37.5, 1_793_500_321.7}) {
            s.build(t, 1920, 1080, false, false, true);
            s.view.matrix(m);
            for (int k = 0; k < 16; k++) assertEquals("t=" + t + " k=" + k, s.matrix[k], m[k], 1e-5);
        }
    }

    @Test
    public void seesBoxesOnTheTurnedScreenAndNotPastIt() {
        View v = new View();
        v.set(1600, 900, 45f, 1f, 0.0, 0.0, 0f);
        assertTrue(v.sees(-10, -10, 10, 10, 0));
        // the screen's corner reaches sqrt(800^2 + 450^2) = 918 px out; turned 45 degrees it points along a diagonal
        assertFalse("past the long side", v.sees(700, -700, 720, -680, 0));
        assertTrue("grown into view", v.sees(700, -700, 720, -680, 400));
        // along plan x the turned screen ends where its short side does: 450 * sqrt(2) = 636 px
        assertTrue(v.sees(630, -1, 632, 1, 0));
        assertFalse(v.sees(640, -1, 642, 1, 0));
        assertTrue("the margin reaches further", withMargin(v, 20).sees(640, -1, 642, 1, 0));
    }

    @Test
    public void zoomAndCentreMoveWhatIsSeen() {
        View v = new View();
        v.set(1000, 1000, 0f, 0.5f, 3000.0, 0.0, 0f);
        assertTrue("1000 px wide at zoom 0.5 shows 2000 plan units", v.sees(3990, 0, 3999, 1, 0));
        assertFalse(v.sees(4001, 0, 4010, 1, 0));
        assertFalse("nothing near 0", v.sees(-10, -10, 10, 10, 0));
    }

    private static View withMargin(View v, float margin) {
        View m = new View();
        m.set(v.w, v.h, v.rollDeg, v.zoom, v.centreX, v.centreY, margin);
        return m;
    }
}
