package com.gathin.matchday;

final class RequestCooldown {
    static final long INTERVAL_MILLIS = 60_000L;

    private RequestCooldown() { }

    static boolean isCoolingDown(long nowMillis, long nextAllowedAtMillis) {
        return nowMillis < nextAllowedAtMillis;
    }

    static long nextAllowedAt(long nowMillis) {
        return nowMillis + INTERVAL_MILLIS;
    }

    static long remainingMillis(long nowMillis, long nextAllowedAtMillis) {
        return Math.max(0L, nextAllowedAtMillis - nowMillis);
    }
}
