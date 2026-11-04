package com.gathin.tvhome;

/** show_stats' memory figure from the lines of /proc/meminfo. */
final class MemInfo {
    private MemInfo() {}

    /**
     * RAM usage as {used, total} in MB, used = MemTotal - MemAvailable, e.g. "945 / 1536 MB". Used over free so
     * the line reads as a load against a known whole. MemAvailable only exists since Linux 3.14; on older kernels
     * (TPM171E Philips, Android TV 8, kernel 3.10) available is MemFree + Buffers + Cached, the estimate free(1)
     * used before it. Null when MemTotal or every available figure is missing.
     */
    static long[] usedOfTotalMb(Iterable<String> lines) {
        long total = -1, available = -1, free = -1, buffers = -1, cached = -1;
        for (String line : lines) {
            if (line.startsWith("MemTotal:")) total = kb(line);
            else if (line.startsWith("MemAvailable:")) available = kb(line);
            else if (line.startsWith("MemFree:")) free = kb(line);
            else if (line.startsWith("Buffers:")) buffers = kb(line);
            else if (line.startsWith("Cached:")) cached = kb(line);
        }
        if (available < 0 && free >= 0) available = free + Math.max(0, buffers) + Math.max(0, cached);
        if (available < 0 || total <= 0) return null;
        long totalMb = total / 1024;
        long availableMb = Math.min(total, available) / 1024;
        return new long[]{Math.max(0, totalMb - availableMb), totalMb};
    }

    /** The kB figure of a "Name:   1234 kB" line, or -1. */
    private static long kb(String line) {
        String[] t = line.trim().split("\\s+");
        if (t.length < 2) return -1;
        try {
            return Long.parseLong(t[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
