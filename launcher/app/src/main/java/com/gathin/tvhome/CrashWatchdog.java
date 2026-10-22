package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides when the launcher should start in safe mode: three crashes within five minutes. Pure Java, so
 * the rules are tested on the JVM; {@link WatchdogApp} stores the timestamps. Nothing here runs on a timer:
 * old crashes simply stop counting the next time the list is read or written.
 */
final class CrashWatchdog {
    static final long WINDOW_MS = 5 * 60 * 1000L;
    static final int LIMIT = 3;
    /** Entries kept; only the newest few can matter, so the stored string stays tiny. */
    static final int CAP = 5;

    private CrashWatchdog() {}

    /** True when at least {@link #LIMIT} crashes happened in the last {@link #WINDOW_MS}. */
    static boolean safeMode(List<Long> crashes, long now) {
        return prune(crashes, now).size() >= LIMIT;
    }

    /** The list with {@code now} added, pruned and capped. */
    static List<Long> record(List<Long> crashes, long now) {
        List<Long> out = prune(crashes, now);
        out.add(now);
        while (out.size() > CAP) out.remove(0);
        return out;
    }

    /**
     * Keeps the crashes inside the window, oldest first. A timestamp after {@code now} comes from a clock
     * that was set back (a TV that lost its time); it cannot be placed, so it is dropped rather than counted.
     */
    static List<Long> prune(List<Long> crashes, long now) {
        List<Long> out = new ArrayList<>();
        for (Long t : crashes) {
            if (t != null && t <= now && now - t <= WINDOW_MS) out.add(t);
        }
        java.util.Collections.sort(out);
        while (out.size() > CAP) out.remove(0);
        return out;
    }

    /** Reads "t1,t2,..." from preferences; anything unreadable is skipped. */
    static List<Long> parse(String text) {
        List<Long> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(",")) {
            try {
                out.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    static String format(List<Long> crashes) {
        StringBuilder sb = new StringBuilder();
        for (Long t : crashes) {
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        return sb.toString();
    }
}
