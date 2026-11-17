package io.github.pbanddev.tatami;

/**
 * Turns a {@link Frame} into coloured triangles for OpenGL: x, y in plan units, then r, g, b, a.
 * Fills, then walls, then doors ({@link DoorShapes}), then fixtures ({@link FixtureShapes}), so later
 * shapes cover earlier ones. Sizes and colours come from the {@link Look}.
 *
 * <p>Yojōhan ({@link #build(Frame, float)}): each room a fill and 4 wall quads round it. Nagaya
 * ({@link #build(Frame, View, float[], Wash)}): each cell a fill, cut into pieces whose corners take the
 * {@link Wash}, then a quad per wall piece. Fills use {@link #color}, which the wash reaches; walls and strokes
 * use {@link #ink}, which it never does.
 *
 * <p>The arrays are sized from the frame's capacities. A shape that does not fit is left out and sets
 * {@link #clipped}.
 */
public final class SceneMesh {
    public static final int FLOATS_PER_VERTEX = 6;

    /** A room's fill and its 4 wall quads. */
    private static final int VERTICES_PER_ROOM = 6 + 4 * 6;
    /**
     * Nagaya's fills reach this many screen px past their cells. Cells of one room meet with no wall over the seam,
     * and their pieces' corners do not line up across it, so without it a rasterizer leaves hairline cracks there.
     * Past a wall the bleed is under the wall.
     */
    static final float BLEED_PX = 0.5f;

    public final float[] data;
    public int floats;
    /** True when the last build had more triangles than {@link #data} holds. */
    public boolean clipped;
    private final Look look;
    private float r, g, b;
    /** Set while a Nagaya build runs; null otherwise. */
    private Wash wash;
    private boolean washing;
    private float[] matrix;
    private float screenW, screenH;
    private final float[] washed = new float[3];
    /** Washed corner colours of 2 rows of pieces; they grow if a cell needs more. */
    private float[] upper = new float[3 * 16], lower = new float[3 * 16];

    public SceneMesh(int rooms, Look look) {
        this(new Frame(rooms), look);
    }

    public SceneMesh(Frame f, Look look) {
        this.look = look;
        int doors = f.doorWall.length * DoorShapes.MAX_VERTICES;
        int fixtures = look.fixtures.share > 0 ? f.fixtureKind.length * FixtureShapes.MAX_VERTICES : 0;
        int vertices = look.plan.kind == Look.Plan.Kind.TREE
                ? f.cellX.length * VERTICES_PER_ROOM + doors + fixtures
                : f.fillCapacity * 6 + f.wallX0.length * 6 + doors + fixtures;
        data = new float[FLOATS_PER_VERTEX * vertices];
    }

    /** Yojōhan: each room's fill, then its outline as walls. */
    public void build(Frame f, float screenHeight) {
        floats = 0;
        clipped = false;
        int[] deck = look.palette.deck;
        for (int i = 0; i < f.roomCount; i++) {
            color(deck[f.roomColor[i]]);
            rect(f.roomX[i], f.roomY[i], f.roomW[i], f.roomH[i]);
        }
        float wall = screenHeight * look.walls.widthH;
        ink(look.walls.color);
        for (int i = 0; i < f.roomCount; i++) outline(f.roomX[i], f.roomY[i], f.roomW[i], f.roomH[i], wall);
        for (int d = 0; d < f.doorCount; d++) DoorShapes.add(this, f, d, screenHeight, look);
        for (int k = 0; k < f.fixtureCount; k++) FixtureShapes.add(this, f, k, screenHeight, look);
    }

    /**
     * Nagaya: cell fills, washed by {@code wash} at the screen positions {@code matrix} gives them (null: not
     * washed, and not cut), then a quad per wall piece, half a wall longer at each end, then doors.
     */
    public void build(Frame f, View view, float[] matrix, Wash wash) {
        floats = 0;
        clipped = false;
        this.wash = wash;
        this.matrix = matrix;
        screenW = view.w;
        screenH = view.h;
        try {
            int[] deck = look.palette.deck;
            float side = view.h * look.post.washPieceH, bleed = BLEED_PX / view.zoom;
            for (int c = 0; c < f.cellCount; c++) {
                color(deck[f.roomColor[f.cellRoom[c]]]);
                fill(f.cellX[c] - bleed, f.cellY[c] - bleed, f.cellW[c] + 2 * bleed, f.cellH[c] + 2 * bleed, side);
            }
            float half = view.h * look.walls.widthH / 2;
            ink(look.walls.color);
            for (int i = 0; i < f.wallCount; i++) {
                rect(f.wallX0[i] - half, f.wallY0[i] - half,
                        f.wallX1[i] - f.wallX0[i] + 2 * half, f.wallY1[i] - f.wallY0[i] + 2 * half);
            }
            for (int d = 0; d < f.doorCount; d++) DoorShapes.add(this, f, d, view.h, look);
            if (look.fixtures.share > 0) {
                for (int k = 0; k < f.fixtureCount; k++) FixtureShapes.add(this, f, k, view.h, look);
            }
        } finally {
            this.wash = null;
            this.matrix = null;
            washing = false;
        }
    }

