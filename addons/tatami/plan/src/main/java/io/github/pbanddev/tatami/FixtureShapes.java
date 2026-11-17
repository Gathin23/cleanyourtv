package io.github.pbanddev.tatami;

/**
 * Draws one fixture of a {@link Frame} into the {@link SceneMesh}: a toilet, a tub or stairs. Every size
 * here is in fixture units
 * ({@link Look.Fixtures#unitH}, 1 px at 1080p), so a fixture never scales with its room.
 *
 * <p>Each shape is drawn unturned with its box's top-left corner at 0, 0: the toilet with its tank up, the
 * tub lying along x, the stairs running along +x from the free end to the arrow, with the free side at the
 * top. Then its vertices are scaled to plan units and turned clockwise on screen, a quarter turn per
 * {@link Frame#fixtureTurn}, about the centre of the fixture's box.
 */
final class FixtureShapes {
    // toilet: a tank bar with an oval bowl under it, one outline round both
    private static final float TANK_W = 40f, TANK_D = 15.5f, BOWL_W = 35f, BOWL_D = 52f;
    /** From the tank's back edge to the bowl's centre. */
    private static final float BOWL_Y = 34f;
    private static final int BOWL_SEGMENTS = 24;

    // tub: a rounded rim round a rounded basin
    private static final float TUB_W = 117.5f, TUB_D = 67.6f, TUB_RADIUS = 10f;
    /** From the tub's edge to the basin's edge. */
    private static final float RIM = 7f;
    private static final float BASIN_RADIUS = 4f;
    private static final int CORNER_SEGMENTS = 4;

    // stairs: u along the run from the free end, v across from the free side
    private static final float RUN = 211.7f, ACROSS = 95.1f;
    private static final float FREE_SIDE_LINE = 2.6f, FREE_END_LINE = 3.5f, STAIRS_LINE = 3f;
    private static final float RAIL_NEAR = 6.6f, RAIL_FAR = 91f, SHAFT = 50.3f;
    /** Tread k (1 to 10) is at u = FIRST_TREAD + k * TREAD_STEP: 11 equal cells from the free-end line. */
    private static final float FIRST_TREAD = FREE_END_LINE / 2, TREAD_STEP = 19.2f;
    private static final int TREADS = 10;
    private static final float DOT = 7f, CHEVRON_BACK = 13.5f, CHEVRON_ASIDE = 12f;
    private static final float CHEVRON_ARM = (float) Math.hypot(CHEVRON_BACK, CHEVRON_ASIDE);
    private static final int DOT_SEGMENTS = 12;

    /** A unit circle in 48 steps; the shapes above take every 2nd, 3rd or 4th. */
    private static final int STEPS = 48;
    private static final float[] COS = new float[STEPS], SIN = new float[STEPS];

    static {
        for (int i = 0; i < STEPS; i++) {
            COS[i] = (float) Math.cos(2 * Math.PI * i / STEPS);
            SIN[i] = (float) Math.sin(2 * Math.PI * i / STEPS);
        }
    }

    private static final int TOILET_VERTICES = 2 * (6 + 3 * BOWL_SEGMENTS);
    private static final int TUB_VERTICES = 4 * 3 * 4 * (CORNER_SEGMENTS + 1);
    /** Fill and 2 edge bars; rails, treads, shaft and 2 chevron arms; the dot; the chevron's point. */
    private static final int STAIRS_VERTICES = 3 * 6 + (2 + TREADS + 1 + 2) * 6 + 3 * DOT_SEGMENTS + 6;
    /** The most vertices one fixture adds. */
    static final int MAX_VERTICES = Math.max(TOILET_VERTICES, Math.max(TUB_VERTICES, STAIRS_VERTICES));

    private FixtureShapes() {}

    /** The kind's width, unturned, in units. */
    static float width(int kind) {
        switch (kind) {
            case Fixtures.TUB: return TUB_W;
            case Fixtures.TOILET: return TANK_W;
            case Fixtures.STAIRS: return RUN;
            default: return 0f;
        }
    }

    /** The kind's depth, unturned, in units: from the toilet's back to its front, the stairs' free side to its wall. */
    static float depth(int kind) {
        switch (kind) {
            case Fixtures.TUB: return TUB_D;
            case Fixtures.TOILET: return BOWL_Y + BOWL_D / 2;
            case Fixtures.STAIRS: return ACROSS;
            default: return 0f;
        }
    }

    static void add(SceneMesh m, Frame f, int k, float screenHeight, Look look) {
        int kind = f.fixtureKind[k], start = m.floats;
        switch (kind) {
            case Fixtures.TUB: tub(m, look); break;
            case Fixtures.TOILET: toilet(m, look); break;
            case Fixtures.STAIRS: stairs(m, look); break;
            default: return;
        }
        float unit = screenHeight * look.fixtures.unitH;
        int turn = f.fixtureTurn[k] & 3;
        float cos = turn == 0 ? unit : turn == 2 ? -unit : 0f, sin = turn == 1 ? unit : turn == 3 ? -unit : 0f;
        float halfW = width(kind) / 2, halfD = depth(kind) / 2;
        float cx = f.fixtureX[k] + f.fixtureW[k] / 2, cy = f.fixtureY[k] + f.fixtureH[k] / 2;
        float[] d = m.data;
        for (int i = start; i < m.floats; i += SceneMesh.FLOATS_PER_VERTEX) {
            float a = d[i] - halfW, b = d[i + 1] - halfD;
            d[i] = cx + cos * a - sin * b;
            d[i + 1] = cy + sin * a + cos * b;
        }
    }

