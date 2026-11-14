package com.gathin.tvhome;

/** Once-per-boot startup policy for the optional remote-pairing prompt. */
final class RemotePairingPrompt {
    static final long STARTUP_GRACE_MS = 30_000L;
    static final String CHECKED_BOOT_KEY = "remote_pairing_checked_boot";
    static final String STARTED_BOOT_KEY = "remote_pairing_started_boot";
    static final String STARTED_UPTIME_KEY = "remote_pairing_started_uptime";

    private RemotePairingPrompt() {
    }

    static boolean due(int boot, int checkedBoot) {
        return boot >= 0 && boot != checkedBoot;
    }

    static long remainingDelay(long startedAt, long now) {
        if (startedAt < 0L || now < startedAt) return STARTUP_GRACE_MS;
        return Math.max(0L, STARTUP_GRACE_MS - (now - startedAt));
    }
}
