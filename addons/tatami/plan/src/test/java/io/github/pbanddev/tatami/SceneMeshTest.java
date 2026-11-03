package io.github.pbanddev.tatami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class SceneMeshTest {

    @Test
    public void meshNeverOverflowsAndHoldsWholeTriangles() {
        Scene scene = new Scene(Look.YOJOHAN);
        int most = 0;
        for (int i = 0; i < 200; i++) {
            double t = i * 17.7 + (i % 2 == 0 ? 0 : 1_793_500_000.0);
            scene.build(t, i % 3 == 0 ? 1280 : 1920, i % 3 == 0 ? 720 : 1080, true, true, true);
            assertTrue(scene.mesh.floats <= scene.mesh.data.length);
            assertEquals(0, scene.mesh.floats % (3 * SceneMesh.FLOATS_PER_VERTEX));
            most = Math.max(most, scene.mesh.floats);
        }
        assertTrue("mesh is empty", most > 0);
    }

    @Test
    public void doorsAndFixturesCanBeLeftOut() {
        Scene scene = new Scene(Look.YOJOHAN);
        scene.build(0, 1920, 1080, false, false, true);
        int without = scene.mesh.floats;
        scene.build(0, 1920, 1080, true, false, true);
        int doors = scene.mesh.floats;
        scene.build(0, 1920, 1080, true, true, true);
        assertTrue(doors > without);
        assertTrue(scene.mesh.floats > doors);
    }

    // ------------------------------------------------------------------------------------- Nagaya

    private static final int F = SceneMesh.FLOATS_PER_VERTEX;

    /** Lays out and meshes Nagaya through the given view, past its camera. */
    private static void nagaya(Scene s, View v, Wash wash) {
        s.view.set(v.w, v.h, v.rollDeg, v.zoom, v.centreX, v.centreY, v.margin);
        s.view.matrix(s.matrix);
        s.fill(1_793_500_000.0, true, true, wash);
    }

    @Test
    public void nagayaNeverClipsOrOverflowsInItsWidestViews() {
        Scene s = new Scene(Look.NAGAYA);
        Look.Plan p = Look.NAGAYA.plan;
        View v = new View();
        for (int[] size : new int[][] {{1280, 720}, {1920, 1080}, {3840, 2160}}) {
            float unit = size[1] * p.unitH, tw = p.tileWUnits * unit, th = p.tileHUnits * unit;
            for (float roll : new float[] {-24f, -16f}) {
                for (int i = 0; i < 6; i++) {
                    for (int j = 0; j < 6; j++) {
                        double cx = tw * i / 6.0 + tw / 2 * j / 6.0, cy = th * j / 6.0;
                        v.set(size[0], size[1], roll, 0.78f, cx, cy, s.view.margin);
                        nagaya(s, v, Wash.NONE);
                        assertTrue(s.frame.cellCount > 0);
                        assertFalse("frame clipped", s.frame.clipped);
                        assertFalse("mesh clipped", s.mesh.clipped);
                        assertTrue(s.mesh.floats <= s.mesh.data.length);
                        assertEquals(0, s.mesh.floats % (3 * F));
                    }
                }
            }
        }
    }

    @Test
    public void nagayaWashesFillsButNeverWalls() {
        Scene s = new Scene(Look.NAGAYA);
        float[] gain = new float[Wash.NX * Wash.NY * 3], blend = new float[gain.length];
        Arrays.fill(gain, 0.5f);
        Arrays.fill(blend, 0.5f);
        View v = new View();
        v.set(1920, 1080, -20f, 0.89f, 0.0, 0.0, 0f);
        nagaya(s, v, new Wash(gain, blend));
        float[] ink = rgb(Look.NAGAYA.walls.color);
        int walls = 0, fills = 0;
        for (int k = 0; k < s.mesh.floats; k += F) {
            float[] c = Arrays.copyOfRange(s.mesh.data, k + 2, k + 5);
            if (Arrays.equals(c, ink)) {
                walls++;
                continue;
            }
            fills++;
            assertTrue("a fill at half a paint: " + Arrays.toString(c), isHalfAPaint(c));
        }
        assertTrue(walls > 0 && fills > 0);
    }

    @Test
    public void nagayaCutsWashedFillsIntoSmallPieces() {
        Scene s = new Scene(Look.NAGAYA);
        View v = new View();
        v.set(1920, 1080, -20f, 0.89f, 0.0, 0.0, 0f);
        nagaya(s, v, Wash.NONE);
        float side = 1080 * Look.NAGAYA.post.washPieceH + 1e-2f;
        float[] ink = rgb(Look.NAGAYA.walls.color), d = s.mesh.data;
        int pieces = 0;
        for (int k = 0; k < s.mesh.floats; k += 3 * F) {
            if (Arrays.equals(Arrays.copyOfRange(d, k + 2, k + 5), ink)) continue;
            float w = Math.max(d[k], Math.max(d[k + F], d[k + 2 * F])) - Math.min(d[k], Math.min(d[k + F], d[k + 2 * F]));
            float h = Math.max(d[k + 1], Math.max(d[k + 1 + F], d[k + 1 + 2 * F]))
                    - Math.min(d[k + 1], Math.min(d[k + 1 + F], d[k + 1 + 2 * F]));
            assertTrue("piece " + w + " x " + h, w <= side && h <= side);
            pieces++;
        }
        assertTrue(pieces > 2 * s.frame.cellCount);
        nagaya(s, v, null);
        assertTrue("unwashed fills stay whole", s.mesh.floats < pieces * 3 * F);
    }

    private static float[] rgb(int argb) {
        return new float[] {((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
    }

    /** True for half of a room paint or of the door's: doors are fills, washed too. */
    private static boolean isHalfAPaint(float[] c) {
        int[] deck = Look.NAGAYA.palette.deck, paints = Arrays.copyOf(deck, deck.length + 1);
        paints[deck.length] = Look.NAGAYA.palette.door;
        for (int paint : paints) {
            float[] p = rgb(paint);
            if (Math.abs(c[0] - p[0] / 2) < 1e-6 && Math.abs(c[1] - p[1] / 2) < 1e-6 && Math.abs(c[2] - p[2] / 2) < 1e-6) {
                return true;
            }
        }
        return false;
    }
}
