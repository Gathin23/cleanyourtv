package io.github.pbanddev.tatami;

/**
 * Nagaya's doors: one on every wall piece the plan flags as a host ({@link Frame#wallDoor}), so at most one per pair
 * of rooms, open as far as the one {@link DoorTrack} says, each at its own small delay behind it.
 *
 * <p>Each piece draws from the seed and its {@link Frame#wallId}, the same in every copy of the tile and never
 * changing: where along the piece the bar's middle sits (a share of the piece between {@link Look.Doors#nearAt} and
 * {@link Look.Doors#farAt}), and which room the leaf swings into (up on a row wall, or right on a joint, with
 * {@link Look.Doors#swingBias}). The swing side sets the hinge end, so every door opens clockwise, and the plan hosts
 * doors only where that room stays deep enough for the swing ({@link TilePlan#doorHosts}). The bar keeps its share as
 * the piece glides, a gap from the piece's ends (centred on a piece too short for that), so it rides its piece and
 * never jumps.
 *
 * <p>It runs every frame: no allocation, one step per wall piece.
 */
final class NagayaDoors implements DoorPlacer {
    private static final long STREAM = 0x646F6F72L;
    // what each piece draws from the seed
    private static final int AT = 1, SIDE = 2;

    private final Look.Doors look;
    private final long seed;
    private final DoorTrack track;

    NagayaDoors(Look look) {
        this.look = look.doors;
        seed = look.plan.seed;
        track = new DoorTrack(look.doors, look.plan.seed);
    }

    /**
     * The room the door on wall piece {@code piece} of the plan with this seed swings into: +1 toward +y (+x on a
     * joint), -1 the other way; up on a row wall or right on a joint with share {@code bias}. The plan asks this too,
     * when it picks hosts.
     */
    static int side(long seed, float bias, int piece, boolean vertical) {
        boolean usual = MoveClock.unit(MoveClock.hash(seed ^ STREAM, piece, SIDE, 0)) < bias;
        return vertical == usual ? 1 : -1;
    }

    @Override
    public void place(Frame f, double t, View view) {
        float length = view.h * look.lengthH, gap = view.h * look.joinGapH;
        int count = 0;
        for (int w = 0; w < f.wallCount; w++) {
            if (!f.wallDoor[w]) continue;
            if (count == f.doorWall.length) {
                f.clipped = true;
                break;
            }
            int id = f.wallId[w];
            boolean vertical = f.wallVertical[w];
            float from = vertical ? f.wallY0[w] : f.wallX0[w], to = vertical ? f.wallY1[w] : f.wallX1[w];
            float at = look.nearAt + (look.farAt - look.nearAt) * (float) MoveClock.unit(MoveClock.hash(seed ^ STREAM, id, AT, 0));
            // the bar's middle, kept a gap from the piece's ends; both limits meet in the middle on a short piece
            float low = from + gap + length / 2, high = to - gap - length / 2;
            float middle = low <= high ? Math.max(low, Math.min(high, from + at * (to - from))) : (from + to) / 2;
            // clockwise turns the bar's way from the hinge (u) into the swing's (v), with y down
            int side = side(seed, look.swingBias, id, vertical);
            int along = vertical ? -side : side;
            float hinge = middle - along * length / 2;
            f.doorWall[count] = w;
            f.doorAlong[count] = along;
            f.doorSide[count] = side;
            f.doorX[count] = vertical ? f.wallX0[w] : hinge;
            f.doorY[count] = vertical ? hinge : f.wallY0[w];
            f.doorOpening[count] = track.opening(t, id);
            count++;
        }
        f.doorCount = count;
    }
}
