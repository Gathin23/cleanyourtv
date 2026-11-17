package io.github.pbanddev.tatami;

/**
 * One frame of the floor plan, from the time to triangles and a camera matrix. The renderer on the TV
 * and the desktop tools (tools/PlanShot.java, tools/PlanStats.java) all run this, so they draw the same
 * frame. Pure Java. Build one through {@link Scenes} in the app, so a look that fails gives way to Yojōhan.
 *
 * <p>The steps, in order: the look's {@link CameraCurve} sets the {@link View} and the matrix, its
 * {@link Layout} lays out rooms, cells and walls, the paints dealt once go on, its {@link DoorPlacer} and
 * {@link Fixtures} add theirs, and {@link SceneMesh} makes the triangles.
 *
 * <p>Yojōhan: {@link FloorPlan}, {@link RoomColours#deal}, {@link Doors} and {@link CameraMatrix}, as they were.
 * Nagaya: {@link TilePlan} with {@link SkylineBuilder}, {@link RoomColours#shuffle}, {@link NagayaDoors},
 * {@link NagayaCamera} and the {@link Wash}.
 */
public final class Scene {
    public final Look look;
    final Layout layout;
    public final Frame frame;
    public final SceneMesh mesh;
    /** What the camera sees this frame. */
    public final View view = new View();
    /** Plan units to clip space, column-major 4x4. */
    public final float[] matrix = new float[16];
    private final int[] paints;
    private final CameraCurve camera;
    private final DoorPlacer doors;
    /** Null when the look has no fixtures. */
    private final Fixtures fixtures;
    /** Null when the look has no wash. */
    private final Wash wash;

    public Scene(Look look) {
        this(look, new SkylineBuilder());
    }

    /** A scene whose TILE plan comes from {@code builder} (the tools try builders this way); TREE ignores it. */
    Scene(Look look, TileBuilder builder) {
        this.look = look;
        if (look.plan.kind == Look.Plan.Kind.TREE) {
            FloorPlan tree = new FloorPlan(look);
            layout = new TreeLayout(tree, look);
            paints = look.palette.deal == Look.Palette.Deal.SHUFFLE
                    ? RoomColours.shuffle(tree.rooms, look) : RoomColours.deal(tree, look);
            camera = new TreeCamera(look);
            Doors placer = new Doors(look);
            doors = (f, t, v) -> placer.place(f, t, v.h);
        } else {
            layout = new TilePlan(look, builder);
            paints = RoomColours.shuffle(layout.rooms(), look);
            camera = new NagayaCamera(look);
            doors = new NagayaDoors(look);
        }
        frame = layout.newFrame();
        mesh = new SceneMesh(frame, look);
        fixtures = look.fixtures.share > 0 ? new Fixtures(look) : null;
        wash = look.post.wash ? Wash.NAGAYA : null;
    }

    /** Fills {@link #frame}, {@link #mesh} and {@link #matrix} for time {@code t} on a w by h screen, washed. */
    public void build(double t, int w, int h, boolean showDoors, boolean showFixtures, boolean moveCamera) {
        build(t, w, h, showDoors, showFixtures, moveCamera, true);
    }

    /** As above; {@code washOn} false leaves Nagaya's fills unwashed (the vignette switch). Yojōhan has no wash. */
    public void build(double t, int w, int h, boolean showDoors, boolean showFixtures, boolean moveCamera,
            boolean washOn) {
        camera.at(t, w, h, moveCamera, view, matrix);
        fill(t, showDoors, showFixtures, washOn ? wash : null);
    }

    /** Everything after the camera, through the {@link #view} and {@link #matrix} as they are. */
    void fill(double t, boolean showDoors, boolean showFixtures, Wash wash) {
        layout.layout(t, view, frame);
        for (int k = 0; k < frame.roomCount; k++) frame.roomColor[k] = paints[frame.roomId[k]];
        if (showDoors) doors.place(frame, t, view); else frame.doorCount = 0;
        if (showFixtures && fixtures != null) fixtures.place(frame, view.h); else frame.fixtureCount = 0;
        if (look.plan.kind == Look.Plan.Kind.TREE) mesh.build(frame, view.h); else mesh.build(frame, view, matrix, wash);
    }

    /** How far past each screen edge the plan must be drawn, in px: the blur's strip, padded to whole GPU tiles. */
    static float margin(Look look) {
        return Lens.margin(look.post) + 16;
    }

    /** Yojōhan's rooms: {@link FloorPlan} as it is, sized as the tilted camera needs, one cell per room. */
    private static final class TreeLayout implements Layout {
        private final FloorPlan tree;
        private final float angleDeg;

        TreeLayout(FloorPlan tree, Look look) {
            this.tree = tree;
            angleDeg = look.camera.angleDeg;
        }

        @Override
        public int rooms() {
            return tree.rooms;
        }

        @Override
        public Frame newFrame() {
            Frame f = new Frame(tree.rooms);
            for (int k = 0; k < f.roomId.length; k++) f.roomId[k] = k;
            for (int k = 0; k < f.wallId.length; k++) {
                f.wallId[k] = k;
                f.wallRoomA[k] = -1;
                f.wallRoomB[k] = -1;
                f.wallMinLength[k] = Float.NaN;
            }
            return f;
        }

        @Override
        public void layout(double t, View v, Frame f) {
            float pw = Camera.planWidth(v.w, v.h, angleDeg), ph = Camera.planHeight(v.w, v.h, angleDeg);
            tree.layout(t, -pw / 2, -ph / 2, pw, ph, f);
            for (int k = 0; k < f.roomCount; k++) {
                f.cellX[k] = f.roomX[k];
                f.cellY[k] = f.roomY[k];
                f.cellW[k] = f.roomW[k];
                f.cellH[k] = f.roomH[k];
                f.cellRoom[k] = k;
            }
            f.cellCount = f.roomCount;
        }
    }

    /** Yojōhan's camera: {@link Camera} and {@link CameraMatrix} as they are; the view follows their pan. */
    private static final class TreeCamera implements CameraCurve {
        private final float angleDeg, margin;
        private final double cos, sin;

        TreeCamera(Look look) {
            angleDeg = look.camera.angleDeg;
            margin = margin(look);
            cos = Math.cos(Math.toRadians(angleDeg));
            sin = Math.sin(Math.toRadians(angleDeg));
        }

        @Override
        public void at(double t, int w, int h, boolean moving, View view, float[] matrix) {
            float zoom = Camera.zoom(t, moving);
            double px = Camera.panX(t, w, moving), py = Camera.panY(t, h, moving);
            // the screen centre, back through the pan, the turn and the zoom
            view.set(w, h, angleDeg, zoom, (-cos * px - sin * py) / zoom, (sin * px - cos * py) / zoom, margin);
            CameraMatrix.build(t, w, h, moving, angleDeg, matrix);
        }
    }
}
