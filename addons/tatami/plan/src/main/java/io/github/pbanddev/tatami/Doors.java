package io.github.pbanddev.tatami;

/**
 * Places the doors on the walls of a laid-out {@link Frame} and sets how far each one is open.
 *
 * <p>Each wall id draws, from the plan's seed, whether it has a door, where along the wall the door sits
 * (a share of the wall's length), which end of the bar the hinge is at and which way the leaf swings. These
 * never change. A door rides its wall: the joins of other walls slide past it, and it never
 * hides, jumps or pops back. Its bar stays a gap away from the wall's own ends (centred when the wall is too
 * short for that). It reads only wall pieces, never how the plan was cut.
 *
 * <p>All doors swing at one speed, their phases spread around a shared one, so they open and shut in
 * loose waves. The swing is a clipped sine: doors pause shut and pause open.
 *
 * <p>It runs every frame on the GL thread: no allocation, one step per wall.
 */
public final class Doors {
    /** How fast doors swing, in radians per second. The pace, not the look: it stays the same in every look. */
    static final double SPEED = 1.3;

    // what each wall draws from the seed
    private static final int HAS_DOOR = 0, AT = 1, HINGE = 2, SIDE = 3, PHASE = 4;
    /** The shared phase draws as wall -1. */
    private static final int ALL = -1;

    private final Look.Doors look;
    private final long seed;
    private final double sharedPhase;

    public Doors(Look look) {
        this.look = look.doors;
        seed = look.plan.seed;
        sharedPhase = 2 * Math.PI * draw(seed, ALL, PHASE);
    }

    /** Fills the door arrays of {@code f} at time {@code t}, and its door count. Sizes follow {@code screenHeight}. */
    public void place(Frame f, double t, float screenHeight) {
        int walls = Math.min(f.doorWall.length, f.wallCount);
        float length = screenHeight * look.lengthH, gap = screenHeight * look.joinGapH;
        int count = 0;
        for (int w = 0; w < walls; w++) {
            if (draw(seed, w, HAS_DOOR) >= look.chance) continue;
            boolean vertical = f.wallVertical[w];
            float from = vertical ? f.wallY0[w] : f.wallX0[w], to = vertical ? f.wallY1[w] : f.wallX1[w];
            float at = look.nearAt + (look.farAt - look.nearAt) * (float) draw(seed, w, AT);
            // the bar's middle, kept a gap from the wall's ends; both limits meet in the middle on a short wall
            float low = from + gap + length / 2, high = to - gap - length / 2;
            float middle = low <= high ? Math.max(low, Math.min(high, from + at * (to - from))) : (from + to) / 2;
            int along = draw(seed, w, HINGE) < 0.5 ? 1 : -1;
            float hinge = middle - along * length / 2;
            f.doorWall[count] = w;
            f.doorAlong[count] = along;
            f.doorSide[count] = draw(seed, w, SIDE) < 0.5 ? 1 : -1;
            f.doorX[count] = vertical ? f.wallX0[w] : hinge;
            f.doorY[count] = vertical ? hinge : f.wallY0[w];
            f.doorOpening[count] = opening(t, sharedPhase + look.phaseSpread * (2 * draw(seed, w, PHASE) - 1));
            count++;
        }
        f.doorCount = count;
    }

    /** How far a door with this phase is open at time t, in radians. */
    private double opening(double t, double phase) {
        double deg = look.swingMiddleDeg + look.swingAmplitudeDeg * Math.sin(SPEED * t + phase);
        return Math.toRadians(Math.max(0, Math.min(look.swingMaxDeg, deg)));
    }

    /** A number in [0, 1) that depends only on the seed, the wall and what is drawn (SplitMix64's mixer). */
    private static double draw(long seed, int wall, int what) {
        long z = seed * 0x9E3779B97F4A7C15L + (wall + 1L) * 0xD1B54A32D192ED03L + what * 0x8CB92BA72F3D8DD7L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
