package com.gathin.tvhome;

import android.content.ComponentCallbacks2;

/** Memory that affects the picture is released only when Home is not on screen. */
final class MemoryTrimPolicy {
    private MemoryTrimPolicy() {
    }

    static boolean shouldRelease(int level, boolean homeStarted) {
        if (homeStarted) return false;
        return level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
                || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL;
    }

    /**
     * Whether Home has to let go by itself once it has been stopped a while, because no trim level above would come.
     * From Android 14 (SDK 34) on, RUNNING_LOW and RUNNING_CRITICAL are no longer sent, and a bound key helper
     * (HomeKeyService, InputKeyService) keeps this process visible, so it is never cached and never gets
     * TRIM_MEMORY_BACKGROUND either. Without a helper, or on an older Android, shouldRelease still covers it.
     */
    static boolean releaseWhenStopped(int sdk, boolean keyHelperBound) {
        return sdk >= 34 && keyHelperBound;
    }
}
