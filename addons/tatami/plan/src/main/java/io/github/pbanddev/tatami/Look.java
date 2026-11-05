package io.github.pbanddev.tatami;

/**
 * Every look value in one place: the cut of the plan, its pace, paints, walls, camera, doors, fixtures and the
 * lens. One instance per look: {@link #YOJOHAN} (4 mats round a half mat, cut as a tree of rooms) and
 * {@link #NAGAYA} (rows of small rooms, one tile repeated).
 *
 * <p>Yojōhan's motion is not here: its wall speeds, door speed and camera pan and zoom stay in
 * {@link FloorPlan}, {@link io.github.pbanddev.tatami.Doors} and {@link io.github.pbanddev.tatami.Camera}.
 * Nagaya's motion is here, in {@link Rhythm}, {@link Motion}, {@link Camera} and {@link Doors}.
 *
 * <p>Sizes whose names end in {@code H} are a share of the screen height and become plan units as
 * {@code screenHeight * size}. The camera's zoom then scales them with the plan, like the walls. Yojōhan's are
 * given at its middle zoom (1.075), Nagaya's at zoom 1 (its camera zooms from 1 out to 0.78).
 *
 * <p>One block per part of the scene. Each block keeps its own instances, one per look, so a change to one
 * block never touches another block's lines.
 */
public final class Look {
    public static final Look YOJOHAN = new Look(Plan.YOJOHAN, null, null, Palette.OLIVE, Walls.YOJOHAN,
            Camera.YOJOHAN, Doors.YOJOHAN, Fixtures.YOJOHAN, Post.YOJOHAN);
    public static final Look NAGAYA = new Look(Plan.NAGAYA, Rhythm.NAGAYA, Motion.NAGAYA, Palette.EMBER,
            Walls.NAGAYA, Camera.NAGAYA, Doors.NAGAYA, Fixtures.NAGAYA, Post.NAGAYA);

    public final Plan plan;
    /** When walls move. Null in Yojōhan, whose walls each slide on their own sine. */
    public final Rhythm rhythm;
    /** Which walls move and how far. Null in Yojōhan. */
    public final Motion motion;
    public final Palette palette;
    public final Walls walls;
    public final Camera camera;
    public final Doors doors;
    public final Fixtures fixtures;
    public final Post post;

    Look(Plan plan, Rhythm rhythm, Motion motion, Palette palette, Walls walls, Camera camera, Doors doors,
            Fixtures fixtures, Post post) {
        this.plan = plan;
        this.rhythm = rhythm;
        this.motion = motion;
        this.palette = palette;
        this.walls = walls;
        this.camera = camera;
        this.doors = doors;
        this.fixtures = fixtures;
        this.post = post;
    }

    // ------------------------------------------------------------------------------------------ plan

    /**
     * How the plan is cut into rooms.
     *
     * <p>{@link Kind#TREE} (Yojōhan): straight cuts and windmills (4 rooms round a centre room). Areas are in H² on
     * screen at the camera's middle zoom (1.075).
     *
     * <p>{@link Kind#TILE} (Nagaya): one tile of rooms, repeated on a staggered lattice. Every wall lies on a line
     * whose place is a whole number of {@link #unitH} steps. The tile is {@link #tileWUnits} wide and
     * {@link #tileHUnits} tall; each row of tiles is shifted half a tile.
     */
    public static final class Plan {
        public enum Kind { TREE, TILE }

        static final Plan YOJOHAN = new Plan(5L, 0.25f, 0.07f, 0.05f, 5f, 2.1f, 0.8f, 0.3f, 0.6f);
        /** A tile 270 x 174 steps (2.50 x 1.61 H). The smaller knob value is 135 x 87. */
        static final Plan NAGAYA = new Plan(5L, 1f / 108f, 270, 174, 0.046f, 2, 2, 15.5, 18.5, 0.8,
                new double[][] {{0.5, 0.5, 0}, {0.2, 0.7, 0.1}, {0.1, 0.3, 0.6}});

        public final Kind kind;
        /** Picks the layout; every seeded choice in the scene starts from it. */
        public final long seed;

        // TREE

