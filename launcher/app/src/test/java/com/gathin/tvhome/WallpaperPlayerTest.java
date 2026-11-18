package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import android.os.Process;

import org.junit.Test;

public class WallpaperPlayerTest {
    @Test
    public void aRotationDecodesInTheBackgroundButAnEmptyScreenDoesNotWait() {
        // Home's frames and the app being opened come first...
        assertEquals(Process.THREAD_PRIORITY_BACKGROUND, WallpaperPlayer.decodePriority(false));
        // ...except after onTrimMemory, when Home came back to the plain colour and the owner is waiting.
        assertEquals(Process.THREAD_PRIORITY_DEFAULT, WallpaperPlayer.decodePriority(true));
    }
}
