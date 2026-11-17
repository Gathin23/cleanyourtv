package io.github.pbanddev.tatami;

import java.util.Arrays;
import java.util.Random;

/**
 * The floor plan: one rectangle cut into rooms by a tree that is grown once per seed. Each part of the tree
 * is a room, a straight cut across its longer side (2 parts, 1 wall), or a windmill: 4 parts wrapped round a
 * centre room (5 parts, 4 walls), so no straight wall runs through it. The whole plan is a windmill too, so
 * no wall crosses it from edge to edge.
 *
 * <p>Every wall is held as a share of its part. Each share slides on a slow sine, so the room on one side of a
 * wall gains exactly the space its neighbour loses: no gaps, no overlaps. Only the shares move; the tree, and
 * so every room's and wall's index, stays the same in every frame.
 *
 * <p>No room's short side ever drops below {@link Look.Plan#minSideH}, and no room is ever longer than
 * {@link Look.Plan#maxAspect} times its width. Each part knows the sizes it can take while all of its own
 * parts keep theirs (its fit: a least width, a least height, and a range of width over height), and every
 * frame it holds its shares where its parts stay in their fits. That only stops a wall for a moment, and
 * never moves a share past the range its sine sweeps anyway.
 *
 * <p>Pure: the same seed and time give the same plan, so Home and the screensaver, both driven by the wall
 * clock, draw the same frame and the hand-off between them does not jump.
 */
public final class FloorPlan {
    /** Each share swings this far either side of its base. */
    private static final float AMPLITUDE = 0.12f;
    /** A share's speed, in radians per second: from SPEED to SPEED + SPEED_SPREAD. */
    private static final float SPEED = 0.15f, SPEED_SPREAD = 0.20f;
    /**
     * A part is cut with a vertical wall if it is at least this wide for its height, else with a horizontal
     * one. Below 1, so near-square parts make tall rooms, against the wide ones windmills on wide parts make.
     */
    private static final float CUT_X_ASPECT = 0.8f;
    /** A cut's base share is picked in CUT_BASE + [0, CUT_SPREAD), and the cut stays inside CUT_MIN to CUT_MAX. */
    private static final float CUT_BASE = 0.38f, CUT_SPREAD = 0.24f, CUT_MIN = 0.25f, CUT_MAX = 0.75f;
    /**
     * A windmill's two walls on each axis: base shares in MILL_LOW + [0, MILL_SPREAD) and MILL_HIGH +
     * [0, MILL_SPREAD), kept MILL_EDGE from its sides and MILL_GAP apart.
     */
    private static final float MILL_LOW = 0.35f, MILL_HIGH = 0.58f, MILL_SPREAD = 0.07f;
    private static final float MILL_EDGE = 0.12f, MILL_GAP = 0.15f;
    /**
     * A windmill's 4 shares swing at one speed, each off by up to MILL_JITTER radians, so its centre glides
     * back and forth on a diagonal and keeps its shape. On a wide windmill all 4 swing together, which keeps
     * the long top and bottom rooms' shapes too; on a tall one the y-walls swing against the x-walls, which
     * keeps the long side rooms' shapes.
     */
    private static final float MILL_JITTER = 0.3f;
    /**
     * The whole plan's windmill sits as close to the middle as the windmill bases allow, so its four long
     * walls end on screen instead of running across it.
     */
    private static final float ROOT_LOW = 0.42f, ROOT_HIGH = 0.58f;
    /**
     * Parts this close to square and at most this big (H² on screen) may become windmills. Up to
     * MILL_ROOMS_AREA the 4 outer parts are rooms; above, they may be cut again.
     */
    private static final float MILL_MAX_ASPECT = 1.8f, MILL_MAX_AREA = 1.0f, MILL_ROOMS_AREA = 0.65f;
    /**
     * A windmill's fit is worked out for heights from this share of its height at the base shares up: its
     * parent holds it there, which it seldom needs to.
     */
    private static final float MILL_SHRINK = 0.65f;
    /** Tries at fresh base shares before a part gives up on a windmill or a cut. */
    private static final int TRIES = 8;
    /** Sizes are judged on screen at the camera's middle zoom (it breathes from 1 to 1.15). */
    private static final float ZOOM = 1.075f;
    /** Rooms keep this much inside the longest shape and the shortest side allowed, for rounding. */
    private static final float ROUNDING = 0.99f;

