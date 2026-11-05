package com.lionlev.tv.capacity;
import java.io.StringReader;
import org.junit.Test;
import static org.junit.Assert.*;
public class MemoryCapacityTest {
    private long[] read(String text) throws Exception { return MemoryCapacity.readMb(new StringReader(text)); }
    @Test public void modernAvailableTakesPrecedenceIncludingZero() throws Exception {
        String base = "MemTotal: 4096000 kB\nMemFree: 102400 kB\nBuffers: 1024 kB\nCached: 204800 kB\n";
        assertArrayEquals(new long[]{1000,4000},read(base + "MemAvailable: 1024000 kB\n"));
        assertArrayEquals(new long[]{0,4000},read(base + "MemAvailable: 0 kB\n"));
    }
    @Test public void oldKernelSumsBeforeConvertingAndClampsToTotal() throws Exception {
        assertArrayEquals(new long[]{3,10},read("MemTotal: 10240 kB\nMemFree: 1536 kB\nBuffers: 768 kB\nCached: 768 kB\n"));
        assertArrayEquals(new long[]{1,1},read("MemTotal: 1024 kB\nMemFree: 1024 kB\nBuffers: 1024 kB\nCached: 1024 kB\n"));
    }
    @Test public void incompleteMalformedOrOverflowCountersDoNotInventMemory() throws Exception {
        assertArrayEquals(new long[]{-1,1},read("MemTotal: 1024 kB\nMemFree: 1024 kB\nCached: 1024 kB\n"));
        assertArrayEquals(new long[]{-1,-1},read("MemAvailable: bad kB\nMemTotal: -1 kB\nMemFree: 1 MB\n"));
        assertArrayEquals(new long[]{-1,-1},read("MemFree: 9223372036854775807 kB\nBuffers: 1 kB\nCached: 1 kB\n"));
    }
}
