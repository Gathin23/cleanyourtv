package io.github.pbanddev.tatami;

/**
 * Nagaya's camera ({@link Look.Camera}): a closed form in the wall clock, with no state, so Home, the screensaver
 * and the settings show the same view at the same time. It pivots on the screen's centre.
 *
 * <p>One cycle: the roll holds at {@code rollFromDeg}, glides to {@code rollToDeg} ({@link Ease#glide}), holds, and
 * glides back. The zoom glides the same way from {@code zoomFrom} to {@code zoomTo} from halfway through the glide to
 * {@code rollToDeg}, and back from halfway through the glide back, so the widest view comes with the steepest roll.
 *
 * <p>The plan drifts along one fixed heading in plan axes: the one that shows as {@code driftHeadingDeg} on screen
 * at the middle roll, and turns with the roll by up to half its range either side. The wander adds a sine along
 * that heading (the speed swings by {@code wanderH}) and one across it (the heading swings by about
 * {@code wanderDeg}). The centre is the sum of their integrals, worked out in double and brought back to within a
 * tile of 0 by whole lattice steps; every copy of the tile is the same, so the step never shows.
 *
 * <p>The clock never goes into a float: every phase is a remainder of the double clock.
 */
final class NagayaCamera implements CameraCurve {
    /** The speed's wander takes this many times as long as the heading's, so the 2 rarely fall into step. */
    private static final double SPEED_WANDER = 1.3;

    private final Look.Camera camera;
    private final float unitH, margin;
    private final int tileW, tileH, shift;
    private final double hold, ramp, cycle, zoomStart;
    /** The way the plan moves on screen, in plan axes, and the way across it. */
    private final double alongX, alongY;
    /** The wander's periods (s) and its sizes in H: speed along and how far it carries, likewise across. */
    private final double speedPeriod, speedReach, headPeriod, headReach;

    NagayaCamera(Look look) {
        camera = look.camera;
        Look.Camera c = camera;
        unitH = look.plan.unitH;
        tileW = look.plan.tileWUnits;
        tileH = look.plan.tileHUnits;
        shift = tileW / 2;
        margin = Scene.margin(look);
        hold = c.rollHoldSeconds;
        ramp = c.rollRampSeconds;
        cycle = 2 * (hold + ramp);
        zoomStart = hold + ramp / 2;
        if (!(ramp > 0 && hold >= 0 && c.zoomRampSeconds > 0 && c.zoomRampSeconds <= hold + ramp
                && c.wanderSeconds > 0 && tileW > 0 && tileH > 0 && unitH > 0)) {
            throw new IllegalArgumentException("camera: roll ramp " + ramp + " s, hold " + hold + " s, zoom ramp "
                    + c.zoomRampSeconds + " s, wander " + c.wanderSeconds + " s");
        }
        double heading = Math.toRadians(c.driftHeadingDeg - (c.rollFromDeg + c.rollToDeg) / 2.0);
        alongX = Math.cos(heading);
        alongY = Math.sin(heading);
        headPeriod = c.wanderSeconds;
        speedPeriod = c.wanderSeconds * SPEED_WANDER;
        speedReach = c.wanderH * speedPeriod / (2 * Math.PI);
        headReach = c.driftH * Math.tan(Math.toRadians(c.wanderDeg)) * headPeriod / (2 * Math.PI);
    }

    @Override
    public void at(double t, int w, int h, boolean moving, View view, float[] matrix) {
        if (!moving) {
            view.set(w, h, camera.angleDeg, camera.restZoom, 0.0, 0.0, margin);
        } else {
            double p = rem(t, cycle);
            // how far the plan has moved on screen, in H, along its heading and across it
            double along = camera.driftH * t - speedReach * Math.cos(2 * Math.PI * rem(t, speedPeriod) / speedPeriod);
            double across = -headReach * Math.cos(2 * Math.PI * rem(t, headPeriod) / headPeriod);
            // the camera moves the other way; plan units are px at zoom 1
            double x = -h * (along * alongX - across * alongY), y = -h * (along * alongY + across * alongX);
            // TilePlan's step, so the lattice here is the one it lays copies on
            float unit = h * unitH;
            double a1x = tileW * (double) unit, a2x = shift * (double) unit, a2y = tileH * (double) unit;
            double j = Math.floor(y / a2y);
            y -= j * a2y;
            x -= j * a2x;
            x -= Math.floor(x / a1x) * a1x;
            view.set(w, h, (float) roll(p), (float) zoom(p), x, y, margin);
        }
        view.matrix(matrix);
    }

    /** The roll at p seconds into the cycle. */
    private double roll(double p) {
        double from = camera.rollFromDeg, to = camera.rollToDeg;
        if (p < hold) return from;
        if (p < hold + ramp) return from + (to - from) * Ease.glide((p - hold) / ramp);
        if (p < 2 * hold + ramp) return to;
        return to + (from - to) * Ease.glide((p - 2 * hold - ramp) / ramp);
    }

    /** The zoom at p seconds into the cycle: out from halfway through the first glide, back in half a cycle later. */
    private double zoom(double p) {
        double from = camera.zoomFrom, to = camera.zoomTo, q = rem(p - zoomStart, cycle), half = cycle / 2;
        if (q < half) return from + (to - from) * Ease.glide(q / camera.zoomRampSeconds);
        return to + (from - to) * Ease.glide((q - half) / camera.zoomRampSeconds);
    }

    /** t modulo a period, in [0, period). */
    private static double rem(double t, double period) {
        double r = t % period;
        return r < 0 ? r + period : r;
    }
}
