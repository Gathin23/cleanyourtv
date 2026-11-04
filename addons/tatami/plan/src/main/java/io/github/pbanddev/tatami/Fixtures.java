package io.github.pbanddev.tatami;

import java.util.Random;

/**
 * Puts toilets, tubs and stairs in their rooms. A fixture keeps its size from {@link FixtureShapes} and never
 * shrinks, so a room hosts one only if the smallest the room ever gets ({@link Frame#roomMinW},
 * {@link Frame#roomMinH}) holds it with its gaps, between the wall faces. Rooms that also leave
 * {@link Look.Fixtures#clearance} on its open sides come first.
 *
 * <p>The hosts, kinds, walls or corners and turns are picked once from the plan's seed, for
 * {@link Look.Fixtures#share} of the rooms, dealt to the kinds by {@link Look.Fixtures#weights}. They are
 * picked again only when the screen height or the rooms' minimums change. Every frame each fixture then
 * follows its room's wall faces (half a wall inside the room's box). Nothing is allocated per frame.
 *
 * <ul>
 *   <li>Toilet: centred on its back wall, {@link Look.Fixtures#toiletGap} from the wall's face. Turn 0
 *       backs onto the top wall, then clockwise: right, bottom, left.</li>
 *   <li>Tub: in any corner, {@link Look.Fixtures#tubGap} from both faces. Turn 0 lies along x, turn 1 along y.</li>
 *   <li>Stairs: flush in a corner, the arrow pointing into it. Turn 0 runs right into the bottom-right
 *       corner, then clockwise: bottom-left, top-left, top-right.</li>
 * </ul>
 */
public final class Fixtures {
    public static final int TUB = 0;
    public static final int TOILET = 1;
    public static final int STAIRS = 2;
    private static final int KINDS = 3;

    /** Biggest first, so smaller kinds do not take the few rooms the stairs fit. */
    private static final int[] BIGGEST_FIRST = {STAIRS, TUB, TOILET};
    /** Corner bits. */
    private static final int RIGHT = 1, BOTTOM = 2;
    /** The corner the stairs run into at each turn. */
    private static final int[] STAIRS_CORNER = {RIGHT | BOTTOM, BOTTOM, 0, RIGHT};
    /** Keeps these picks apart from the plan's other seeded choices. */
    private static final long SALT = 0x5F1C_7E55L;

    private final Look look;
    private final Random random = new Random();
    private final int[] dealt = new int[KINDS];

    // what the picks were made for
    private float pickedHeight = Float.NaN;
    private int pickedRooms = -1;
    private float[] pickedMinW = new float[0], pickedMinH = new float[0];
    // the picks
    private int hosts;
    private int[] hostRoom = new int[0], hostKind = new int[0], hostTurn = new int[0], hostCorner = new int[0];
    private int[] order = new int[0];
    private boolean[] taken = new boolean[0];

    public Fixtures(Look look) {
        this.look = look;
    }

    /** Fills the fixture arrays of {@code f} from its rooms. Sizes follow {@code screenHeight}. */
    public void place(Frame f, float screenHeight) {
        if (!pickedFor(f, screenHeight)) pick(f, screenHeight);
        float unit = screenHeight * look.fixtures.unitH, half = screenHeight * look.walls.widthH / 2;
        int n = 0;
        for (int j = 0; j < hosts; j++) {
            int r = hostRoom[j], kind = hostKind[j], turn = hostTurn[j];
            float left = f.roomX[r] + half, top = f.roomY[r] + half;
            float right = f.roomX[r] + f.roomW[r] - half, bottom = f.roomY[r] + f.roomH[r] - half;
            // the minimums promise a fit; should a room ever break that, leave its fixture out, never cross a wall
            if (!fits(kind, turn, right - left, bottom - top, unit, 0f)) continue;
            float w = unit * FixtureShapes.width(kind), h = unit * FixtureShapes.depth(kind);
            if ((turn & 1) == 1) {
                float swap = w;
                w = h;
                h = swap;
            }
            float x, y;
            if (kind == TOILET) {
                float gap = unit * look.fixtures.toiletGap;
                x = turn == 1 ? right - gap - w : turn == 3 ? left + gap : (left + right - w) / 2;
                y = turn == 0 ? top + gap : turn == 2 ? bottom - gap - h : (top + bottom - h) / 2;
            } else {
                float gap = kind == TUB ? unit * look.fixtures.tubGap : 0f;
                int corner = hostCorner[j];
                x = (corner & RIGHT) != 0 ? right - gap - w : left + gap;
                y = (corner & BOTTOM) != 0 ? bottom - gap - h : top + gap;
            }
            f.fixtureKind[n] = kind;
            f.fixtureRoom[n] = r;
            f.fixtureTurn[n] = turn;
            f.fixtureX[n] = x;
            f.fixtureY[n] = y;
            f.fixtureW[n] = w;
            f.fixtureH[n] = h;
            n++;
        }
        f.fixtureCount = n;
    }

