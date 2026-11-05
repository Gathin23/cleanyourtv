package com.lionlev.tv.capacity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/** Reads KiB counters once; old kernels lack MemAvailable, so use a conservative estimate. */
final class MemoryCapacity {
    static long[] readMb(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        long total = -1, available = -1, free = -1, buffers = -1, cached = -1;
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 3 || !"kB".equals(fields[2])) continue;
            long value;
            try { value = Long.parseLong(fields[1]); }
            catch (NumberFormatException ignored) { continue; }
            if (value < 0) continue;
            switch (fields[0]) {
                case "MemTotal:": total = value; break;
                case "MemAvailable:": available = value; break;
                case "MemFree:": free = value; break;
                case "Buffers:": buffers = value; break;
                case "Cached:": cached = value; break;
            }
        }
        if (available < 0 && free >= 0 && buffers >= 0 && cached >= 0) {
            try { available = Math.addExact(Math.addExact(free, buffers), cached); }
            catch (ArithmeticException ignored) { available = -1; }
        }
        if (available >= 0 && total > 0) available = Math.min(available, total);
        return new long[]{available < 0 ? -1 : available / 1024, total < 0 ? -1 : total / 1024};
    }
}