        /** A part bigger than this is always cut again. */
        public final float splitAboveH2;
        /** A part smaller than this is always a room. In between, a coin decides. */
        public final float stopBelowH2;
        /** No room's short side is ever below this. */
        public final float minSideH;
        /** No room is ever longer than this many times its width. */
        public final float maxAspect;
        /** A part stays a room only if, with every wall at its middle, it is at most this long for its width. */
        public final float roomAspect;
        /** The chance that a part that can be a windmill is one. */
        public final float windmillOdds;
        /** Only parts at least this big become windmills, so the centre room is not too small. */
        public final float windmillMinH2;
        /** The share of windmills that turn clockwise. */
        public final float clockwiseShare;

        // TILE

        /** One step: lines sit and move on whole steps. */
        public final float unitH;
        /** The tile in steps: the lattice is (tileW, 0) and (tileW / 2, tileH). */
        public final int tileWUnits, tileHUnits;
        /** No room is ever thinner than this across: each cell's height in its row, a room's width between its joints. */
        public final float minCrossH;
        /** The least step in a room's edge, and the least offset between 2 joints that meet one wall from both sides. */
        public final int minStepUnits;
        /** The least width of a cell. */
        public final int minCellUnits;
        /** Rooms per H² that {@link SkylineBuilder} aims for. */
        public final double roomsPerH2;
        /** A row's mean height in steps, before its edges step. */
        public final double courseUnits;
        /** One row line in every this much tile height (H) runs straight all the way round. */
        public final double straightEveryH;
        /** The shares of rooms with 0, 1 and 2 steps in their edges: under 0.3 H wide, 0.3 to 0.5 H, over 0.5 H. */
        public final double[][] stepsByWidth;

        Plan(long seed, float splitAboveH2, float stopBelowH2, float minSideH, float maxAspect, float roomAspect,
                float windmillOdds, float windmillMinH2, float clockwiseShare) {
            this.kind = Kind.TREE;
            this.seed = seed;
            this.splitAboveH2 = splitAboveH2;
            this.stopBelowH2 = stopBelowH2;
            this.minSideH = minSideH;
            this.maxAspect = maxAspect;
            this.roomAspect = roomAspect;
            this.windmillOdds = windmillOdds;
            this.windmillMinH2 = windmillMinH2;
            this.clockwiseShare = clockwiseShare;
            this.unitH = 0f;
            this.tileWUnits = 0;
            this.tileHUnits = 0;
            this.minCrossH = 0f;
            this.minStepUnits = 0;
            this.minCellUnits = 0;
            this.roomsPerH2 = 0;
            this.courseUnits = 0;
            this.straightEveryH = 0;
            this.stepsByWidth = new double[0][];
        }

        Plan(long seed, float unitH, int tileWUnits, int tileHUnits, float minCrossH, int minStepUnits,
                int minCellUnits, double roomsPerH2, double courseUnits, double straightEveryH, double[][] stepsByWidth) {
            this.kind = Kind.TILE;
            this.seed = seed;
            this.splitAboveH2 = 0f;
            this.stopBelowH2 = 0f;
            this.minSideH = 0f;
            this.maxAspect = 0f;
            this.roomAspect = 0f;
            this.windmillOdds = 0f;
            this.windmillMinH2 = 0f;
            this.clockwiseShare = 0f;
            this.unitH = unitH;
            this.tileWUnits = tileWUnits;
            this.tileHUnits = tileHUnits;
            this.minCrossH = minCrossH;
            this.minStepUnits = minStepUnits;
            this.minCellUnits = minCellUnits;
            this.roomsPerH2 = roomsPerH2;
            this.courseUnits = courseUnits;
            this.straightEveryH = straightEveryH;
            this.stepsByWidth = stepsByWidth;
        }

        /** This TILE plan with another seed and tile size, for tests and tools. */
        Plan withTile(long seed, int tileWUnits, int tileHUnits) {
            return new Plan(seed, unitH, tileWUnits, tileHUnits, minCrossH, minStepUnits, minCellUnits, roomsPerH2,
                    courseUnits, straightEveryH, stepsByWidth);
        }
    }

    // ---------------------------------------------------------------------------------------- rhythm