    private static final int ROOM = 0, CUT_X = 1, CUT_Y = 2, MILL = 3, MILL_MIRRORED = 4;

    public final int rooms;
    /** The least side of a room, in plan heights. */
    private final float minSide;
    // The tree. The Builder grows these once; after that they are only read.
    /** Per part: its kind, its first share, its first wall (or its room), and its children. */
    private int[] kind = new int[64], share = new int[64], index = new int[64];
    private int[][] child = new int[64][];
    /**
     * Per part, its fit: the least and most width over height, and the least width and height (in plan
     * heights), it can take while its own parts keep theirs.
     */
    private float[] fitLo = new float[64], fitHi = new float[64], fitW = new float[64], fitH = new float[64];
    /** Per share: its sine, and the least and most it can be after the fixed clamps. */
    private float[] base = new float[64], speed = new float[64], phase = new float[64], lo = new float[64], hi = new float[64];
    /** Per room: the smallest share of the plan's width and height it gets with every share at its worst. */
    private float[] minShareW = new float[64], minShareH = new float[64];

    public FloorPlan(Look look) {
        this(look, look.plan.seed);
    }

    /** Kept for tests written before the windmill layout: Look.YOJOHAN with another seed. The depth is not used. */
    public FloorPlan(long seed, int depth) {
        this(Look.YOJOHAN, seed);
    }

    FloorPlan(Look look, long seed) {
        float angle = look.camera.angleDeg;
        // the plan Scene lays out on a 16:9 screen, in screen heights
        float planW = Camera.planWidth(16, 9, angle) / 9, planH = Camera.planHeight(16, 9, angle) / 9;
        minSide = look.plan.minSideH / planH;
        Builder b = new Builder(look.plan, new Random(seed), planH);
        b.root(planW / planH);
        rooms = b.rooms;
        kind = Arrays.copyOf(kind, b.parts);
        share = Arrays.copyOf(share, b.parts);
        index = Arrays.copyOf(index, b.parts);
        child = Arrays.copyOf(child, b.parts);
        fitLo = Arrays.copyOf(fitLo, b.parts);
        fitHi = Arrays.copyOf(fitHi, b.parts);
        fitW = Arrays.copyOf(fitW, b.parts);
        fitH = Arrays.copyOf(fitH, b.parts);
        base = Arrays.copyOf(base, b.shares);
        speed = Arrays.copyOf(speed, b.shares);
        phase = Arrays.copyOf(phase, b.shares);
        lo = Arrays.copyOf(lo, b.shares);
        hi = Arrays.copyOf(hi, b.shares);
        minShareW = Arrays.copyOf(minShareW, rooms);
        minShareH = Arrays.copyOf(minShareH, rooms);
    }

    /**
     * Fills {@code out} with the rooms and walls of the plan at time {@code t} (seconds), inside x, y, w, h, and
     * a stand-in door in the middle of every wall, for {@link Doors} to replace.
     */
    public void layout(double t, float x, float y, float w, float h, Frame out) {
        // a Frame made for fewer rooms, or an empty plan, gets no rooms: never an exception
        boolean fits = out.roomX.length >= rooms && w > 0 && h > 0;
        out.roomCount = fits ? rooms : 0;
        out.wallCount = fits ? rooms - 1 : 0;
        out.doorCount = out.wallCount;
        if (!fits) return;
        place(0, t, x, y, w, h, h, out);
        for (int k = 0; k < rooms; k++) {
            out.roomMinW[k] = Math.max(minShareW[k] * w, minSide * ROUNDING * h);
            out.roomMinH[k] = Math.max(minShareH[k] * h, minSide * ROUNDING * h);
        }
        for (int k = 0; k < out.wallCount; k++) {
            out.doorWall[k] = k;
            out.doorX[k] = (out.wallX0[k] + out.wallX1[k]) / 2;
            out.doorY[k] = (out.wallY0[k] + out.wallY1[k]) / 2;
            out.doorAlong[k] = out.wallVertical[k] ? -1 : 1;
            out.doorSide[k] = 1;
            out.doorOpening[k] = Math.toRadians(90 * (0.55 + 0.45 * Math.sin(1.3 * t + k)));
        }
    }

