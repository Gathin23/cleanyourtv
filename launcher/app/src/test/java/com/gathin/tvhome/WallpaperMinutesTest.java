package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WallpaperMinutesTest {

    @Test
    public void stepsUpToTheNextIntervalOnOffer() {
        assertEquals(5, WallpaperRotation.nextMinutes(1));
        assertEquals(60, WallpaperRotation.nextMinutes(30));
    }

    @Test
    public void aThemesOwnIntervalStepsToTheNextOneAboveIt() {
        assertEquals(30, WallpaperRotation.nextMinutes(20));
    }

    @Test
    public void wrapsToTheShortestAfterTheLongest() {
        assertEquals(1, WallpaperRotation.nextMinutes(1440));
    }
}
