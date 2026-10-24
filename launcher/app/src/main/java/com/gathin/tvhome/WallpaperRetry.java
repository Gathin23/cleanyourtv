package com.gathin.tvhome;

/**
 * After the TV starts, Home can come up before external storage is readable: Config then uses its copy of the theme
 * but finds none of the theme's pictures, and Home stays black. When a rotating collection is configured and nothing
 * was read, MainActivity looks again every few seconds for a while and recreates Home once a picture is there.
 */
final class WallpaperRetry {
    static final long INTERVAL_MS = 3_000L;
    static final long WINDOW_MS = 120_000L;

    private WallpaperRetry() {
    }

    /** Worth retrying: a rotation is configured and the start found no picture to show. */
    static boolean needed(boolean rotating, int pictures) {
        return rotating && pictures == 0;
    }

    /** Another look is still allowed {@code elapsed} ms after the first one was scheduled. */
    static boolean again(long elapsed) {
        return elapsed >= 0 && elapsed + INTERVAL_MS <= WINDOW_MS;
    }
}