    /**
     * When Nagaya's walls move: in slots. Slot k starts at {@code k * slotSeconds} plus a jitter of up to
     * {@code jitterSeconds} either way. In a slot each moving wall starts up to {@code staggerSeconds} after the
     * slot does and glides for {@code glideMinSeconds} to {@code glideSeconds}, each its own; then all rest until
     * the next slot. {@code staggerSeconds + glideSeconds} fits in the shortest slot, so a slot's glides are over
     * before the next one starts. Every {@code checkpointSeconds} of the wall clock all walls are home; the last
     * {@code homeSlots} slots before it lead them there, and the very last takes them all home together.
     */
    public static final class Rhythm {
        static final Rhythm NAGAYA = new Rhythm(7.0, 1.0, 2.4, 3.2, 1.8, 600.0, 3);

        public final double slotSeconds, jitterSeconds, glideMinSeconds, glideSeconds, staggerSeconds, checkpointSeconds;
        public final int homeSlots;

        Rhythm(double slotSeconds, double jitterSeconds, double glideMinSeconds, double glideSeconds,
                double staggerSeconds, double checkpointSeconds, int homeSlots) {
            this.slotSeconds = slotSeconds;
            this.jitterSeconds = jitterSeconds;
            this.glideMinSeconds = glideMinSeconds;
            this.glideSeconds = glideSeconds;
            this.staggerSeconds = staggerSeconds;
            this.checkpointSeconds = checkpointSeconds;
            this.homeSlots = homeSlots;
        }
    }

    // ---------------------------------------------------------------------------------------- motion

    /**
     * Which of Nagaya's walls move in a slot, and how far. With {@code quietShare} a slot moves nothing (never one
     * that leads home). Otherwise lines are picked in a shuffled order until the slot's share of the wall length
     * moves: {@code moveShare / (1 - quietShare)} times 1 give or take {@code shareSpread}, picked per slot, so over
     * all slots {@code moveShare} moves on average. Each aims at home plus 1 to {@code maxUnits} steps to one side
     * and goes as far toward it as the rooms around it allow. It turns back from its last side with
     * {@code reverseShare}, else a coin picks the side. With {@code alignShare} it instead lines up with a nearby
     * parallel line. No cell's width or height and no room's width ever gets below {@code keepShare} of its size at
     * home.
     */
    public static final class Motion {
        static final Motion NAGAYA = new Motion(0.25f, 13, 0.56f, 0.20f, 0.5f, 0.15f, 0.7f);

        public final float moveShare;
        public final int maxUnits;
        public final float reverseShare;
        public final float alignShare;
        public final float shareSpread;
        public final float quietShare;
        public final float keepShare;

        Motion(float moveShare, int maxUnits, float reverseShare, float alignShare, float shareSpread, float quietShare,
                float keepShare) {
            this.moveShare = moveShare;
            this.maxUnits = maxUnits;
            this.reverseShare = reverseShare;
            this.alignShare = alignShare;
            this.shareSpread = shareSpread;
            this.quietShare = quietShare;
            this.keepShare = keepShare;
        }
    }

    // --------------------------------------------------------------------------------------- palette

    /** Room paints and the colours of everything drawn on top of the rooms. ARGB. */
    public static final class Palette {
        /** How paints go to rooms. APART: rooms that ever touch never share a paint. SHUFFLE: in any order. */
        public enum Deal { APART, SHUFFLE }

        /** Olive: 22 paints, dark to light, as they look in the middle of the screen, before the lens's colour ramp. */
        static final Palette OLIVE = new Palette(
                new int[] {
                        0xFF1F120C, 0xFF1B1D0F, 0xFF251E16, 0xFF2F2913, 0xFF344234, 0xFF645535,
                        0xFFA03E1C, 0xFF577048, 0xFF776924, 0xFF6F7420, 0xFF82723D, 0xFF97804A,
                        0xFF669469, 0xFF909156, 0xFFA29240, 0xFF8D987B, 0xFFBA9535, 0xFFC39449,
                        0xFFB3A66C, 0xFFBDB65E, 0xFFCABA82, 0xFFF4ECCA},
                new int[] {
                        3, 5, 5, 5, 2, 4,
                        1, 4, 2, 3, 4, 3,
                        1, 4, 2, 2, 3, 2,
                        3, 2, 2, 2},
                0xFFC4BF74, 0xFF6E9F86, 0xFFAEB099, 0xFF040404, 0xFF1B1D0F, Deal.APART);

