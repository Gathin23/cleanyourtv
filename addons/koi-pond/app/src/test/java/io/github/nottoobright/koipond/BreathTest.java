package io.github.nottoobright.koipond;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BreathTest {

    private static final float EPS = 1e-4f;

    @Test
    public void sizeFollowsTheCycle() {
        assertEquals(0f, Breath.size(0), EPS);
        assertEquals(0.5f, Breath.size(2000), EPS);
        assertEquals(1f, Breath.size(4000), EPS);
        assertEquals(0.5f, Breath.size(7000), EPS);
        assertEquals(0f, Breath.size(10000), EPS);
        assertEquals(1f, Breath.size(14000), EPS);
    }

    @Test
    public void sizeStaysInRange() {
        for (long t = 0; t < 25_000; t += 37) {
            float s = Breath.size(t);
            assertTrue("size at " + t + " was " + s, s >= 0f && s <= 1f);
        }
    }

    @Test
    public void negativeElapsedCountsAsStart() {
        assertEquals(0f, Breath.size(-500), EPS);
        assertTrue(Breath.inhaling(-500));
        assertEquals(4, Breath.secondsLeft(-500));
    }

    @Test
    public void inhalingOnlyInTheFirstFourSeconds() {
        assertTrue(Breath.inhaling(0));
        assertTrue(Breath.inhaling(3999));
        assertFalse(Breath.inhaling(4000));
        assertFalse(Breath.inhaling(9999));
        assertTrue(Breath.inhaling(10000));
    }

    @Test
    public void secondsLeftCountsDownEachPhase() {
        assertEquals(4, Breath.secondsLeft(0));
        assertEquals(4, Breath.secondsLeft(999));
        assertEquals(3, Breath.secondsLeft(1000));
        assertEquals(1, Breath.secondsLeft(3999));
        assertEquals(6, Breath.secondsLeft(4000));
        assertEquals(5, Breath.secondsLeft(5000));
        assertEquals(1, Breath.secondsLeft(9999));
        assertEquals(4, Breath.secondsLeft(10000));
    }
}