    private float raw(int s, double t) {
        return base[s] + AMPLITUDE * (float) Math.sin(speed[s] * t + phase[s]);
    }

    private static float clamp(float v, float least, float most) {
        return Math.max(least, Math.min(most, v));
    }

    private static float clampCut(float raw) {
        return clamp(raw, CUT_MIN, CUT_MAX);
    }

    /** A windmill's lower wall on one axis, from its two raw shares: kept off the sides and MILL_GAP below the upper wall. */
    private static float millLow(float a, float b) {
        a = edge(a);
        b = edge(b);
        return b - a >= MILL_GAP ? a : middle(a, b) - MILL_GAP / 2;
    }

    private static float millHigh(float a, float b) {
        a = edge(a);
        b = edge(b);
        return b - a >= MILL_GAP ? b : middle(a, b) + MILL_GAP / 2;
    }

    private static float edge(float s) {
        return clamp(s, MILL_EDGE, 1 - MILL_EDGE);
    }

    private static float middle(float a, float b) {
        return clamp((a + b) / 2, MILL_EDGE + MILL_GAP / 2, 1 - MILL_EDGE - MILL_GAP / 2);
    }

    /** The least gap between the windmill walls held by shares s and s + 1, at any time. */
    private float millGap(int s) {
        return Math.max(MILL_GAP, edge(base[s + 1] - AMPLITUDE) - edge(base[s] + AMPLITUDE));
    }

    /**
     * Where cut n with this raw share goes in a w by h part: as near the raw share as keeps both of its parts
     * in their fits. {@code unit} is the plan's height.
     */
    private float cutAt(int n, float raw, float w, float h, float unit) {
        int s = share[n], p = child[n][0], q = child[n][1];
        float r = clampCut(raw), a = w / h;
        if (kind[n] == CUT_X) {     // parts r * w by h and (1 - r) * w by h
            r = clamp(r, Math.max(fitW[p] * unit / w, Math.max(fitLo[p] / a, 1 - fitHi[q] / a)),
                    Math.min(1 - fitW[q] * unit / w, Math.min(fitHi[p] / a, 1 - fitLo[q] / a)));
        } else {                    // parts w by r * h and w by (1 - r) * h
            r = clamp(r, Math.max(fitH[p] * unit / h, Math.max(a / fitHi[p], 1 - a / fitLo[q])),
                    Math.min(1 - fitH[q] * unit / h, Math.min(a / fitLo[p], 1 - a / fitHi[q])));
        }
        return clamp(r, lo[s], hi[s]);
    }

    /**
     * Windmill n's lower (or upper) y-wall, as a share of its height h, from its raw y-shares: as near them as
     * gives each of its 5 parts its least height. Heights: top s1, right s2, bottom 1 - s2, left 1 - s1,
     * centre s2 - s1.
     */
    private float millY(int n, boolean upper, float h, float unit, float sa, float sb) {
        int s = share[n] + 2;
        int[] c = child[n];
        float lowLo = Math.max(lo[s], fitH[c[0]] * unit / h), lowHi = Math.min(hi[s], 1 - fitH[c[3]] * unit / h);
        float highLo = Math.max(lo[s + 1], fitH[c[1]] * unit / h), highHi = Math.min(hi[s + 1], 1 - fitH[c[2]] * unit / h);
        float gap = Math.max(millGap(s), fitH[c[4]] * unit / h);
        float s2 = clamp(clamp(millHigh(sa, sb), Math.max(highLo, lowLo + gap), highHi), lo[s + 1], hi[s + 1]);
        if (upper) return s2;
        float s1 = clamp(millLow(sa, sb), lowLo, Math.min(lowHi, s2 - gap));
        return clamp(s1, lo[s], Math.min(hi[s], s2 - millGap(s)));
    }

