package io.github.pbanddev.tatami;

/**
 * The view onto the plan: tilted by the look's angle, drifting slowly and pulling in and back out
 * over a few minutes. The plan is sized so that, tilted and panned as far as the drift goes, it
 * still covers every corner of the screen; the zoom only ever pulls in, so it never shows an edge.
 */
public final class Camera {
    private static final float PAN = 0.03f;
    private static final double PAN_SPEED_X = 0.05;
    private static final double PAN_SPEED_Y = 0.037;
    private static final float ZOOM_RANGE = 0.15f;
    private static final double ZOOM_SPEED = 0.021;
    /**
     * Spare plan past the screen's corners at the farthest pan. The copy to blur is drawn up to 24 px past
     * every screen edge ({@link Lens#margin}, padded to whole GPU tiles), so that strip must be plan too:
     * 6 % covers it down to 720p.
     */
    private static final float MARGIN = 1.06f;

    private Camera() {}

    public static float panX(double t, float w, boolean moving) {
        return moving ? (float) (PAN * w * Math.sin(t * PAN_SPEED_X)) : 0f;
    }

    public static float panY(double t, float h, boolean moving) {
        return moving ? (float) (PAN * h * Math.cos(t * PAN_SPEED_Y)) : 0f;
    }

    /** 1 is the widest view; the camera pulls in to 1.15 and back. */
    public static float zoom(double t, boolean moving) {
        return moving ? 1f + ZOOM_RANGE * 0.5f * (1f - (float) Math.cos(t * ZOOM_SPEED)) : 1f;
    }

    /** The plan's width for a w by h screen with the plan tilted by {@code angleDeg}. */
    public static float planWidth(float w, float h, float angleDeg) {
        double a = Math.toRadians(angleDeg), cos = Math.cos(a), sin = Math.abs(Math.sin(a));
        return (float) (2 * MARGIN * (reachX(w) * cos + reachY(h) * sin));
    }

    public static float planHeight(float w, float h, float angleDeg) {
        double a = Math.toRadians(angleDeg), cos = Math.cos(a), sin = Math.abs(Math.sin(a));
        return (float) (2 * MARGIN * (reachX(w) * sin + reachY(h) * cos));
    }

    private static double reachX(float w) {
        return w / 2.0 + PAN * w;
    }

    private static double reachY(float h) {
        return h / 2.0 + PAN * h;
    }
}
