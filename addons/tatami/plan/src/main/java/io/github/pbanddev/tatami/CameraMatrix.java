package io.github.pbanddev.tatami;

/**
 * The {@link Camera} as one OpenGL matrix (column-major, 4x4): plan units in, clip space out.
 * Screen = centre + rotate(angle) * zoom * plan, with y pointing down, then mapped to -1..1.
 */
public final class CameraMatrix {
    private CameraMatrix() {}

    public static void build(double t, float w, float h, boolean moving, float angleDeg, float[] out) {
        double angle = Math.toRadians(angleDeg);
        float zoom = Camera.zoom(t, moving);
        float cos = (float) Math.cos(angle) * zoom, sin = (float) Math.sin(angle) * zoom;
        float cx = w / 2 + Camera.panX(t, w, moving);
        float cy = h / 2 + Camera.panY(t, h, moving);
        java.util.Arrays.fill(out, 0f);
        out[0] = 2 * cos / w;      // clip x from plan x
        out[1] = -2 * sin / h;     // clip y from plan x
        out[4] = -2 * sin / w;     // clip x from plan y
        out[5] = -2 * cos / h;     // clip y from plan y
        out[10] = 1f;
        out[12] = 2 * cx / w - 1;
        out[13] = 1 - 2 * cy / h;
        out[15] = 1f;
    }
}
