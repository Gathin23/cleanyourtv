package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FloatingClockViewTest {

    @Test
    public void tensOfSecondsCarriesSmoothlyAcrossBoundary() {
        float[] out = new float[3];

        // Mid-tens (01:15:25.0): no carry.
        assertFalse(FloatingClockView.carryState(4, 1 * 3600 + 15 * 60 + 25f, 1, 2, out));

        // 0.8 s before 29 -> 30 carry (u = 0.25): approaching 2 -> 3.
        assertTrue(FloatingClockView.carryState(4, 1 * 3600 + 15 * 60 + 29.2f, 1, 2, out));
        assertEquals(2f, out[0], 0.001f);
        assertEquals(3f, out[1], 0.001f);
        assertTrue(out[2] > 0f && out[2] < 0.5f);

        // Exactly at 30.0 s (u = 0.5): halfway between 2 and 3.
        assertTrue(FloatingClockView.carryState(4, 1 * 3600 + 15 * 60 + 30f, 1, 3, out));
        assertEquals(2f, out[0], 0.001f);
        assertEquals(3f, out[1], 0.001f);
        assertEquals(0.5f, out[2], 0.001f);

        // 0.8 s after 30.0 s (u = 0.75): finishing 2 -> 3.
        assertTrue(FloatingClockView.carryState(4, 1 * 3600 + 15 * 60 + 30.8f, 1, 3, out));
        assertEquals(2f, out[0], 0.001f);
        assertEquals(3f, out[1], 0.001f);
        assertTrue(out[2] > 0.5f && out[2] < 1f);
    }

    @Test
    public void midnightWrapsHoursFrom23To00() {
        float[] out = new float[3];

        // 23:59:59.5 -> approaching 00:00:00
        float sec = 23 * 3600 + 59 * 60 + 59.5f;
        assertTrue(FloatingClockView.carryState(0, sec, 23, 2, out));
        assertEquals(2f, out[0], 0.001f);
        assertEquals(0f, out[1], 0.001f);

        assertTrue(FloatingClockView.carryState(1, sec, 23, 3, out));
        assertEquals(3f, out[0], 0.001f);
        assertEquals(0f, out[1], 0.001f);

        // 00:00:00.5 -> completing 23 -> 00
        assertTrue(FloatingClockView.carryState(0, 0.5f, 0, 0, out));
        assertEquals(2f, out[0], 0.001f);
        assertEquals(0f, out[1], 0.001f);

        assertTrue(FloatingClockView.carryState(1, 0.5f, 0, 0, out));
        assertEquals(3f, out[0], 0.001f);
        assertEquals(0f, out[1], 0.001f);
    }
}
