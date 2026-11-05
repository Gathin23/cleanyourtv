package io.github.pbanddev.tatami;

/** A look's camera: what it sees at a time. */
interface CameraCurve {
    /**
     * Fills {@code view} and {@code matrix} (plan units to clip space, column-major 4x4) for time {@code t} on a
     * {@code w} by {@code h} screen. {@code moving} false holds the camera at rest.
     */
    void at(double t, int w, int h, boolean moving, View view, float[] matrix);
}
