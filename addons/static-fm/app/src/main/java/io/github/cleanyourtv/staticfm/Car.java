package io.github.cleanyourtv.staticfm;

/**
 * A compact closed car as a small 3D model, seen almost from the side and turned a little towards the viewer, so
 * a sliver of its front shows: boxes for the body, bonnet and bumpers, a cabin with a roof, pillars and windows
 * that only reflect, a raked windscreen, cylinders for the wheels. Each cell of the fine grid sends one ray into
 * it and keeps what it hits (the part and the surface's direction), so the painter can light it. The car never
 * moves, so this is worked out once.
 *
 * Model space, in metres: x towards the nose, y up from the road, z towards the viewer (the near side).
 */
final class Car {
    static final byte NONE = 0, BODY = 1, SILL = 2, LINE = 3, TRIM = 4, LIGHT = 5, GRILLE = 6, PLATE = 7, GLASS = 8,
            FRAME = 9, WINDOW = 10, TIRE = 13, TREAD = 14, RIM = 15, ARCH = 16, AMBER = 19;

    /** Screen heights per metre: the car is about 4 m long. */
    static final float SCALE = 0.18f;
    private static final double YAW = Math.toRadians(13), PITCH = Math.toRadians(8);
    /** View direction (into the scene), and the screen's right and up, in model space. */
    private static final float DX = (float) (-Math.sin(YAW) * Math.cos(PITCH)), DY = (float) -Math.sin(PITCH),
            DZ = (float) (-Math.cos(YAW) * Math.cos(PITCH));
    private static final float RX = (float) Math.cos(YAW), RZ = (float) -Math.sin(YAW);
    private static final float UX = (float) (-Math.sin(YAW) * Math.sin(PITCH)), UY = (float) Math.cos(PITCH),
            UZ = (float) (-Math.cos(YAW) * Math.sin(PITCH));

    private static final float WHEEL_R = 0.31f, WHEEL_X = 1.25f, WHEEL_Y = 0.31f, SIDE = 0.85f;

    /** Boxes: minX, maxX, minY, maxY, minZ, maxZ, part. */
    private static final float[][] BOXES = {
        {-1.95f, 1.05f, 0.26f, 0.82f, -SIDE, SIDE, BODY},        // the body: sill to beltline
        {1.05f, 1.90f, 0.26f, 0.82f, -SIDE, SIDE, BODY},         // the bonnet, level with the beltline: no step
        {1.90f, 2.00f, 0.26f, 0.80f, -0.83f, 0.83f, BODY},       // front bumper, flush below the bonnet
        {-2.03f, -1.95f, 0.28f, 0.56f, -0.83f, 0.83f, BODY},     // rear bumper
        {0.72f, 0.90f, 0.84f, 0.96f, SIDE - 0.11f, SIDE + 0.08f, BODY}, // the door mirror
    };
    /**
     * The cabin, in profile: the windscreen runs from the cowl up and back to the roof, the roof runs level, and
     * the tail slopes down to the beltline like a hatchback's. Its sides lean in a little from the body's.
     */
    private static final float BELT = 0.82f, ROOF = 1.30f, CABIN_Z = 0.74f;
    private static final float COWL_X = 0.98f, ROOF_FRONT = 0.50f, ROOF_BACK = -1.25f, TAIL_X = -1.88f;

    final int c0, r0, cols, rows;
    final byte[] part;
    /** The surface normal at each hit, and two spare numbers: for a wheel its angle and radius. */
    final float[] nx, ny, nz, a, b;

