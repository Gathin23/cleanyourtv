package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClockBrightnessTest {

    @Test
    public void alphaClampedWithinRange() {
        assertEquals(1.0f, ClockScene.parseAlpha(100), 0.001f);
        assertEquals(0.35f, ClockScene.parseAlpha(35), 0.001f);
        assertEquals(0.1f, ClockScene.parseAlpha(5), 0.001f);
        assertEquals(1.0f, ClockScene.parseAlpha(120), 0.001f);
    }
}
