package io.github.pbanddev.tatami;

/**
 * Nagaya's shared rhythm, from the wall clock and the seed alone ({@link Look.Rhythm}). Slot k starts at
 * {@code k * slotSeconds} plus a jitter in [-jitterSeconds, jitterSeconds] hashed from k. In each slot each moving
 * group of walls starts its glide {@link #delay} after the slot does and glides for {@link #glide}, both hashed from
 * the slot and the group; then everything rests until the next slot starts. Every {@link #slotsPerCheckpoint} slots
 * is a checkpoint, when every wall is home; a checkpoint slot's jitter is never negative, so it starts at or after
 * its checkpoint's time. The last slot before it takes every group home together: no delay, the longest glide.
 *
 * <p>Slot numbers are longs worked out from the double clock; the clock itself never goes into a float.
 */
final class MoveClock {
    private static final int DELAY = 1, GLIDE = 2;

    final double slotSeconds, jitterSeconds, glideMinSeconds, glideSeconds, staggerSeconds;
    final int slotsPerCheckpoint;
    private final long seed;

    MoveClock(Look.Rhythm rhythm, long seed) {
        slotSeconds = rhythm.slotSeconds;
        jitterSeconds = rhythm.jitterSeconds;
        glideMinSeconds = rhythm.glideMinSeconds;
        glideSeconds = rhythm.glideSeconds;
        staggerSeconds = rhythm.staggerSeconds;
        slotsPerCheckpoint = (int) Math.round(rhythm.checkpointSeconds / rhythm.slotSeconds);
        this.seed = seed;
        if (!(slotSeconds > 0 && jitterSeconds >= 0 && glideMinSeconds > 0 && glideMinSeconds <= glideSeconds
                && staggerSeconds >= 0 && staggerSeconds + glideSeconds <= slotSeconds - 2 * jitterSeconds
                && slotsPerCheckpoint >= 2)) {
            throw new IllegalArgumentException("glides would overlap: slot " + slotSeconds + " s, jitter "
                    + jitterSeconds + " s, glide " + glideMinSeconds + " to " + glideSeconds + " s, stagger "
                    + staggerSeconds + " s, " + slotsPerCheckpoint + " slots per checkpoint");
        }
    }

    /** When slot k starts, in seconds of the wall clock. */
    double start(long k) {
        double j = jitterSeconds * (2 * unit(hash(seed, k, 0, 0)) - 1);
        if (Math.floorMod(k, slotsPerCheckpoint) == 0) j = Math.abs(j);
        return k * slotSeconds + j;
    }

    /** The last slot that has started by time t. */
    long slot(double t) {
        long k = (long) Math.floor(t / slotSeconds);
        if (start(k + 1) <= t) return k + 1;
        return start(k) <= t ? k : k - 1;
    }

    /** The checkpoint slot at or before slot k. */
    long checkpoint(long k) {
        return Math.floorDiv(k, slotsPerCheckpoint) * slotsPerCheckpoint;
    }

    /** True for the last slot before a checkpoint, which takes every group home together. */
    boolean homeTogether(long k) {
        return Math.floorMod(k, slotsPerCheckpoint) == slotsPerCheckpoint - 1;
    }

    /** How long after slot k starts group g's glide starts, in [0, staggerSeconds]. */
    double delay(long k, int g) {
        return homeTogether(k) ? 0 : staggerSeconds * unit(hash(seed, k, g, DELAY));
    }

    /** How long group g's glide in slot k lasts, in [glideMinSeconds, glideSeconds]. */
    double glide(long k, int g) {
        return homeTogether(k) ? glideSeconds : glideMinSeconds + (glideSeconds - glideMinSeconds) * unit(hash(seed, k, g, GLIDE));
    }

    /** 64 well-mixed bits from a seed and 3 numbers (SplitMix64's mixer, chained). */
    static long hash(long seed, long a, long b, long c) {
        long z = mix(seed * 0x9E3779B97F4A7C15L + a);
        z = mix(z + b * 0xD1B54A32D192ED03L);
        return mix(z + c * 0x8CB92BA72F3D8DD7L);
    }

    /** A hash as a number in [0, 1). */
    static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
