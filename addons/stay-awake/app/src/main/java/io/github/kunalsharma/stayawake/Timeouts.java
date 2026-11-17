package io.github.kunalsharma.stayawake;

/**
 * The screen-off timeouts the add-on cycles through. Android stores the timeout in milliseconds in
 * Settings.System.SCREEN_OFF_TIMEOUT; the largest int means "never turn the screen off".
 */
final class Timeouts {
    private Timeouts() {}

    /** The value that means Never. */
    static final int NEVER = Integer.MAX_VALUE;

    private static final int MINUTE = 60 * 1000;

    /** Offered timeouts, shortest first, ending in Never. */
    static final int[] OPTIONS = {
            15 * MINUTE,
            30 * MINUTE,
            60 * MINUTE,
            2 * 60 * MINUTE,
            4 * 60 * MINUTE,
            8 * 60 * MINUTE,
            NEVER,
    };

    /**
     * The option after the current one: the smallest offered timeout that is longer than {@code currentMs}.
     * Never, and anything longer than every option, wraps to the first option. Odd values the TV had before
     * (30 seconds, 10 minutes) therefore move up to the next offered step.
     */
    static int next(int currentMs) {
        if (currentMs >= NEVER) return OPTIONS[0];
        for (int option : OPTIONS) {
            if (option > currentMs) return option;
        }
        return OPTIONS[0];
    }

    /** True when the timeout means the screen never turns off. */
    static boolean isNever(int ms) {
        return ms >= NEVER;
    }

    /** Whole minutes in a finite timeout, rounded down. */
    static int minutes(int ms) {
        return ms / MINUTE;
    }
}
