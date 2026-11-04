package io.github.luyzfernando08.sleeptimer;

/** A client and its Shizuku user service share Android's elapsed-realtime clock. */
final class PowerDeadline {
    static final long TIMEOUT_MS = 9_000L;

    private PowerDeadline() { }

    /** Refuse expired or implausibly distant deadlines, including arithmetic overflow. */
    static boolean active(long deadlineElapsedMs, long nowElapsedMs) {
        long remaining = deadlineElapsedMs - nowElapsedMs;
        return remaining > 0 && remaining <= TIMEOUT_MS;
    }
}
