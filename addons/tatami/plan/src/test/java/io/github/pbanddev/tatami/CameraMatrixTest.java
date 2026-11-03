package io.github.pbanddev.tatami;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CameraMatrixTest {
    private static final float ANGLE = Look.YOJOHAN.camera.angleDeg;

    @Test
    public void matrixPutsEveryPlanPointWhereTheCameraSays() {
        float w = 1920, h = 1080;
        float[] m = new float[16];
        for (int i = 0; i < 300; i++) {
            double t = i * 13.3 + (i % 2 == 0 ? 0 : 1_793_500_000.0);
            boolean moving = i % 3 != 0;
            CameraMatrix.build(t, w, h, moving, ANGLE, m);
            float px = (i * 97 % 2000) - 1000f, py = (i * 61 % 1600) - 800f;
            // column-major: clip = M * (px, py, 0, 1)
            float cx = m[0] * px + m[4] * py + m[12];
            float cy = m[1] * px + m[5] * py + m[13];
            float sx = (cx + 1) / 2 * w, sy = (1 - cy) / 2 * h;
            // undo the camera the way CameraTest does
            float dx = sx - (w / 2 + Camera.panX(t, w, moving));
            float dy = sy - (h / 2 + Camera.panY(t, h, moving));
            double a = Math.toRadians(-ANGLE);
            float z = Camera.zoom(t, moving);
            assertEquals(px, (float) ((dx * Math.cos(a) - dy * Math.sin(a)) / z), 0.05f);
            assertEquals(py, (float) ((dx * Math.sin(a) + dy * Math.cos(a)) / z), 0.05f);
        }
    }
}
