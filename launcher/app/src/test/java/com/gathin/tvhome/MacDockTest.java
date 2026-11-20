package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MacDockTest {
    @Test
    public void iconsSitPitchApartCentredOnTheRow() {
        int slots = 8;
        float cell = 212f, pitch = 140f;
        float first = 0 * cell + cell / 2f + MacDock.shiftPx(0, slots, cell, pitch);
        float last = 7 * cell + cell / 2f + MacDock.shiftPx(7, slots, cell, pitch);
        for (int i = 1; i < slots; i++) {
            float prev = (i - 1) * cell + cell / 2f + MacDock.shiftPx(i - 1, slots, cell, pitch);
            float here = i * cell + cell / 2f + MacDock.shiftPx(i, slots, cell, pitch);
            assertEquals(pitch, here - prev, 1f);
        }
        assertEquals(slots * cell / 2f, (first + last) / 2f, 1f);
    }

    @Test
    public void iconsKeepAGapBetweenThem() {
        int side = 100;
        assertTrue(MacDock.pitchPx(side) > side);
        assertEquals(22.5f, MacDock.radiusPx(side), 0.01f);
    }
}
