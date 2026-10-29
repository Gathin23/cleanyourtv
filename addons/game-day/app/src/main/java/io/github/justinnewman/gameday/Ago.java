package io.github.justinnewman.gameday;

/** "2 hours ago", for the settings screens. */
final class Ago {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private Ago() {
    }

    static String text(long nowMs, long thenMs) {
        if (thenMs <= 0) return "never";
        long d = Math.max(0, nowMs - thenMs);
        if (d < MINUTE) return "just now";
        if (d < HOUR) return plural(d / MINUTE, "minute");
        if (d < DAY) return plural(d / HOUR, "hour");
        if (d < 2 * DAY) return "yesterday";
        return plural(d / DAY, "day");
    }

    private static String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s") + " ago";
    }
}
