package com.gathin.tvhome;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;
public class MemInfoTest {
    @Test public void usesMemAvailableWhenTheKernelHasIt() {
        long[] mem = MemInfo.usedOfTotalMb(Arrays.asList(
                "MemTotal:        1572864 kB",
                "MemFree:          102400 kB",
                "MemAvailable:     604160 kB",
                "Buffers:           20480 kB",
                "Cached:           409600 kB"));
        assertArrayEquals(new long[]{946, 1536}, mem);
    }
    @Test public void fallsBackToFreeBuffersAndCachedBefore314() {
        // A 3.10 kernel (Philips TPM171E, Android TV 8): no MemAvailable line.
        long[] mem = MemInfo.usedOfTotalMb(Arrays.asList(
                "MemTotal:        1909568 kB",
                "MemFree:           58408 kB",
                "Buffers:           48164 kB",
                "Cached:           682468 kB",
                "SwapCached:            0 kB"));
        assertArrayEquals(new long[]{1864 - 770, 1864}, mem);
    }
    @Test public void freeAloneIsEnoughForTheFallback() {
        assertArrayEquals(new long[]{1024, 2048},
                MemInfo.usedOfTotalMb(Arrays.asList("MemTotal: 2097152 kB", "MemFree: 1048576 kB")));
    }
    @Test public void neverReportsMoreAvailableThanTotal() {
        assertArrayEquals(new long[]{0, 1024},
                MemInfo.usedOfTotalMb(Arrays.asList("MemTotal: 1048576 kB", "MemAvailable: 2097152 kB")));
    }
    @Test public void nullWithoutTotalOrAnyAvailableFigure() {
        assertNull(MemInfo.usedOfTotalMb(Collections.emptyList()));
        assertNull(MemInfo.usedOfTotalMb(Arrays.asList("MemTotal: 1048576 kB", "Buffers: 1024 kB")));
        assertNull(MemInfo.usedOfTotalMb(Arrays.asList("MemFree: 1048576 kB")));
        assertNull(MemInfo.usedOfTotalMb(Arrays.asList("MemTotal: x kB", "MemFree: 1024 kB")));
    }
}