    /**
     * True when the floor between the wall faces, {@code across} by {@code down}, holds the kind at this turn
     * with its gaps and {@code clearance} units of floor on its open sides.
     */
    private boolean fits(int kind, int turn, float across, float down, float unit, float clearance) {
        float w = unit * FixtureShapes.width(kind), h = unit * FixtureShapes.depth(kind), c = unit * clearance;
        boolean odd = (turn & 1) == 1;
        if (odd) {
            float swap = w;
            w = h;
            h = swap;
        }
        if (kind == TOILET) {
            float gap = unit * look.fixtures.toiletGap;
            return odd ? h + 2 * c <= down && gap + w + c <= across : w + 2 * c <= across && gap + h + c <= down;
        }
        float gap = kind == TUB ? unit * look.fixtures.tubGap : 0f;
        return gap + w + c <= across && gap + h + c <= down;
    }

    private boolean pickedFor(Frame f, float screenHeight) {
        if (screenHeight != pickedHeight || f.roomCount != pickedRooms) return false;
        for (int i = 0; i < f.roomCount; i++) {
            if (f.roomMinW[i] != pickedMinW[i] || f.roomMinH[i] != pickedMinH[i]) return false;
        }
        return true;
    }

    /** Picks the hosts. Allocates only when the frame holds more rooms than ever before. */
    private void pick(Frame f, float screenHeight) {
        int rooms = f.roomCount;
        if (order.length < rooms) {
            pickedMinW = new float[rooms];
            pickedMinH = new float[rooms];
            hostRoom = new int[rooms];
            hostKind = new int[rooms];
            hostTurn = new int[rooms];
            hostCorner = new int[rooms];
            order = new int[rooms];
            taken = new boolean[rooms];
        }
        System.arraycopy(f.roomMinW, 0, pickedMinW, 0, rooms);
        System.arraycopy(f.roomMinH, 0, pickedMinH, 0, rooms);
        pickedHeight = screenHeight;
        pickedRooms = rooms;
        hosts = 0;

        random.setSeed(look.plan.seed ^ SALT);
        for (int i = 0; i < rooms; i++) {
            order[i] = i;
            taken[i] = false;
        }
        for (int i = rooms - 1; i > 0; i--) {
            int j = random.nextInt(i + 1), swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        deal(Math.round(look.fixtures.share * rooms));
        float unit = screenHeight * look.fixtures.unitH, wall = screenHeight * look.walls.widthH;
        for (int kind : BIGGEST_FIRST) {
            for (int c = 0; c < dealt[kind]; c++) host(f, kind, unit, wall);
        }
    }

    /** Splits {@code count} fixtures between the kinds by weight, the leftovers to the largest remainders. */
    private void deal(int count) {
        float[] weights = look.fixtures.weights;
        float total = 0f;
        for (int kind = 0; kind < KINDS; kind++) total += kind < weights.length ? Math.max(0f, weights[kind]) : 0f;
        int left = count;
        for (int kind = 0; kind < KINDS; kind++) {
            dealt[kind] = total > 0f && kind < weights.length ? (int) (count * Math.max(0f, weights[kind]) / total) : 0;
            left -= dealt[kind];
        }
        while (left > 0 && total > 0f) {
            int best = 0;
            float bestRest = -Float.MAX_VALUE;
            for (int kind = 0; kind < KINDS && kind < weights.length; kind++) {
                float rest = count * Math.max(0f, weights[kind]) / total - dealt[kind];
                if (rest > bestRest) {
                    bestRest = rest;
                    best = kind;
                }
            }
            dealt[best]++;
            left--;
        }
    }

    /**
     * Gives one fixture of this kind to the first free room, in the seeded order, that it fits at some seeded
     * turn with the clearance. If no room does, to the first it fits at all.
     */
    private void host(Frame f, int kind, float unit, float wall) {
        int options = kind == TUB ? 8 : 4;   // the tub: 2 turns in each of 4 corners
        for (int pass = 0; pass < 2; pass++) {
            float clearance = pass == 0 ? look.fixtures.clearance : 0f;
            for (int o = 0; o < pickedRooms; o++) {
                int r = order[o];
                if (taken[r]) continue;
                float across = f.roomMinW[r] - wall, down = f.roomMinH[r] - wall;
                int first = random.nextInt(options);
                for (int i = 0; i < options; i++) {
                    int option = (first + i) % options, turn = kind == TUB ? option & 1 : option;
                    if (!fits(kind, turn, across, down, unit, clearance)) continue;
                    taken[r] = true;
                    hostRoom[hosts] = r;
                    hostKind[hosts] = kind;
                    hostTurn[hosts] = turn;
                    hostCorner[hosts] = kind == TUB ? option >> 1 : kind == STAIRS ? STAIRS_CORNER[turn] : 0;
                    hosts++;
                    return;
                }
            }
        }
    }
}
