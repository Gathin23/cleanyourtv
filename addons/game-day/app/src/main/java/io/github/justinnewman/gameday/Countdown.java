package io.github.justinnewman.gameday;

import java.util.Locale;

/** "46 min", "2h 18m", "1d 3h": the launcher's countdown, for the settings preview. */
final class Countdown {
    private Countdown() {
    }

    static String text(long ms) {
        long minutes = Math.max(1, (ms / 1000 + 59) / 60);
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        if (hours < 24) return String.format(Locale.US, "%dh %dm", hours, minutes % 60);
        return String.format(Locale.US, "%dd %dh", hours / 24, hours % 24);
    }
}