    /**
     * Windmill n's lower (or upper) x-wall, as a share of its width w, from its raw x-shares, given its y-walls
     * s1 and s2: as near the raw shares as keeps its 5 parts in their fits. Widths: top r2, right 1 - r2,
     * bottom 1 - r1, left r1, centre r2 - r1; the heights are as in {@link #millY}.
     */
    private float millX(int n, boolean upper, float w, float h, float unit, float s1, float s2, float ra, float rb) {
        int s = share[n];
        int[] c = child[n];
        float a = w / h, u = unit / w;
        float highLo = Math.max(Math.max(lo[s + 1], fitW[c[0]] * u), Math.max(fitLo[c[0]] * s1 / a, 1 - fitHi[c[1]] * s2 / a));
        float highHi = Math.min(Math.min(hi[s + 1], 1 - fitW[c[1]] * u), Math.min(fitHi[c[0]] * s1 / a, 1 - fitLo[c[1]] * s2 / a));
        float lowLo = Math.max(Math.max(lo[s], fitW[c[3]] * u), Math.max(fitLo[c[3]] * (1 - s1) / a, 1 - fitHi[c[2]] * (1 - s2) / a));
        float lowHi = Math.min(Math.min(hi[s], 1 - fitW[c[2]] * u), Math.min(fitHi[c[3]] * (1 - s1) / a, 1 - fitLo[c[2]] * (1 - s2) / a));
        float gapLo = Math.max(Math.max(millGap(s), fitW[c[4]] * u), fitLo[c[4]] * (s2 - s1) / a);
        float gapHi = fitHi[c[4]] * (s2 - s1) / a;
        float r2 = clamp(millHigh(ra, rb), Math.max(highLo, lowLo + gapLo), Math.min(highHi, lowHi + gapHi));
        r2 = clamp(r2, lo[s + 1], hi[s + 1]);
        if (upper) return r2;
        float r1 = clamp(millLow(ra, rb), Math.max(lowLo, r2 - gapHi), Math.min(lowHi, r2 - gapLo));
        return clamp(r1, lo[s], Math.min(hi[s], r2 - millGap(s)));
    }

    private void place(int n, double t, float x, float y, float w, float h, float unit, Frame out) {
        int[] c = child[n];
        int s = share[n];
        switch (kind[n]) {
            case ROOM: {
                int k = index[n];
                out.roomX[k] = x;
                out.roomY[k] = y;
                out.roomW[k] = w;
                out.roomH[k] = h;
                return;
            }
            case CUT_X: {
                float at = x + w * cutAt(n, raw(s, t), w, h, unit);
                wall(out, index[n], at, y, at, y + h);
                place(c[0], t, x, y, at - x, h, unit, out);
                place(c[1], t, at, y, x + w - at, h, unit, out);
                return;
            }
            case CUT_Y: {
                float at = y + h * cutAt(n, raw(s, t), w, h, unit);
                wall(out, index[n], x, at, x + w, at);
                place(c[0], t, x, y, w, at - y, unit, out);
                place(c[1], t, x, at, w, y + h - at, unit, out);
                return;
            }
            default: {
                float ra = raw(s, t), rb = raw(s + 1, t), sa = raw(s + 2, t), sb = raw(s + 3, t);
                float s1 = millY(n, false, h, unit, sa, sb), s2 = millY(n, true, h, unit, sa, sb);
                float r1 = millX(n, false, w, h, unit, s1, s2, ra, rb), r2 = millX(n, true, w, h, unit, s1, s2, ra, rb);
                // Clockwise, from the left side: the top part runs to x2, the bottom part from x1.
                // Mirrored, the same from the right side.
                boolean mirrored = kind[n] == MILL_MIRRORED;
                float near = mirrored ? x + w : x, far = mirrored ? x : x + w;
                float x1 = near + (far - near) * r1, x2 = near + (far - near) * r2;
                float y1 = y + h * s1, y2 = y + h * s2, bottom = y + h;
                int k = index[n];
                wall(out, k, near, y1, x2, y1);
                wall(out, k + 1, x2, y, x2, y2);
                wall(out, k + 2, x1, y2, far, y2);
                wall(out, k + 3, x1, y1, x1, bottom);
                between(c[0], t, near, y, x2, y1, unit, out);
                between(c[1], t, x2, y, far, y2, unit, out);
                between(c[2], t, x1, y2, far, bottom, unit, out);
                between(c[3], t, near, y1, x1, bottom, unit, out);
                between(c[4], t, x1, y1, x2, y2, unit, out);
            }
        }
    }

