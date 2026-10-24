package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** wallpaper_rotation_seconds: the interval, when WallpaperPlayer needs its own timer, and how long that waits. */
public class WallpaperSecondsTest {

    @Test
    public void secondsWinOverMinutes() {
        assertEquals(5_000L, WallpaperRotation.intervalMillis(30, 5));
        assertEquals(1_800_000L, WallpaperRotation.intervalMillis(30, 0));
        assertEquals(0L, WallpaperRotation.intervalMillis(0, 0));
        assertEquals(0L, WallpaperRotation.intervalMillis(-1, -5));
        assertEquals(86_400_000L, WallpaperRotation.intervalMillis(0, 86_400));
    }

    @Test
    public void ownTimerOnlyUnderAMinute() {
        assertTrue(WallpaperRotation.ownTimer(5_000L));
        assertTrue(WallpaperRotation.ownTimer(59_000L));
        assertFalse(WallpaperRotation.ownTimer(60_000L));   // Home's minute tick is enough
        assertFalse(WallpaperRotation.ownTimer(90_000L));
        assertFalse(WallpaperRotation.ownTimer(0L));
    }

    @Test
    public void delayCountsFromTheLastChange() {
        long now = 1_000_000L;
        assertEquals(5_000L, WallpaperRotation.delay(0, now, 5_000L));            // nothing shown yet
        assertEquals(3_000L, WallpaperRotation.delay(now - 2_000L, now, 5_000L)); // a slow decode ate 2 s
        assertEquals(250L, WallpaperRotation.delay(now - 9_000L, now, 5_000L));   // overdue: change soon
        assertEquals(5_000L, WallpaperRotation.delay(now + 60_000L, now, 5_000L)); // the clock went back
    }

    @Test
    public void dueAgreesWithTheTimer() {
        long changed = 1_000_000L;
        assertFalse(WallpaperRotation.due(changed, changed + 4_999L, 5_000L));
        assertTrue(WallpaperRotation.due(changed, changed + 5_000L, 5_000L));
    }
}
