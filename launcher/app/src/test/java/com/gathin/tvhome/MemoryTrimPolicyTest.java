package com.gathin.tvhome;

import android.content.ComponentCallbacks2;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MemoryTrimPolicyTest {
    @Test
    public void ordinaryBackgroundTransitionKeepsWarmResources() {
        assertFalse(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, false));
        assertFalse(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, true));
    }

    @Test
    public void backgroundPressureReleasesResourcesOnlyWhenHomeIsStopped() {
        assertTrue(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, false));
        assertTrue(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, false));
        assertFalse(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, true));
        assertFalse(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, true));
    }

    @Test
    public void stoppedHomeReleasesByItselfOnlyWhereNoTrimLevelWouldCome() {
        assertTrue(MemoryTrimPolicy.releaseWhenStopped(34, true));
        assertTrue(MemoryTrimPolicy.releaseWhenStopped(35, true));
        assertFalse(MemoryTrimPolicy.releaseWhenStopped(33, true));
        assertFalse(MemoryTrimPolicy.releaseWhenStopped(34, false));
    }

    @Test
    public void lowMemorySignalsReleaseResourcesWhenHomeIsStopped() {
        assertTrue(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, false));
        assertTrue(MemoryTrimPolicy.shouldRelease(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, false));
    }
}