    /** Places part n in the box between two x and two y edges, given in either order. */
    private void between(int n, double t, float xa, float ya, float xb, float yb, float unit, Frame out) {
        place(n, t, Math.min(xa, xb), Math.min(ya, yb), Math.abs(xb - xa), Math.abs(yb - ya), unit, out);
    }

    private static void wall(Frame out, int k, float xa, float ya, float xb, float yb) {
        out.wallX0[k] = Math.min(xa, xb);
        out.wallX1[k] = Math.max(xa, xb);
        out.wallY0[k] = Math.min(ya, yb);
        out.wallY1[k] = Math.max(ya, yb);
        out.wallVertical[k] = xa == xb;
    }

    /**
     * Grows the tree once, from the seed, in plan heights. A part is judged at its base shares: a cut or a
     * windmill is only kept if it fits there, and a part only stays a room if a room fits there.
     */
    private final class Builder {
        private final Look.Plan look;
        private final Random random;
        /** Plan heights to screen heights at the middle zoom. */
        private final float screen;
        private final float roomFit;
        int parts, shares, rooms, walls;

        Builder(Look.Plan look, Random random, float planH) {
            this.look = look;
            this.random = random;
            screen = planH * ZOOM;
            roomFit = look.maxAspect * ROUNDING;
        }

        void root(float aspect) {
            Size plan = new Size(aspect, 1);
            boolean mirrored = random.nextFloat() >= look.clockwiseShare;
            if (mill(plan, ROOT_LOW, ROOT_HIGH, ROOT_LOW, ROOT_HIGH, mirrored, true, true) < 0) part(plan);
        }

        private int part(Size p) {
            float area = p.w * p.h * screen * screen;
            boolean split = area > look.splitAboveH2 || (area >= look.stopBelowH2 && random.nextBoolean())
                    || !roomFits(p);
            if (!split) return room(p);
            float aspect = Math.max(p.w / p.h, p.h / p.w);
            if (aspect <= MILL_MAX_ASPECT && area >= look.windmillMinH2 && area <= MILL_MAX_AREA
                    && random.nextFloat() < look.windmillOdds) {
                boolean mirrored = random.nextFloat() >= look.clockwiseShare;
                for (int i = 0; i < TRIES; i++) {
                    float ra = MILL_LOW + MILL_SPREAD * random.nextFloat(), rb = MILL_HIGH + MILL_SPREAD * random.nextFloat();
                    float sa = MILL_LOW + MILL_SPREAD * random.nextFloat(), sb = MILL_HIGH + MILL_SPREAD * random.nextFloat();
                    int n = mill(p, ra, rb, sa, sb, mirrored, area > MILL_ROOMS_AREA, false);
                    if (n >= 0) return n;
                }
            }
            for (int i = 0; i < TRIES; i++) {
                int n = cut(p, CUT_BASE + CUT_SPREAD * random.nextFloat());
                if (n >= 0) return n;
            }
            return room(p);
        }

        private boolean roomFits(Size p) {
            float side = minSide / ROUNDING;
            return p.w >= side && p.h >= side && p.w <= look.roomAspect * p.h && p.h <= look.roomAspect * p.w;
        }

