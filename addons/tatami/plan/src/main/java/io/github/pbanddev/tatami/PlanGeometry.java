package io.github.pbanddev.tatami;

/**
 * Questions about the rooms of a laid-out {@link Frame}: which room holds a point, where two rooms meet,
 * and which rooms are windmill centres. Linear scans: fine for setup, tests and tools, too slow to run for
 * every room every frame.
 */
public final class PlanGeometry {
    private PlanGeometry() {}

    /**
     * The room (its index) that holds the point, or -1 outside the plan. A point on a wall goes to the first room
     * found. Reads the cells when the frame has them, else the rooms as boxes.
     */
    public static int roomAt(Frame f, float x, float y) {
        if (f.cellCount > 0) {
            for (int c = 0; c < f.cellCount; c++) {
                if (x >= f.cellX[c] && x < f.cellX[c] + f.cellW[c] && y >= f.cellY[c] && y < f.cellY[c] + f.cellH[c]) {
                    return f.cellRoom[c];
                }
            }
            return -1;
        }
        for (int i = 0; i < f.roomCount; i++) {
            if (x >= f.roomX[i] && x < f.roomX[i] + f.roomW[i] && y >= f.roomY[i] && y < f.roomY[i] + f.roomH[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The length of the wall piece rooms i and j share, or 0 when they meet only at a corner or not at all.
     * When it is longer than 0, {@code out} gets its ends: x0, y0, x1, y1. Sides closer than {@code eps} touch.
     */
    public static float sharedEdge(Frame f, int i, int j, float eps, float[] out) {
        float ax0 = f.roomX[i], ay0 = f.roomY[i], ax1 = ax0 + f.roomW[i], ay1 = ay0 + f.roomH[i];
        float bx0 = f.roomX[j], by0 = f.roomY[j], bx1 = bx0 + f.roomW[j], by1 = by0 + f.roomH[j];
        boolean right = Math.abs(ax1 - bx0) <= eps, left = Math.abs(bx1 - ax0) <= eps;
        if (right || left) {
            float x = right ? ax1 : ax0, lo = Math.max(ay0, by0), hi = Math.min(ay1, by1);
            if (hi - lo > eps) return edge(out, x, lo, x, hi);
        }
        boolean below = Math.abs(ay1 - by0) <= eps, above = Math.abs(by1 - ay0) <= eps;
        if (below || above) {
            float y = below ? ay1 : ay0, lo = Math.max(ax0, bx0), hi = Math.min(ax1, bx1);
            if (hi - lo > eps) return edge(out, lo, y, hi, y);
        }
        return 0f;
    }

    private static float edge(float[] out, float x0, float y0, float x1, float y1) {
        out[0] = x0;
        out[1] = y0;
        out[2] = x1;
        out[3] = y1;
        return (x1 - x0) + (y1 - y0);
    }

    /**
     * True when room k is a windmill centre: 4 rooms wrap around it, so no straight wall runs through it.
     * At each of its corners exactly one of its two sides runs on past the corner (a T join), and the sides
     * that run on take turns around the room: horizontal, vertical, horizontal, vertical. Rooms on the plan's
     * edge never count. {@code delta} is how far from a corner to look; keep it below the thinnest room.
     */
    public static boolean isWindmillCentre(Frame f, int k, float delta) {
        float x0 = f.roomX[k], y0 = f.roomY[k], x1 = x0 + f.roomW[k], y1 = y0 + f.roomH[k];
        // corners in turn round the room, each with the direction that points away from the room
        float[][] corners = {{x0, y0, -1, -1}, {x1, y0, 1, -1}, {x1, y1, 1, 1}, {x0, y1, -1, 1}};
        boolean[] horizontal = new boolean[4];
        for (int c = 0; c < 4; c++) {
            float cx = corners[c][0], cy = corners[c][1], sx = corners[c][2], sy = corners[c][3];
            int above = roomAt(f, cx + sx * delta, cy - delta), below = roomAt(f, cx + sx * delta, cy + delta);
            int left = roomAt(f, cx - delta, cy + sy * delta), right = roomAt(f, cx + delta, cy + sy * delta);
            if (above < 0 || below < 0 || left < 0 || right < 0) return false;
            boolean runsOnSideways = above != below, runsOnUpOrDown = left != right;
            if (runsOnSideways == runsOnUpOrDown) return false;
            horizontal[c] = runsOnSideways;
        }
        return horizontal[0] == horizontal[2] && horizontal[1] == horizontal[3] && horizontal[0] != horizontal[1];
    }
}
