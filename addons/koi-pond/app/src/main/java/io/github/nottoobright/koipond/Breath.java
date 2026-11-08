package io.github.nottoobright.koipond;

/** The breathing rhythm: in 4 s, out 6 s, repeating, with a sine ease so the ring never jerks. */
final class Breath {

    static final long IN_MS = 4000, OUT_MS = 6000, CYCLE_MS = IN_MS + OUT_MS;

    private Breath() {
    }

    /** Ring size 0..1, eased (sine in/out): 0 at the start of an in-breath, 1 at its end, back to 0 at the end of the out-breath. */
    static float size(long elapsedMs) {
        long e = phaseMs(elapsedMs);
        if (e < IN_MS) {
            return (float) (0.5 - 0.5 * Math.cos(Math.PI * e / IN_MS));
        }
        return (float) (0.5 + 0.5 * Math.cos(Math.PI * (e - IN_MS) / OUT_MS));
    }

    static boolean inhaling(long elapsedMs) {
        return phaseMs(elapsedMs) < IN_MS;
    }

    /** Whole seconds left in the current phase, counting down 4..1 or 6..1. */
    static int secondsLeft(long elapsedMs) {
        long e = phaseMs(elapsedMs);
        long left = e < IN_MS ? IN_MS - e : CYCLE_MS - e;
        return (int) ((left + 999) / 1000);
    }

    private static long phaseMs(long elapsedMs) {
        return Math.max(0L, elapsedMs) % CYCLE_MS;
    }
}
