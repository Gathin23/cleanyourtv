package io.github.kunalsharma.stayawake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimeoutsTest {
    private static final int MIN = 60 * 1000;

    @Test
    public void cyclesThroughEveryOptionAndWrapsAfterNever() {
        int ms = Timeouts.OPTIONS[0];
        for (int i = 1; i < Timeouts.OPTIONS.length; i++) {
            ms = Timeouts.next(ms);
            assertEquals(Timeouts.OPTIONS[i], ms);
        }
        assertEquals(Timeouts.NEVER, ms);
        assertEquals(Timeouts.OPTIONS[0], Timeouts.next(ms));
    }

    @Test
    public void thirtyMinutesGoesToAnHour() {
        assertEquals(60 * MIN, Timeouts.next(30 * MIN));
    }

    @Test
    public void shortTimeoutsMoveUpToTheFirstOption() {
        assertEquals(15 * MIN, Timeouts.next(30 * 1000));
        assertEquals(15 * MIN, Timeouts.next(0));
    }

    @Test
    public void anOddValueMovesUpToTheNextStep() {
        assertEquals(30 * MIN, Timeouts.next(20 * MIN));
    }

    @Test
    public void neverIsRecognised() {
        assertTrue(Timeouts.isNever(Timeouts.NEVER));
        assertFalse(Timeouts.isNever(8 * 60 * MIN));
    }

    @Test
    public void minutesRoundDown() {
        assertEquals(30, Timeouts.minutes(30 * MIN));
        assertEquals(1, Timeouts.minutes(90 * 1000));
    }
}