        /** Ember: 23 warm and olive paints, as they look in the middle of the screen, before the wash. */
        static final Palette EMBER = new Palette(
                new int[] {
                        0xFF100E08, 0xFF1A0D08, 0xFF262516, 0xFF4B1703, 0xFF34291A, 0xFF542407,
                        0xFF680B05, 0xFF2A3B21, 0xFF463C26, 0xFF6F380F, 0xFFA21504, 0xFF675115,
                        0xFF734D24, 0xFF8B4925, 0xFFC01B04, 0xFF845A31, 0xFF94572A, 0xFF317B34,
                        0xFF2E863C, 0xFFA78437, 0xFFC58030, 0xFFDA8417, 0xFFCF9611},
                new int[] {
                        1, 7, 5, 1, 2, 8,
                        5, 2, 1, 1, 2, 1,
                        3, 1, 2, 1, 1, 4,
                        1, 2, 5, 6, 2},
                0xFFE1B03C, 0xFFE1B03C, 0xFFE1B03C, 0xFF040103, 0xFF1A0D08, Deal.SHUFFLE);

        /** The room paints. {@link Frame#roomColor} holds an index into this. */
        public final int[] deck;
        /**
         * How often each paint of {@link #deck} comes up: its cards in a deck of 64, one card per room.
         * {@link RoomColours} deals in proportion for other room counts.
         */
        public final int[] cards;
        /** Door bar and swing. */
        public final int door;
        /** Toilet and tub. */
        public final int fixture;
        public final int stairs;
        /** Outlines of doors and fixtures. */
        public final int line;
        /** Shown only if the plan cannot draw. */
        public final int floor;
        public final Deal deal;

        Palette(int[] deck, int[] cards, int door, int fixture, int stairs, int line, int floor, Deal deal) {
            this.deck = deck;
            this.cards = cards;
            this.door = door;
            this.fixture = fixture;
            this.stairs = stairs;
            this.line = line;
            this.floor = floor;
            this.deal = deal;
        }
    }

    // ----------------------------------------------------------------------------------------- walls

    public static final class Walls {
        /** 0.0094 H on screen at the middle zoom (1.075): 10.2 px at 1080p. Near-black. */
        static final Walls YOJOHAN = new Walls(0.0094f / 1.075f, 0xFF040404);
        /** 0.0097 H at zoom 1: 10.5 px at 1080p. */
        static final Walls NAGAYA = new Walls(0.0097f, 0xFF040103);

        public final float widthH;
        public final int color;

        Walls(float widthH, int color) {
            this.widthH = widthH;
            this.color = color;
        }
    }

    // ---------------------------------------------------------------------------------------- camera

    /**
     * Yojōhan's camera keeps one tilt; its pan and zoom are in {@link io.github.pbanddev.tatami.Camera}.
     *
     * <p>Nagaya's rolls between {@code rollFromDeg} and {@code rollToDeg} (ramps of {@code rollRampSeconds}, sine
     * eased, with {@code rollHoldSeconds} holds between), zooms between {@code zoomFrom} and {@code zoomTo}
     * (eased ramps of {@code zoomRampSeconds}, each starting halfway through a roll ramp), and drifts
     * {@code driftH} a second toward {@code driftHeadingDeg} on screen at the middle roll (0 right, 90 down; a fixed
     * heading in plan axes, so it turns with the roll), plus a slow wander of
     * up to {@code wanderH} a second and {@code wanderDeg} of heading over about {@code wanderSeconds}.
     * With the camera switch off it holds {@code angleDeg} and {@code restZoom} and does not drift.
     */
    public static final class Camera {
        static final Camera YOJOHAN = new Camera(11f);
        /** A 26 s glide over 8 degrees peaks at 0.48 degrees a second. */
        static final Camera NAGAYA = new Camera(-20f, -16f, -24f, 40f, 26f, 1f, 0.78f, 20f, 0.89f,
                0.04f, 111.6f, 0.01f, 8f, 30f);

        /** The plan's tilt on screen, in degrees. Positive: walls fall to the right (y points down). */
        public final float angleDeg;
        public final float rollFromDeg, rollToDeg, rollHoldSeconds, rollRampSeconds;
        public final float zoomFrom, zoomTo, zoomRampSeconds, restZoom;
        public final float driftH, driftHeadingDeg, wanderH, wanderDeg, wanderSeconds;

        Camera(float angleDeg) {
            this(angleDeg, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 0f);
        }

