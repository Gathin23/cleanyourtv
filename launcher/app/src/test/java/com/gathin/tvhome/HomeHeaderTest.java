package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** date_below_clock's arithmetic, greeting_text, and the wallpaper retry after a boot. */
public class HomeHeaderTest {

    @Test
    public void marginMakesTheInkGapEqual() {
        // The clock leaves 14 px under its digits and the date has 6 px over its capitals: a 10 px gap pulls it up.
        assertEquals(-10, HeaderLayout.marginForGap(10, 14, 6));
        assertEquals(4, HeaderLayout.marginForGap(10, 3, 3));
        assertEquals(10, HeaderLayout.marginForGap(10, 0, 0));
    }

    @Test
    public void buttonsCentreOnTheDigits() {
        // Digits from 20 to 80 px, buttons 40 px high: the buttons start at 30.
        assertEquals(30, HeaderLayout.centredTop(20, 80, 40));
        // Buttons taller than the digits stick out evenly above and below: centre 50, 80 px high.
        assertEquals(10, HeaderLayout.centredTop(30, 70, 80));
    }

    @Test
    public void greetingTextReplacesTheTimeOfDayGreeting() {
        assertEquals("Nhà mình", GreetingText.fixed("", "Nhà mình"));
        assertEquals("Nhà mình", GreetingText.fixed("", "  Nhà mình "));
        assertNull(GreetingText.fixed("", ""));
        assertNull(GreetingText.fixed("", "   "));
        assertNull(GreetingText.fixed(null, null));
        // A festival's own greeting still wins while it runs.
        assertEquals("Happy Diwali", GreetingText.fixed("Happy Diwali", "Nhà mình"));
    }

    @Test
    public void retryOnlyWhenARotationFoundNothing() {
        assertTrue(WallpaperRetry.needed(true, 0));
        assertFalse(WallpaperRetry.needed(true, 3));
        assertFalse(WallpaperRetry.needed(false, 0));
    }

    @Test
    public void retryEveryThreeSecondsForTwoMinutes() {
        assertEquals(3_000L, WallpaperRetry.INTERVAL_MS);
        assertTrue(WallpaperRetry.again(0));
        assertTrue(WallpaperRetry.again(117_000L));    // the last look lands at 120 s
        assertFalse(WallpaperRetry.again(117_001L));
        assertFalse(WallpaperRetry.again(-1));
        // As MainActivity runs it: the first look 3 s after the start, each look schedules the next while allowed.
        int looks = 0;
        for (long t = WallpaperRetry.INTERVAL_MS; ; t += WallpaperRetry.INTERVAL_MS) {
            looks++;
            if (!WallpaperRetry.again(t)) break;
        }
        assertEquals(40, looks);   // 3 s, 6 s, ... 120 s
    }
}