        /** True if part n fits at size p: else its parent could not hold it at its base shares. */
        private boolean fits(int n, Size p) {
            float a = p.w / p.h;
            return p.w >= fitW[n] && p.h >= fitH[n] && a >= fitLo[n] && a <= fitHi[n];
        }

        private int cut(Size p, float b) {
            boolean across = p.w >= CUT_X_ASPECT * p.h;
            float least = clampCut(b - AMPLITUDE), most = clampCut(b + AMPLITUDE);
            Size first = across ? p.scale(b, least, 1, 1) : p.scale(1, 1, b, least);
            Size second = across ? p.scale(1 - b, 1 - most, 1, 1) : p.scale(1, 1, 1 - b, 1 - most);
            int partsBefore = parts, sharesBefore = shares, roomsBefore = rooms, wallsBefore = walls;
            int n = node(across ? CUT_X : CUT_Y, 2);
            share[n] = newShare(b, least, most, SPEED + SPEED_SPREAD * random.nextFloat(), (float) (2 * Math.PI * random.nextFloat()));
            index[n] = walls++;
            int one = part(first), two = part(second);
            child[n][0] = one;
            child[n][1] = two;
            // The cut's fit, worked out along the axis it divides ("along") and the other ("across"):
            // l, u bound along / across; a, b are the least along and across.
            float l0 = across ? fitLo[one] : 1 / fitHi[one], u0 = across ? fitHi[one] : 1 / fitLo[one];
            float l1 = across ? fitLo[two] : 1 / fitHi[two], u1 = across ? fitHi[two] : 1 / fitLo[two];
            float a0 = across ? fitW[one] : fitH[one], b0 = across ? fitH[one] : fitW[one];
            float a1 = across ? fitW[two] : fitH[two], b1 = across ? fitH[two] : fitW[two];
            float l = Math.max(l0 + l1, Math.max(l0 / most, l1 / (1 - least)));
            float u = Math.min(u0 + u1, Math.min(u0 / least, u1 / (1 - most)));
            float alongLeast = Math.max(Math.max(a0 + a1, Math.max(a0 / most, a1 / (1 - least))),
                    Math.max(a0 / (1 - l1 / l), a1 / (1 - l0 / l)));
            float acrossLeast = Math.max(Math.max(b0, b1), Math.max(a0 / u0, a1 / u1));
            fitLo[n] = across ? l : 1 / u;
            fitHi[n] = across ? u : 1 / l;
            fitW[n] = across ? alongLeast : acrossLeast;
            fitH[n] = across ? acrossLeast : alongLeast;
            if (!fits(n, p)) return undo(partsBefore, sharesBefore, roomsBefore, wallsBefore);
            return n;
        }