    /** Both black shapes, then both fills one stroke in, so no line shows where the bowl meets the tank. */
    private static void toilet(SceneMesh m, Look look) {
        float s = look.fixtures.line, cx = TANK_W / 2;
        m.color(look.palette.line);
        m.rect(0, 0, TANK_W, TANK_D);
        oval(m, cx, BOWL_Y, BOWL_W / 2, BOWL_D / 2, STEPS / BOWL_SEGMENTS);
        m.color(look.palette.fixture);
        m.rect(s, s, TANK_W - 2 * s, TANK_D - 2 * s);
        oval(m, cx, BOWL_Y, BOWL_W / 2 - s, BOWL_D / 2 - s, STEPS / BOWL_SEGMENTS);
    }

    /** 4 rounded boxes on one centre: black, fill one stroke in, black at the basin, fill one stroke in. */
    private static void tub(SceneMesh m, Look look) {
        float s = look.fixtures.line;
        m.color(look.palette.line);
        roundBox(m, 0, TUB_RADIUS);
        m.color(look.palette.fixture);
        roundBox(m, s, TUB_RADIUS - s);
        m.color(look.palette.line);
        roundBox(m, RIM, BASIN_RADIUS);
        m.color(look.palette.fixture);
        roundBox(m, RIM + s, Math.max(0f, BASIN_RADIUS - s));
    }

    private static void stairs(SceneMesh m, Look look) {
        m.color(look.palette.stairs);
        m.rect(0, 0, RUN, ACROSS);
        m.color(look.palette.line);
        m.rect(0, 0, RUN, FREE_SIDE_LINE);
        m.rect(0, 0, FREE_END_LINE, ACROSS);
        m.line(0, RAIL_NEAR, RUN, RAIL_NEAR, STAIRS_LINE);
        m.line(0, RAIL_FAR, RUN, RAIL_FAR, STAIRS_LINE);
        for (int k = 1; k <= TREADS; k++) {
            float u = FIRST_TREAD + k * TREAD_STEP;
            m.line(u, RAIL_NEAR, u, RAIL_FAR, STAIRS_LINE);
        }
        float tip = FIRST_TREAD + TREADS * TREAD_STEP;
        m.line(FIRST_TREAD, SHAFT, tip, SHAFT, STAIRS_LINE);
        oval(m, FIRST_TREAD, SHAFT, DOT / 2, DOT / 2, STEPS / DOT_SEGMENTS);
        m.line(tip - CHEVRON_BACK, SHAFT - CHEVRON_ASIDE, tip, SHAFT, STAIRS_LINE);
        m.line(tip - CHEVRON_BACK, SHAFT + CHEVRON_ASIDE, tip, SHAFT, STAIRS_LINE);
        // fill the point between the two flat arm ends (a mitre)
        float half = STAIRS_LINE / 2;
        float ox = tip + half * CHEVRON_ASIDE / CHEVRON_ARM, oy = half * CHEVRON_BACK / CHEVRON_ARM;
        float point = tip + half * CHEVRON_ARM / CHEVRON_ASIDE;
        m.triangle(tip, SHAFT, ox, SHAFT - oy, point, SHAFT);
        m.triangle(tip, SHAFT, point, SHAFT, ox, SHAFT + oy);
    }

    /** A filled oval as a fan, every {@code step}-th step of the circle. */
    private static void oval(SceneMesh m, float cx, float cy, float rx, float ry, int step) {
        for (int i = 0; i < STEPS; i += step) {
            int j = (i + step) % STEPS;
            m.triangle(cx, cy, cx + rx * COS[i], cy + ry * SIN[i], cx + rx * COS[j], cy + ry * SIN[j]);
        }
    }

    /** The tub's box inset by {@code in} on every side, with corners of radius {@code r}, as a fan from its centre. */
    private static void roundBox(SceneMesh m, float in, float r) {
        float x0 = in + r, y0 = in + r, x1 = TUB_W - in - r, y1 = TUB_D - in - r;
        float cx = TUB_W / 2, cy = TUB_D / 2;
        int points = 4 * (CORNER_SEGMENTS + 1);
        float px = 0, py = 0;
        for (int p = 0; p <= points; p++) {
            // corners in turn from the top-left, clockwise on screen; each arc a quarter of the circle
            int corner = (p % points) / (CORNER_SEGMENTS + 1), along = (p % points) % (CORNER_SEGMENTS + 1);
            int i = (STEPS / 2 + corner * STEPS / 4 + along * STEPS / 4 / CORNER_SEGMENTS) % STEPS;
            float x = (corner == 0 || corner == 3 ? x0 : x1) + r * COS[i];
            float y = (corner < 2 ? y0 : y1) + r * SIN[i];
            if (p > 0) m.triangle(cx, cy, px, py, x, y);
            px = x;
            py = y;
        }
    }
}
