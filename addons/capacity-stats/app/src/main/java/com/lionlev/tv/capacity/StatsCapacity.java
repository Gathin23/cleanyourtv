package com.lionlev.tv.capacity;

import java.util.Locale;

/** Available/total, with one unit shared by both values. Never invent a missing total. */
final class StatsCapacity {
    static String memory(long availableMb, long totalMb) {
        if (availableMb < 0) return "";
        return Long.toString(availableMb) + (totalMb > 0 ? "/" + totalMb : "");
    }
    static String storage(long availableBytes, long totalBytes) {
        if (availableBytes < 0) return "";
        double unit = 1024.0 * 1024 * 1024;
        String available = String.format(Locale.US, "%.1f", availableBytes / unit);
        return available + (totalBytes > 0 ? "/" + String.format(Locale.US, "%.1f", totalBytes / unit) : "") + " GB";
    }
}