        Camera(float angleDeg, float rollFromDeg, float rollToDeg, float rollHoldSeconds, float rollRampSeconds,
                float zoomFrom, float zoomTo, float zoomRampSeconds, float restZoom,
                float driftH, float driftHeadingDeg, float wanderH, float wanderDeg, float wanderSeconds) {
            this.angleDeg = angleDeg;
            this.rollFromDeg = rollFromDeg;
            this.rollToDeg = rollToDeg;
            this.rollHoldSeconds = rollHoldSeconds;
            this.rollRampSeconds = rollRampSeconds;
            this.zoomFrom = zoomFrom;
            this.zoomTo = zoomTo;
            this.zoomRampSeconds = zoomRampSeconds;
            this.restZoom = restZoom;
            this.driftH = driftH;
            this.driftHeadingDeg = driftHeadingDeg;
            this.wanderH = wanderH;
            this.wanderDeg = wanderDeg;
            this.wanderSeconds = wanderSeconds;
        }
    }

    // ----------------------------------------------------------------------------------------- doors

    /**
     * Doors: a pale bar in the wall, always drawn, and a swing that opens into one room. Yojōhan's sizes are given
     * on screen at the camera's middle zoom, 1.075, so each one is divided by it; Nagaya's at zoom 1.
     */
    public static final class Doors {
        /** WAVES: each door on a clipped sine of its own phase. TRACK: all doors on one shared opening. */
        public enum Timing { WAVES, TRACK }

        static final Doors YOJOHAN = new Doors(0.8f, 0.28f, 0.73f, 0.017f / 1.075f,
                0.0361f / 1.075f, 0.00315f / 1.075f, 0.0364f / 1.075f, 0.0020f / 1.075f,
                45f, 56f, 90f, 1.6f);
        static final Doors NAGAYA = new Doors(Timing.TRACK, 0.28f, 0.18f, 0.74f, 0.017f,
                0.0302f, 0.00245f, 0.0323f, 0.0020f, 0f, 0f, 100f, 0f, 0.78f, 1.7f,
                new Track(13.0, 1.2, 4.1, 0.9, 1.4, 2.0));

        public final Timing timing;
        /**
         * Yojōhan: the share of walls that get a door, tuned so about 0.6 doors per room show on screen
         * ({@code tools/shot.sh --stats}). Nagaya: the share of touching room pairs that get one.
         */
        public final float chance;
        /** Where along its wall a door wants its middle, as a share of the wall: each wall draws one in this range. */
        public final float nearAt, farAt;
        /** The least gap between a bar's end and its wall's ends. */
        public final float joinGapH;
        /** The bar in the wall: its length from the hinge, and half its thickness. */
        public final float lengthH, barHalfH;
        /** The swing, from the hinge to the middle of its arc's stroke. */
        public final float radiusH;
        /** The arc and the leaf. */
        public final float lineH;
        /** The opening, in degrees: clamp(middle + amplitude * sin(speed * t + phase), 0, max). TRACK uses max only. */
        public final float swingMiddleDeg, swingAmplitudeDeg, swingMaxDeg;
        /** Each door's phase is one shared phase plus up to this much either way, in radians. 0: all in step. */
        public final float phaseSpread;
        /** TRACK: the share of doors that swing up (on horizontal walls) or right (on vertical ones). */
        public final float swingBias;
        /** TRACK: a wall piece hosts a door only if it is at least this many bar lengths long. */
        public final float hostBars;
        /** TRACK: when the doors swing. {@link Track#NONE} for WAVES. */
        public final Track track;

        Doors(float chance, float nearAt, float farAt, float joinGapH, float lengthH, float barHalfH, float radiusH,
                float lineH, float swingMiddleDeg, float swingAmplitudeDeg, float swingMaxDeg, float phaseSpread) {
            this(Timing.WAVES, chance, nearAt, farAt, joinGapH, lengthH, barHalfH, radiusH, lineH,
                    swingMiddleDeg, swingAmplitudeDeg, swingMaxDeg, phaseSpread, 0f, 0f, Track.NONE);
        }

        Doors(Timing timing, float chance, float nearAt, float farAt, float joinGapH, float lengthH, float barHalfH,
                float radiusH, float lineH, float swingMiddleDeg, float swingAmplitudeDeg, float swingMaxDeg,
                float phaseSpread, float swingBias, float hostBars, Track track) {
            this.timing = timing;
            this.chance = chance;
            this.nearAt = nearAt;
            this.farAt = farAt;
            this.joinGapH = joinGapH;
            this.lengthH = lengthH;
            this.barHalfH = barHalfH;
            this.radiusH = radiusH;
            this.lineH = lineH;
            this.swingMiddleDeg = swingMiddleDeg;
            this.swingAmplitudeDeg = swingAmplitudeDeg;
            this.swingMaxDeg = swingMaxDeg;
            this.phaseSpread = phaseSpread;
            this.swingBias = swingBias;
            this.hostBars = hostBars;
            this.track = track;
        }