        /**
         * A windmill on part p with these base shares, or -1 if it does not fit (the whole plan's windmill is
         * always made). Its 4 outer parts are rooms, or grow on if {@code grows}; its centre is always a room.
         */
        private int mill(Size p, float ra, float rb, float sa, float sb, boolean mirrored, boolean grows, boolean whole) {
            float a = ra - AMPLITUDE, b = rb - AMPLITUDE, c = ra + AMPLITUDE, d = rb + AMPLITUDE;
            float x1 = millLow(ra, rb), x1Lo = millLow(a, b), x1Hi = millLow(c, d);
            float x2 = millHigh(ra, rb), x2Lo = millHigh(a, b), x2Hi = millHigh(c, d);
            float gx = x2 - x1, gxLo = Math.max(MILL_GAP, edge(b) - edge(c));
            a = sa - AMPLITUDE;
            b = sb - AMPLITUDE;
            c = sa + AMPLITUDE;
            d = sb + AMPLITUDE;
            float y1 = millLow(sa, sb), y1Lo = millLow(a, b), y1Hi = millLow(c, d);
            float y2 = millHigh(sa, sb), y2Lo = millHigh(a, b), y2Hi = millHigh(c, d);
            float gy = y2 - y1, gyLo = Math.max(MILL_GAP, edge(b) - edge(c));
            Size[] kids = {
                    p.scale(x2, x2Lo, y1, y1Lo),                    // top
                    p.scale(1 - x2, 1 - x2Hi, y2, y2Lo),            // right
                    p.scale(1 - x1, 1 - x1Hi, 1 - y2, 1 - y2Hi),    // bottom
                    p.scale(x1, x1Lo, 1 - y1, 1 - y1Hi),            // left
                    p.scale(gx, gxLo, gy, gyLo)};                   // centre
            int partsBefore = parts, sharesBefore = shares, roomsBefore = rooms, wallsBefore = walls;
            int n = node(mirrored ? MILL_MIRRORED : MILL, 5);
            float turn = SPEED + SPEED_SPREAD * random.nextFloat(), at = (float) (2 * Math.PI * random.nextFloat());
            float against = p.w >= p.h ? 0 : (float) Math.PI;
            share[n] = newShare(ra, x1Lo, x1Hi, turn, at + jitter());
            newShare(rb, x2Lo, x2Hi, turn, at + jitter());
            newShare(sa, y1Lo, y1Hi, turn, at + against + jitter());
            newShare(sb, y2Lo, y2Hi, turn, at + against + jitter());
            index[n] = walls;
            walls += 4;
            for (int i = 0; i < 4; i++) child[n][i] = grows ? part(kids[i]) : room(kids[i]);
            child[n][4] = room(kids[4]);
            millFit(n, p.w / p.h, whole ? p.h : MILL_SHRINK * p.h);
            if (!whole && !fits(n, p)) return undo(partsBefore, sharesBefore, roomsBefore, wallsBefore);
            return n;
        }

        /** Drops every part, share, room and wall made since the counts were these; returns -1. */
        private int undo(int partsBefore, int sharesBefore, int roomsBefore, int wallsBefore) {
            parts = partsBefore;
            shares = sharesBefore;
            rooms = roomsBefore;
            walls = wallsBefore;
            return -1;
        }

        /**
         * Windmill n's fit. Its least height is {@code h}, or more if y-walls that give all 5 parts their least
         * heights need more. Its widths over heights are the run round {@code a} at which, at that height (taller
         * only makes it easier) and for every pair of y-walls the shares can make, x-walls exist that keep all 5
         * parts in their fits; found in steps of 2 %, a step inside the last that held, for the y-walls between
         * the samples.
         */
        private void millFit(int n, float a, float h) {
            float low = 0, high = 64;
            for (int i = 0; i < 40; i++) {
                float mid = (low + high) / 2;
                if (millYFits(n, mid)) high = mid; else low = mid;
            }
            float least = Math.max(h, high), step = 1.02f;
            float from = a, to = a;
            if (millXFits(n, a, least)) {
                while (from > 0.05f && millXFits(n, from / step, least)) from /= step;
                while (to < 20f && millXFits(n, to * step, least)) to *= step;
                from = Math.min(a, from * step);
                to = Math.max(a, to / step);
            } else {
                from = Float.MAX_VALUE;
                to = 0;
            }
            fitLo[n] = from;
            fitHi[n] = to;
            fitH[n] = least;
            fitW[n] = from * least;
        }

        /** True if windmill n, h plan heights tall, has y-walls that give all 5 parts their least heights. */
        private boolean millYFits(int n, float h) {
            int s = share[n] + 2;
            int[] c = child[n];
            float lowLo = Math.max(lo[s], fitH[c[0]] / h), lowHi = Math.min(hi[s], 1 - fitH[c[3]] / h);
            float highLo = Math.max(lo[s + 1], fitH[c[1]] / h), highHi = Math.min(hi[s + 1], 1 - fitH[c[2]] / h);
            float gap = Math.max(millGap(s), fitH[c[4]] / h);
            return lowLo <= lowHi && highLo <= highHi && lowLo + gap <= highHi;
        }