    /** How many pieces a w by h fill is cut into for the wash, with pieces at most {@code side} each way. */
    static int pieces(float w, float h, float side) {
        return Math.max(1, (int) Math.ceil(w / side)) * Math.max(1, (int) Math.ceil(h / side));
    }

    /** The colour of the fills that follow: the wash reaches it, if there is one. */
    void color(int argb) {
        set(argb);
        washing = wash != null;
    }

    /** The colour of the walls and strokes that follow: never washed. */
    void ink(int argb) {
        set(argb);
        washing = false;
    }

    private void set(int argb) {
        r = ((argb >> 16) & 0xFF) / 255f;
        g = ((argb >> 8) & 0xFF) / 255f;
        b = (argb & 0xFF) / 255f;
    }

    /** A box outline of the given width, centred on the box's edges. */
    void outline(float x, float y, float w, float h, float width) {
        float half = width / 2;
        rect(x - half, y - half, w + width, width);
        rect(x - half, y + h - half, w + width, width);
        rect(x - half, y - half, width, h + width);
        rect(x + w - half, y - half, width, h + width);
    }

    /** A straight line of the given width with flat ends at the end points. Always 2 triangles. */
    void line(float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float length = (float) Math.hypot(dx, dy);
        if (length == 0) {
            triangle(x0, y0, x0, y0, x0, y0);
            triangle(x0, y0, x0, y0, x0, y0);
            return;
        }
        float nx = -dy / length * width / 2, ny = dx / length * width / 2;
        triangle(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny);
        triangle(x0 + nx, y0 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny);
    }

    void rect(float x, float y, float w, float h) {
        triangle(x, y, x + w, y, x + w, y + h);
        triangle(x, y, x + w, y + h, x, y + h);
    }

    void triangle(float x0, float y0, float x1, float y1, float x2, float y2) {
        if (floats + 3 * FLOATS_PER_VERTEX > data.length) {
            clipped = true;
            return;
        }
        vertex(x0, y0);
        vertex(x1, y1);
        vertex(x2, y2);
    }

    /** A fill: one box, or with a wash, pieces at most {@code side} each way whose corners are washed once. */
    private void fill(float x, float y, float w, float h, float side) {
        if (!washing) {
            rect(x, y, w, h);
            return;
        }
        int nx = Math.max(1, (int) Math.ceil(w / side)), ny = Math.max(1, (int) Math.ceil(h / side));
        if (upper.length < 3 * (nx + 1)) {
            upper = new float[3 * (nx + 1)];
            lower = new float[3 * (nx + 1)];
        }
        washRow(upper, x, y, w, nx);
        float top = y;
        for (int j = 1; j <= ny; j++) {
            float bottom = j == ny ? y + h : y + h * j / ny;
            washRow(lower, x, bottom, w, nx);
            float left = x;
            for (int i = 0; i < nx; i++) {
                float right = i + 1 == nx ? x + w : x + w * (i + 1) / nx;
                if (floats + 6 * FLOATS_PER_VERTEX > data.length) {
                    clipped = true;
                    return;
                }
                put(left, top, upper, i);
                put(right, top, upper, i + 1);
                put(right, bottom, lower, i + 1);
                put(left, top, upper, i);
                put(right, bottom, lower, i + 1);
                put(left, bottom, lower, i);
                left = right;
            }
            float[] swap = upper;
            upper = lower;
            lower = swap;
            top = bottom;
        }
    }

    /** The washed colours of the nx + 1 corners along y, from x to x + w. */
    private void washRow(float[] row, float x, float y, float w, int nx) {
        for (int i = 0; i <= nx; i++) {
            washAt(i == nx ? x + w : x + w * i / nx, y);
            row[3 * i] = washed[0];
            row[3 * i + 1] = washed[1];
            row[3 * i + 2] = washed[2];
        }
    }

    /** The current colour, washed where plan point (x, y) lands on screen, into {@link #washed}. */
    private void washAt(float x, float y) {
        float[] m = matrix;
        float cx = m[0] * x + m[4] * y + m[12], cy = m[1] * x + m[5] * y + m[13];
        washed[0] = r;
        washed[1] = g;
        washed[2] = b;
        wash.apply((cx + 1) / 2 * screenW, (1 - cy) / 2 * screenH, screenW, screenH, washed);
    }

    private void vertex(float x, float y) {
        if (washing) {
            washAt(x, y);
            put(x, y, washed[0], washed[1], washed[2]);
        } else {
            put(x, y, r, g, b);
        }
    }

    private void put(float x, float y, float[] row, int i) {
        put(x, y, row[3 * i], row[3 * i + 1], row[3 * i + 2]);
    }

    private void put(float x, float y, float red, float green, float blue) {
        float[] d = data;
        int i = floats;
        d[i] = x;
        d[i + 1] = y;
        d[i + 2] = red;
        d[i + 3] = green;
        d[i + 4] = blue;
        d[i + 5] = 1f;
        floats = i + FLOATS_PER_VERTEX;
    }
}