        /**
         * One opening for every door, on a clock of its own, apart from the walls': cycle c starts at
         * {@code c * cycleSeconds} plus up to {@code cycleJitterSeconds} either way. In a cycle the doors swing open
         * over {@code swingSeconds}, stay open {@code openSeconds} plus up to {@code openSpreadSeconds} more, swing
         * shut, then rest shut until the next cycle. Each door runs its own fixed delay of up to
         * {@code rippleSeconds} behind that opening, so they swing in a ripple, not all at once.
         */
        public static final class Track {
            static final Track NONE = new Track(0, 0, 0, 0, 0, 0);

            public final double cycleSeconds, cycleJitterSeconds, swingSeconds, openSeconds, openSpreadSeconds,
                    rippleSeconds;

            Track(double cycleSeconds, double cycleJitterSeconds, double swingSeconds, double openSeconds,
                    double openSpreadSeconds, double rippleSeconds) {
                this.cycleSeconds = cycleSeconds;
                this.cycleJitterSeconds = cycleJitterSeconds;
                this.swingSeconds = swingSeconds;
                this.openSeconds = openSeconds;
                this.openSpreadSeconds = openSpreadSeconds;
                this.rippleSeconds = rippleSeconds;
            }
        }
    }

    // -------------------------------------------------------------------------------------- fixtures

    /**
     * Toilets, tubs and stairs. Their shapes and sizes are in {@link FixtureShapes}. Those sizes and the ones
     * below are in units of {@link #unitH}, so they never change with the room.
     */
    public static final class Fixtures {
        static final Fixtures YOJOHAN = new Fixtures(1f / (1080f * 1.075f), 2.2f, 0.18f,
                new float[] {0.17f, 0.5f, 0.33f}, 31f, 28.5f, 20f);
        /** Nagaya has none. */
        static final Fixtures NAGAYA = new Fixtures(1f / 1080f, 2.2f, 0f,
                new float[] {0.17f, 0.5f, 0.33f}, 31f, 28.5f, 20f);

        /** One unit: 1 px at 1080p on screen at the camera's middle zoom (1.075), as a share of the screen height. */
        public final float unitH;
        /** The outline of the toilet and the tub. */
        public final float line;
        /** The share of rooms that hold a fixture. 0 for none. */
        public final float share;
        /** How often each kind shows up, by kind: tub, toilet, stairs ({@code Fixtures.TUB} and so on). */
        public final float[] weights;
        /** From the toilet's back to the face of the wall it backs onto. */
        public final float toiletGap;
        /** From the tub to the faces of the 2 walls of its corner. */
        public final float tubGap;
        /** Floor to leave between a fixture's open sides and the walls they face, in rooms that allow it. */
        public final float clearance;

        Fixtures(float unitH, float line, float share, float[] weights, float toiletGap, float tubGap, float clearance) {
            this.unitH = unitH;
            this.line = line;
            this.share = share;
            this.weights = weights;
            this.toiletGap = toiletGap;
            this.tubGap = tubGap;
            this.clearance = clearance;
        }
    }

    // ------------------------------------------------------------------------------------------ post

