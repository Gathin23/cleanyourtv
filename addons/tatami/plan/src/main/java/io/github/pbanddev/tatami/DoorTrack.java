package io.github.pbanddev.tatami;

/**
 * Nagaya's door opening: one shared angle on a clock of its own ({@link Look.Doors.Track}), apart from the walls', so
 * doors and walls rarely start together. Cycle c starts at {@code c * cycleSeconds} plus a jitter hashed from the seed
 * and c. In a cycle the doors swing open along {@link Ease#glide}, stay open for a time hashed per cycle, swing shut
 * the same way, then rest shut until the next cycle. The ease starts and stops at rest and never overshoots, so the
 * opening never jumps and never passes its most.
 *
 * <p>Each door runs a fixed delay behind the shared opening, hashed from the seed and its wall piece's id, so the
 * doors swing in a ripple instead of all at once.
 *
 * <p>Pure: the same t always gives the same opening, whichever way the clock runs. The clock stays in double.
 */
final class DoorTrack {
    private static final long STREAM = 0x7472616B636C6BL;
    private static final int JITTER = 1, OPEN = 2, DELAY = 3;

    private final Look.Doors.Track track;
    private final long seed;
    /** The most a door opens, in radians. */
    private final double most;

    DoorTrack(Look.Doors doors, long seed) {
        track = doors.track;
        this.seed = seed ^ STREAM;
        most = Math.toRadians(doors.swingMaxDeg);
        Look.Doors.Track k = track;
        if (!(k.cycleSeconds > 0 && k.cycleJitterSeconds >= 0 && k.swingSeconds > 0 && k.openSeconds >= 0
                && k.openSpreadSeconds >= 0 && k.rippleSeconds >= 0
                && 2 * k.swingSeconds + k.openSeconds + k.openSpreadSeconds <= k.cycleSeconds - 2 * k.cycleJitterSeconds)) {
            throw new IllegalArgumentException("door cycles would overlap");
        }
    }

    /** When cycle c starts, in seconds of the wall clock. */
    double start(long c) {
        return c * track.cycleSeconds + track.cycleJitterSeconds * (2 * MoveClock.unit(MoveClock.hash(seed, c, JITTER, 0)) - 1);
    }

    /** The last cycle that has started by time t. */
    long cycle(double t) {
        long c = (long) Math.floor(t / track.cycleSeconds);
        if (start(c + 1) <= t) return c + 1;
        return start(c) <= t ? c : c - 1;
    }

    /** How far the doors are open at time t, in radians: 0 shut. */
    double opening(double t) {
        long c = cycle(t);
        double u = t - start(c), swing = track.swingSeconds;
        if (u < swing) return most * Ease.glide(u / swing);
        double close = swing + track.openSeconds + track.openSpreadSeconds * MoveClock.unit(MoveClock.hash(seed, c, OPEN, 0));
        if (u < close) return most;
        return most * (1 - Ease.glide((u - close) / swing));
    }

    /** How far the door on wall piece {@code piece} is open at time t: the shared opening, its delay ago. */
    double opening(double t, int piece) {
        return opening(t - delay(piece));
    }

    /** The door on wall piece {@code piece} runs this many seconds behind the shared opening, for good. */
    double delay(int piece) {
        return track.rippleSeconds * MoveClock.unit(MoveClock.hash(seed, piece, DELAY, 0));
    }
}
