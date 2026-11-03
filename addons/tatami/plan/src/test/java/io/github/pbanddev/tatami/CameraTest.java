package io.github.pbanddev.tatami;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CameraTest {
    private static final float ANGLE = Look.YOJOHAN.camera.angleDeg;

    /** Maps a screen point into plan space the way the renderer's canvas transform does, in reverse. */
    private static float[] toPlan(float sx, float sy, double t, float w, float h, boolean moving) {
        float dx = sx - (w / 2 + Camera.panX(t, w, moving));
        float dy = sy - (h / 2 + Camera.panY(t, h, moving));
        double a = Math.toRadians(-ANGLE);
        float zoom = Camera.zoom(t, moving);
        return new float[] {
                (float) ((dx * Math.cos(a) - dy * Math.sin(a)) / zoom),
                (float) ((dx * Math.sin(a) + dy * Math.cos(a)) / zoom)
        };
    }

    /** The renderer draws the copy to blur past every screen edge (padded as it pads it), so the plan must reach it too. */
    private static float margin(int size) {
        Look.Post post = Look.YOJOHAN.post;
        return Lens.padded(size, Lens.margin(post), post.blurDivisor);
    }

    private static void assertCovers(int w, int h, boolean moving) {
        float pw = Camera.planWidth(w, h, ANGLE), ph = Camera.planHeight(w, h, ANGLE);
        float mx = margin(w), my = margin(h);
        for (int i = 0; i < 2000; i++) {
            double t = i * 7.9 + (i % 2 == 0 ? 0 : 1_793_500_000.0);
            for (float[] corner : new float[][] {{-mx, -my}, {w + mx, -my}, {-mx, h + my}, {w + mx, h + my}}) {
                float[] p = toPlan(corner[0], corner[1], t, w, h, moving);
                assertTrue("corner " + corner[0] + "," + corner[1] + " leaves the plan at t=" + t,
                        Math.abs(p[0]) <= pw / 2 && Math.abs(p[1]) <= ph / 2);
            }
        }
    }

    @Test
    public void tiltedPlanAlwaysCoversTheScreenAndTheBlurMarginAt1080p() {
        assertCovers(1920, 1080, true);
        assertCovers(1920, 1080, false);
    }

    @Test
    public void tiltedPlanAlwaysCoversTheScreenAndTheBlurMarginAt720p() {
        assertCovers(1280, 720, true);
        assertCovers(1280, 720, false);
    }

    @Test
    public void zoomOnlyPullsInAndStaysGentle() {
        for (int i = 0; i < 5000; i++) {
            float z = Camera.zoom(i * 3.7, true);
            assertTrue("zoom " + z, z >= 1f && z <= 1.15f);
        }
        assertTrue(Camera.zoom(123.0, false) == 1f);
    }
}
