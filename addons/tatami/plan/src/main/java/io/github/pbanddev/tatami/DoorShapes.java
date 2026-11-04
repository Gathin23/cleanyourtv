package io.github.pbanddev.tatami;

/**
 * Draws one door of a {@link Frame} into the {@link SceneMesh}: a pale bar inside the wall,
 * always there, and the swing, a filled sector from the wall face to the leaf with dark strokes on the arc
 * and the leaf (none along the wall face). A shut door is only its bar. The bar and the sector are fills, which a
 * {@link Wash} reaches; the strokes are ink, which it never does.
 *
 * <p>In the door's own frame u runs along the wall from the hinge toward the bar's far end and v across
 * the wall toward the swing side, with the wall's centre line at v = 0. The swing turns about the hinge on
 * the wall face, (0, wall / 2), from u (shut) toward v.
 */
final class DoorShapes {
    private static final int ARC_SEGMENTS = 10;
    /** The most vertices one door adds: the bar, the sector's fan, the leaf, then the arc as lines. */
    static final int MAX_VERTICES = 6 + 3 * ARC_SEGMENTS + 6 + 6 * ARC_SEGMENTS;

    private DoorShapes() {}

    static void add(SceneMesh m, Frame f, int d, float screenHeight, Look look) {
        Look.Doors door = look.doors;
        float wall = screenHeight * look.walls.widthH, length = screenHeight * door.lengthH;
        float bar = Math.min(screenHeight * door.barHalfH, wall / 2);
        float radius = screenHeight * door.radiusH, line = screenHeight * door.lineH;
        boolean vertical = f.wallVertical[f.doorWall[d]];
        float ux = vertical ? 0 : f.doorAlong[d], uy = vertical ? f.doorAlong[d] : 0;
        float vx = vertical ? f.doorSide[d] : 0, vy = vertical ? 0 : f.doorSide[d];
        float x = f.doorX[d], y = f.doorY[d];

        m.color(look.palette.door);
        float x0 = x - vx * bar, y0 = y - vy * bar, x1 = x + vx * bar, y1 = y + vy * bar;
        float lx = ux * length, ly = uy * length;
        m.triangle(x0, y0, x0 + lx, y0 + ly, x1 + lx, y1 + ly);
        m.triangle(x0, y0, x1 + lx, y1 + ly, x1, y1);

        double open = f.doorOpening[d];
        if (!(open > 0)) return;
        float hx = x + vx * wall / 2, hy = y + vy * wall / 2;
        float px = hx + ux * radius, py = hy + uy * radius;
        for (int s = 1; s <= ARC_SEGMENTS; s++) {
            double a = open * s / ARC_SEGMENTS;
            float c = radius * (float) Math.cos(a), n = radius * (float) Math.sin(a);
            float qx = hx + ux * c + vx * n, qy = hy + uy * c + vy * n;
            m.triangle(hx, hy, px, py, qx, qy);
            px = qx;
            py = qy;
        }
        m.ink(look.palette.line);
        // the leaf reaches half a stroke past the arc, closing the corner where they meet
        float reach = radius + line / 2;
        float c = (float) Math.cos(open), n = (float) Math.sin(open);
        m.line(hx, hy, hx + reach * (ux * c + vx * n), hy + reach * (uy * c + vy * n), line);
        px = hx + ux * radius;
        py = hy + uy * radius;
        for (int s = 1; s <= ARC_SEGMENTS; s++) {
            double a = open * s / ARC_SEGMENTS;
            float ca = radius * (float) Math.cos(a), na = radius * (float) Math.sin(a);
            float qx = hx + ux * ca + vx * na, qy = hy + uy * ca + vy * na;
            m.line(px, py, qx, qy, line);
            px = qx;
            py = qy;
        }
    }
}