    /**
     * The lens: what the GPU does to the drawn plan, all of it fixed to the screen. A screen position p is
     * measured from the screen centre in half screen heights, x to the right and y up. Colours stay
     * gamma-encoded (0 to 1, as drawn), and so do the blur and the blend.
     *
     * <p>Yojōhan reads its vignette and colour ramp from the lens map. Nagaya instead washes its room and door
     * fills on the CPU ({@link #wash}, {@link Wash}), so its walls stay flat black.
     */
    public static final class Post {
        static final Post YOJOHAN = new Post(
                new Oval(-0.071f, 0.054f, 0.574f, 0.206f, 1.64f), 0.42f,
                new float[] {0.045f, 0f, -0.05f}, new float[] {0f, 0f, 0.03f},
                1.3f, 1.75f, 23.976,
                new Oval(0.030f, 0f, 0.611f, 0.837f, 1.157f), 4,
                new float[] {0.2719f, 0.3235f, 0.0406f}, new float[] {0f, 1.3333f, 3.1650f});
        /** Grain at 2/3 strength, as Yojōhan's; the blur is Yojōhan's. */
        static final Post NAGAYA = new Post(
                YOJOHAN.vignetteOval, 0f, new float[] {0f, 0f, 0f}, new float[] {0f, 0f, 0f},
                1.4f * 2f / 3f, 0f, 23.976,
                YOJOHAN.blurOval, YOJOHAN.blurDivisor, YOJOHAN.blurWeights, YOJOHAN.blurOffsets,
                2.35f * 2f / 3f, 0.25f, 0.30f, true, 0.07f);

        /** Where the vignette darkens: k = 0 inside the oval, 1 outside it. */
        public final Oval vignetteOval;
        /** Gain {@code 1 - vignette * k^3}: edge middles ~0.90-0.94, corners ~0.6-0.78. */
        public final float vignette;
        /**
         * The colour ramp: r, g and b times {@code exp(tintX * p.x + tintY * p.y)}, bluer to the left and
         * warmer to the right. The vignette switch turns it off too.
         */
        public final float[] tintX, tintY;
        /**
         * Grain std in 8-bit levels: {@code grainBase + grainSlope * luma^2 + grainStep * smoothstep(grainStepFrom,
         * grainStepTo, luma)}, luma 0 to 1. A term that is 0 is left out of the shader.
         */
        public final float grainBase, grainSlope, grainStep, grainStepFrom, grainStepTo;
        /** A new grain pattern this many times a second: film's frame rate. */
        public final double grainFps;
        /** Where the blurred copy shows: its weight is 0 inside the oval, 1 outside it. */
        public final Oval blurOval;
        /** The blurred copy is this many times smaller than the screen. */
        public final int blurDivisor;
        /**
         * One separable Gaussian round on the small copy: {@code blurWeights[i]} at {@code +-blurOffsets[i]}
         * of its texels (each tap between 2 texels, read bilinear). Sigma ~6 px at 1080p, 0.0056 H.
         */
        public final float[] blurWeights, blurOffsets;
        /** True: room and door fills take the screen's colour {@link Wash} on the CPU, in place of the lens map. */
        public final boolean wash;
        /** The wash is worked out at the corners of pieces at most this big each way, so it stays smooth. */
        public final float washPieceH;

        Post(Oval vignetteOval, float vignette, float[] tintX, float[] tintY,
                float grainBase, float grainSlope, double grainFps,
                Oval blurOval, int blurDivisor, float[] blurWeights, float[] blurOffsets) {
            this(vignetteOval, vignette, tintX, tintY, grainBase, grainSlope, grainFps, blurOval, blurDivisor,
                    blurWeights, blurOffsets, 0f, 0f, 0f, false, 0f);
        }

        Post(Oval vignetteOval, float vignette, float[] tintX, float[] tintY,
                float grainBase, float grainSlope, double grainFps,
                Oval blurOval, int blurDivisor, float[] blurWeights, float[] blurOffsets,
                float grainStep, float grainStepFrom, float grainStepTo, boolean wash, float washPieceH) {
            this.vignetteOval = vignetteOval;
            this.vignette = vignette;
            this.tintX = tintX;
            this.tintY = tintY;
            this.grainBase = grainBase;
            this.grainSlope = grainSlope;
            this.grainFps = grainFps;
            this.blurOval = blurOval;
            this.blurDivisor = blurDivisor;
            this.blurWeights = blurWeights;
            this.blurOffsets = blurOffsets;
            this.grainStep = grainStep;
            this.grainStepFrom = grainStepFrom;
            this.grainStepTo = grainStepTo;
            this.wash = wash;
            this.washPieceH = washPieceH;
        }

        /** {@code smoothstep(inner, outer, length(vec2((p.x - x) * scaleX, p.y - y)))}: 0 in the middle, 1 out. */
        public static final class Oval {
            public final float x, y, scaleX, inner, outer;

            Oval(float x, float y, float scaleX, float inner, float outer) {
                this.x = x;
                this.y = y;
                this.scaleX = scaleX;
                this.inner = inner;
                this.outer = outer;
            }
        }
    }
}