    Car(float carX, float groundY, float cell, int fcols, int frows) {
        c0 = Math.max(0, (int) ((carX - 0.50f) / cell));
        int c1 = Math.min(fcols - 1, (int) ((carX + 0.50f) / cell));
        r0 = Math.max(0, (int) ((groundY - 0.36f) * frows));
        int r1 = Math.min(frows - 1, (int) ((groundY + 0.04f) * frows));
        cols = c1 - c0 + 1;
        rows = r1 - r0 + 1;
        int n = cols * rows;
        part = new byte[n];
        nx = new float[n];
        ny = new float[n];
        nz = new float[n];
        a = new float[n];
        b = new float[n];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float sx = ((c0 + c + 0.5f) * cell - carX) / SCALE, sy = (groundY - (r0 + r + 0.5f) / frows) / SCALE;
                trace(r * cols + c, sx, sy);
            }
        }
    }

    private float bestT;
    private int at;

    private void hit(float t, byte p, float x, float y, float z, float pa, float pb) {
        if (t >= bestT) return;
        bestT = t;
        part[at] = p;
        nx[at] = x;
        ny[at] = y;
        nz[at] = z;
        a[at] = pa;
        b[at] = pb;
    }

    private void trace(int i, float sx, float sy) {
        at = i;
        bestT = Float.MAX_VALUE;
        // the ray starts well in front of the car
        float ox = RX * sx + UX * sy - DX * 30, oy = UY * sy - DY * 30, oz = RZ * sx + UZ * sy - DZ * 30;

        for (float[] bx : BOXES) {
            float t0 = -Float.MAX_VALUE, t1 = Float.MAX_VALUE;
            int axis = 0;
            for (int k = 0; k < 3; k++) {
                float o = k == 0 ? ox : k == 1 ? oy : oz, d = k == 0 ? DX : k == 1 ? DY : DZ;
                float ta = (bx[k * 2] - o) / d, tb = (bx[k * 2 + 1] - o) / d;
                float lo = Math.min(ta, tb), hi = Math.max(ta, tb);
                if (lo > t0) {
                    t0 = lo;
                    axis = k;
                }
                t1 = Math.min(t1, hi);
            }
            if (t0 > t1 || t0 >= bestT) continue;
            float px = ox + DX * t0, py = oy + DY * t0, pz = oz + DZ * t0;
            byte p = (byte) bx[6];
            // the entry face's outward normal: the ray comes from +x, +y, +z, so it is the positive one
            float fx = axis == 0 ? 1 : 0, fy = axis == 1 ? 1 : 0, fz = axis == 2 ? 1 : 0;
            float pa = 0;
            if (bx != BOXES[4]) {
                if (axis == 2) p = side(px, py);
                else if (axis == 0 && px > 1.5f) p = front(px, py, pz);
                if (p == BODY && axis == 2 && py > 0.60f && py < 0.645f) pa = 1;   // the crease along the side
            }
            hit(t0, p, fx, fy, fz, pa, 0);
        }
        wheels(ox, oy, oz);
        cabin(ox, oy, oz);
    }

    /** The near side of the body: sill, wheel arches, door lines, handles. */
    private static byte side(float x, float y) {
        float w = (float) Math.sqrt(Math.min(sq(x - WHEEL_X), sq(x + WHEEL_X)) + sq(y - WHEEL_Y));
        if (w < WHEEL_R + 0.075f) return ARCH;
        if (y < 0.36f) return SILL;
        if (y > 0.40f && (Math.abs(x - 0.82f) < 0.018f || Math.abs(x + 0.30f) < 0.018f || Math.abs(x + 1.02f) < 0.018f)) return LINE;
        if (y > 0.70f && y < 0.74f && (x > -0.22f && x < -0.06f || x > -0.96f && x < -0.80f)) return TRIM;
        if (x > 1.42f && x < 1.54f && y > 0.64f && y < 0.69f) return AMBER;
        return BODY;
    }

    /**
     * The front of the car, from the nose down: headlights with the grille between them, then the number plate,
     * the lower intake and the fog lamps. The bumper runs flush under the bonnet, so this is all one face.
     */
    private static byte front(float x, float y, float z) {
        float az = Math.abs(z);
        if (sq((az - 0.60f) / 0.21f) + sq((y - 0.71f) / 0.062f) < 1) return LIGHT;
        if (az < 0.37f && y > 0.655f && y < 0.79f) return GRILLE;
        if (az < 0.27f && y > 0.50f && y < 0.605f) return PLATE;
        if (az < 0.56f && y > 0.31f && y < 0.45f) return GRILLE;
        if (az > 0.62f && az < 0.76f && y > 0.33f && y < 0.42f) return LIGHT;
        return BODY;
    }

    /** The cabin: its near side with pillars and windows, the roof, and the windscreen. */
    private void cabin(float ox, float oy, float oz) {
        // the near side, a flat pane leaning in from the body
        float t = (CABIN_Z - oz) / DZ;
        float x = ox + DX * t, y = oy + DY * t;
        if (y > BELT && y < ROOF) {
            float up = (y - BELT) / (ROOF - BELT);
            float frontX = COWL_X + (ROOF_FRONT - COWL_X) * up, backX = TAIL_X + (ROOF_BACK - TAIL_X) * up;
            if (x < frontX && x > backX) {
                boolean pillar = x > frontX - 0.09f                      // the A-pillar, along the windscreen
                        || x < backX + 0.30f                              // the C-pillar, along the tail
                        || Math.abs(x + 0.33f) < 0.05f                    // the B-pillar, between the doors
                        || up > 0.86f || up < 0.07f;                      // the roof rail and the window sill
                // a window keeps how high and how far along it is, for its reflection
                hit(t, pillar ? BODY : WINDOW, 0, 0, 1, up, (x - backX) / (frontX - backX));
            }
        }
        // the roof
        t = (ROOF - oy) / DY;
        x = ox + DX * t;
        float z = oz + DZ * t;
        if (x > ROOF_BACK && x < ROOF_FRONT && Math.abs(z) < CABIN_Z) hit(t, BODY, 0, 1, 0, 0, 0);
        // the windscreen, raked back from the cowl to the roof
        float ex = ROOF_FRONT - COWL_X, ey = ROOF - BELT, len = (float) Math.sqrt(ex * ex + ey * ey);
        float px = ey / len, py = -ex / len;               // the pane's normal, forward and up
        t = ((COWL_X - ox) * px + (BELT - oy) * py) / (DX * px + DY * py);
        float hx = ox + DX * t - COWL_X, hy = oy + DY * t - BELT;
        z = oz + DZ * t;
        float up = (hx * ex + hy * ey) / (len * len);
        if (up >= 0 && up <= 1 && Math.abs(z) <= CABIN_Z) {
            boolean frame = up > 0.92f || up < 0.04f || Math.abs(z) > CABIN_Z - 0.06f;
            hit(t, frame ? FRAME : GLASS, px, py, 0, up, z / CABIN_Z);
        }
    }

    private void wheels(float ox, float oy, float oz) {
        for (int s = -1; s <= 1; s += 2) {
            float cx = s * WHEEL_X;
            // the face of the near wheel
            float t = (SIDE + 0.01f - oz) / DZ;
            float px = ox + DX * t - cx, py = oy + DY * t - WHEEL_Y;
            float rad = (float) Math.sqrt(px * px + py * py);
            if (rad < WHEEL_R) hit(t, rad > WHEEL_R * 0.72f ? TIRE : RIM, 0, 0, 1, (float) Math.atan2(py, px), rad / WHEEL_R);
            // its tread: the cylinder's side, between the body and the face
            float lx = ox - cx, ly = oy - WHEEL_Y;
            float qa = DX * DX + DY * DY, qb = lx * DX + ly * DY, qc = lx * lx + ly * ly - WHEEL_R * WHEEL_R;
            float disc = qb * qb - qa * qc;
            if (disc > 0) {
                float tt = (-qb - (float) Math.sqrt(disc)) / qa;
                float z = oz + DZ * tt;
                if (z > 0.60f && z < SIDE + 0.01f) hit(tt, TREAD, (lx + DX * tt) / WHEEL_R, (ly + DY * tt) / WHEEL_R, 0, 0, 0);
            }
        }
    }

    private static float sq(float v) {
        return v * v;
    }
}
