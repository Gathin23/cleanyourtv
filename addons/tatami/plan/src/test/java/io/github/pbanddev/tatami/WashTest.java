package io.github.pbanddev.tatami;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WashTest {

    /** The W3C soft light, written out as the spec gives it. */
    private static double w3c(double cb, double cs) {
        if (cs <= 0.5) return cb - (1 - 2 * cs) * cb * (1 - cb);
        double d = cb <= 0.25 ? ((16 * cb - 12) * cb + 4) * cb : Math.sqrt(cb);
        return cb + (2 * cs - 1) * (d - cb);
    }

    @Test
    public void softLightIsTheW3cFormula() {
        for (int i = 0; i <= 20; i++) {
            for (int j = 0; j <= 20; j++) {
                float a = i / 20f, b = j / 20f;
                assertEquals("a=" + a + " b=" + b, w3c(a, b), Wash.softLight(a, b), 1e-6);
            }
        }
        assertEquals("black stays black", 0f, Wash.softLight(0f, 0.9f), 0f);
    }

    @Test
    public void noneChangesNothing() {
        float[] c = {0.1f, 0.5f, 0.93f};
        Wash.NONE.apply(123, 456, 1920, 1080, c);
        assertArrayEquals(new float[] {0.1f, 0.5f, 0.93f}, c, 1e-6f);
    }

    @Test
    public void nodesSitOnAGridFromTheTopLeftAndHoldPastTheEdges() {
        float[] gain = new float[Wash.NX * Wash.NY * 3], blend = new float[gain.length];
        java.util.Arrays.fill(gain, 1f);
        java.util.Arrays.fill(blend, 0.5f);
        gain[(2 * Wash.NX + 4) * 3] = 0.5f;   // node (4, 2), red: 4/16 across, 2/9 down
        Wash wash = new Wash(gain, blend);
        float[] c = {0.8f, 0.8f, 0.8f};
        wash.apply(1920 * 4 / 16f, 1080 * 2 / 9f, 1920, 1080, c);
        assertArrayEquals(new float[] {0.4f, 0.8f, 0.8f}, c, 1e-5f);
        c = new float[] {0.8f, 0.8f, 0.8f};
        wash.apply(1920 * 4.5f / 16, 1080 * 2 / 9f, 1920, 1080, c);
        assertEquals("halfway to the next node", 0.6f, c[0], 1e-5f);
        gain[0] = 0.25f;
        Wash corner = new Wash(gain, blend);
        c = new float[] {0.8f, 0.8f, 0.8f};
        corner.apply(-50, -50, 1920, 1080, c);
        assertEquals("past the top left: the corner node", 0.2f, c[0], 1e-5f);
    }

    /** Upside down or mirrored, the maps would put the blue at the bottom and the dark at the top. */
    @Test
    public void nagayaTurnsMidGreyBlueAtTheTopLeftAndDarkAtTheBottomMiddle() {
        assertGrey(100, 80, 0x10, 0x85, 0xD0);
        assertGrey(960, 1020, 0x53, 0x67, 0x91);
        assertGrey(960, 540, 0x80, 0x80, 0x80);
    }

    private static void assertGrey(float x, float y, int r, int g, int b) {
        float[] c = {128 / 255f, 128 / 255f, 128 / 255f};
        Wash.NAGAYA.apply(x, y, 1920, 1080, c);
        assertArrayEquals("grey at " + x + ", " + y, new float[] {r / 255f, g / 255f, b / 255f}, c, 0.6f / 255);
    }

    @Test
    public void nagayaWallsKeepTheirInkWhereTheRoomsAreWashed() {
        Scene s = new Scene(Look.NAGAYA);
        s.build(1_793_500_000.0, 1920, 1080, false, false, false, true);   // the camera at rest, so walls reach the corner
        assertFalse(s.mesh.clipped);
        float[] d = s.mesh.data, m = s.matrix;
        int ink = Look.NAGAYA.walls.color, walls = s.mesh.floats - s.frame.wallCount * 6 * SceneMesh.FLOATS_PER_VERTEX;
        int wallsInCorner = 0, fillsInCorner = 0;
        for (int k = 0; k < s.mesh.floats; k += SceneMesh.FLOATS_PER_VERTEX) {
            float x = (m[0] * d[k] + m[4] * d[k + 1] + m[12] + 1) / 2 * 1920;
            float y = (1 - (m[1] * d[k] + m[5] * d[k + 1] + m[13])) / 2 * 1080;
            boolean corner = x >= 0 && x < 240 && y >= 0 && y < 240;   // where red's W is under 0.45
            if (k >= walls) {
                assertEquals(((ink >> 16) & 0xFF) / 255f, d[k + 2], 0f);
                assertEquals(((ink >> 8) & 0xFF) / 255f, d[k + 3], 0f);
                assertEquals((ink & 0xFF) / 255f, d[k + 4], 0f);
                if (corner) wallsInCorner++;
            } else if (corner) {
                fillsInCorner++;
                assertFalse("a fill in the corner keeps its paint", isPaint(d[k + 2], d[k + 3], d[k + 4]));
            }
        }
        assertTrue(wallsInCorner > 0);
        assertTrue(fillsInCorner > 0);
    }

    private static boolean isPaint(float r, float g, float b) {
        float near = 0.5f / 255;
        for (int p : Look.NAGAYA.palette.deck) {
            if (Math.abs(((p >> 16) & 0xFF) / 255f - r) < near && Math.abs(((p >> 8) & 0xFF) / 255f - g) < near
                    && Math.abs((p & 0xFF) / 255f - b) < near) return true;
        }
        return false;
    }
}