        /** True if windmill n, h tall and a times as wide, keeps its parts in their fits whatever its y-walls. */
        private boolean millXFits(int n, float a, float h) {
            int s = share[n];
            int[] c = child[n];
            float w = a * h;
            int samples = 8;
            for (int i = 0; i <= samples; i++) {
                for (int j = 0; j <= samples; j++) {
                    float sa = base[s + 2] + AMPLITUDE * (2f * i / samples - 1), sb = base[s + 3] + AMPLITUDE * (2f * j / samples - 1);
                    float s1 = millLow(sa, sb), s2 = millHigh(sa, sb);
                    float r1 = millX(n, false, w, h, 1, s1, s2, base[s], base[s + 1]);
                    float r2 = millX(n, true, w, h, 1, s1, s2, base[s], base[s + 1]);
                    if (!inFit(c[0], r2 * w, s1 * h) || !inFit(c[1], (1 - r2) * w, s2 * h)
                            || !inFit(c[2], (1 - r1) * w, (1 - s2) * h) || !inFit(c[3], r1 * w, (1 - s1) * h)
                            || !inFit(c[4], (r2 - r1) * w, (s2 - s1) * h)) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** True if part n keeps its least width and its widths over heights at w by h (its height is checked apart). */
        private boolean inFit(int n, float w, float h) {
            float a = w / h, slack = 1e-4f;
            return w >= fitW[n] * (1 - slack) && a >= fitLo[n] * (1 - slack) && a <= fitHi[n] * (1 + slack);
        }

        private int room(Size p) {
            int n = node(ROOM, 0);
            int k = rooms++;
            index[n] = k;
            fitLo[n] = 1 / roomFit;
            fitHi[n] = roomFit;
            fitW[n] = minSide;
            fitH[n] = minSide;
            if (k == minShareW.length) {
                minShareW = Arrays.copyOf(minShareW, 2 * k);
                minShareH = Arrays.copyOf(minShareH, 2 * k);
            }
            minShareW[k] = p.wLo / p.planW;
            minShareH[k] = p.hLo;
            return n;
        }

        private int node(int type, int children) {
            int n = parts++;
            if (n == kind.length) {
                kind = Arrays.copyOf(kind, 2 * n);
                share = Arrays.copyOf(share, 2 * n);
                index = Arrays.copyOf(index, 2 * n);
                child = Arrays.copyOf(child, 2 * n);
                fitLo = Arrays.copyOf(fitLo, 2 * n);
                fitHi = Arrays.copyOf(fitHi, 2 * n);
                fitW = Arrays.copyOf(fitW, 2 * n);
                fitH = Arrays.copyOf(fitH, 2 * n);
            }
            kind[n] = type;
            child[n] = new int[children];
            return n;
        }

        private float jitter() {
            return MILL_JITTER * (2 * random.nextFloat() - 1);
        }

        /** A new share with this base, reaching from least to most after the clamps, swinging at this speed and phase. */
        private int newShare(float b, float least, float most, float swing, float from) {
            int s = shares++;
            if (s == base.length) {
                base = Arrays.copyOf(base, 2 * s);
                speed = Arrays.copyOf(speed, 2 * s);
                phase = Arrays.copyOf(phase, 2 * s);
                lo = Arrays.copyOf(lo, 2 * s);
                hi = Arrays.copyOf(hi, 2 * s);
            }
            base[s] = b;
            lo[s] = least;
            hi[s] = most;
            speed[s] = swing;
            phase[s] = from;
            return s;
        }
    }

    /**
     * A part's size in plan heights: at the base shares, and its least with every share at its worst.
     * {@code planW} is the whole plan's width.
     */
    private static final class Size {
        final float w, h, wLo, hLo, planW;

        /** The whole plan. */
        Size(float w, float h) {
            this(w, h, w, h, w);
        }

        private Size(float w, float h, float wLo, float hLo, float planW) {
            this.w = w;
            this.h = h;
            this.wLo = wLo;
            this.hLo = hLo;
            this.planW = planW;
        }

        /** This part's child that gets these shares of its width and height, at the base and at the worst. */
        Size scale(float sw, float swLo, float sh, float shLo) {
            return new Size(w * sw, h * sh, wLo * swLo, hLo * shLo, planW);
        }
    }
}
