package io.github.pbanddev.tatami;

/**
 * What the camera sees in one frame: the screen, the plan's tilt and zoom, and the plan point at the screen's
 * centre. In screen px with y down:
 * {@code screen = (w / 2, h / 2) + zoom * rotate(rollDeg) * (plan - centre)}; a positive roll turns the plan
 * clockwise on screen (walls fall to the right). A {@link CameraCurve} fills it every frame with {@link #set};
 * no allocation.
 */
public final class View {
    /** The screen, in px. */
    public float w, h;
    /** The plan's tilt on screen, in degrees. */
    public float rollDeg;
    /** Screen px per plan unit. */
    public float zoom = 1f;
    /** The plan point at the screen's centre, in plan units. Nagaya keeps it within a tile of 0, so floats stay exact. */
    public double centreX, centreY;
    /** How far past each screen edge the plan must still be drawn, in screen px: the strip the blur reads. */
    public float margin;
    private double cos = 1, sin;

    public void set(float w, float h, float rollDeg, float zoom, double centreX, double centreY, float margin) {
        this.w = w;
        this.h = h;
        this.rollDeg = rollDeg;
        this.zoom = zoom;
        this.centreX = centreX;
        this.centreY = centreY;
        this.margin = margin;
        double a = Math.toRadians(rollDeg);
        cos = Math.cos(a);
        sin = Math.sin(a);
    }

    /** Plan units to clip space, column-major 4x4. */
    public void matrix(float[] out) {
        double c = cos * zoom, s = sin * zoom;
        java.util.Arrays.fill(out, 0f);
        out[0] = (float) (2 * c / w);
        out[1] = (float) (-2 * s / h);
        out[4] = (float) (-2 * s / w);
        out[5] = (float) (-2 * c / h);
        out[10] = 1f;
        out[12] = (float) (2 * (s * centreY - c * centreX) / w);
        out[13] = (float) (2 * (s * centreX + c * centreY) / h);
        out[15] = 1f;
    }

    /**
     * True when the plan box [x0, x1] x [y0, y1], grown by {@code grow} plan units on every side, can show on the
     * screen grown by {@link #margin}: the 2 rectangles overlap on all 4 of their axes.
     */
    public boolean sees(float x0, float y0, float x1, float y1, float grow) {
        double hx = (x1 - x0) / 2.0 + grow, hy = (y1 - y0) / 2.0 + grow;
        double dx = (x0 + x1) / 2.0 - centreX, dy = (y0 + y1) / 2.0 - centreY;
        double ex = (w / 2.0 + margin) / zoom, ey = (h / 2.0 + margin) / zoom;
        double c = Math.abs(cos), s = Math.abs(sin);
        if (Math.abs(dx) > hx + ex * c + ey * s) return false;
        if (Math.abs(dy) > hy + ex * s + ey * c) return false;
        // the screen's own axes in plan units: across (cos, -sin) and down (sin, cos)
        if (Math.abs(dx * cos - dy * sin) > ex + hx * c + hy * s) return false;
        return Math.abs(dx * sin + dy * cos) <= ey + hx * s + hy * c;
    }
}
