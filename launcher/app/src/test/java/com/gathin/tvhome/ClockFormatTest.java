package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClockFormatTest {

    @Test
    public void aChosenFormatWinsWithoutAskingTheTv() {
        // A null context would throw if the TV's own setting were read.
        assertTrue(ClockFormat.is24(null, "24"));
        assertFalse(ClockFormat.is24(null, "12"));
    }
}
