package io.github.pbanddev.tatami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MoveClockTest {
    private static final Look.Rhythm RHYTHM = Look.NAGAYA.rhythm;
    private static final MoveClock CLOCK = new MoveClock(RHYTHM, Look.NAGAYA.plan.seed);
    private static final double NOW = 1_793_500_000.0;
    /** From a slot's start to the end of its last glide, at the most. */
    private static final double WINDOW = RHYTHM.staggerSeconds + RHYTHM.glideSeconds;

    @Test
    public void slotsAreOrderedAndSpacedWithinTheJitter() {
        double p = RHYTHM.slotSeconds, j = RHYTHM.jitterSeconds;
        for (long base : new long[] {-5, 0, (long) (NOW / p) - 300}) {
            for (long k = base; k < base + 600; k++) {
                double gap = CLOCK.start(k + 1) - CLOCK.start(k);
                assertTrue("slot " + k + " gap " + gap, gap >= p - 2 * j - 1e-9 && gap <= p + 2 * j + 1e-9);
                assertTrue("glides of " + k + " overlap the next slot", CLOCK.start(k) + WINDOW <= CLOCK.start(k + 1));
            }
        }
    }

    @Test
    public void everyTimeFallsInTheSlotThatHasStarted() {
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 20_000; i++) {
            double t = i % 2 == 0 ? random.nextDouble() * 5000 - 100 : NOW + random.nextDouble() * 1e6;
            long k = CLOCK.slot(t);
            assertTrue("t=" + t, CLOCK.start(k) <= t && t < CLOCK.start(k + 1));
        }
    }

    @Test
    public void checkpointSlotsStartAtOrAfterTheirCheckpoint() {
        int n = CLOCK.slotsPerCheckpoint;
        assertEquals(Math.round(RHYTHM.checkpointSeconds / RHYTHM.slotSeconds), n);
        for (long c = -3; c < 300; c++) {
            long k = c * n;
            assertEquals(k, CLOCK.checkpoint(k + n - 1));
            assertEquals(k, CLOCK.checkpoint(k));
            assertTrue(CLOCK.start(k) >= k * RHYTHM.slotSeconds);
            // the last glide before it is over by then
            assertTrue(CLOCK.start(k - 1) + WINDOW <= k * RHYTHM.slotSeconds);
        }
    }

    @Test
    public void eachGroupStartsWithinTheStaggerAndGlidesBetweenItsShortestAndLongest() {
        long k0 = CLOCK.slot(NOW);
        double earliest = Double.MAX_VALUE, latest = 0, shortest = Double.MAX_VALUE, longest = 0;
        for (long k = k0; k < k0 + 50; k++) {
            if (CLOCK.homeTogether(k)) continue;
            for (int g = 0; g < 100; g++) {
                double delay = CLOCK.delay(k, g), glide = CLOCK.glide(k, g);
                assertTrue("delay " + delay, delay >= 0 && delay <= RHYTHM.staggerSeconds);
                assertTrue("glide " + glide, glide >= RHYTHM.glideMinSeconds && glide <= RHYTHM.glideSeconds);
                assertEquals(delay, CLOCK.delay(k, g), 0);
                assertEquals(glide, CLOCK.glide(k, g), 0);
                earliest = Math.min(earliest, delay);
                latest = Math.max(latest, delay);
                shortest = Math.min(shortest, glide);
                longest = Math.max(longest, glide);
            }
        }
        assertTrue("delays span the stagger", latest - earliest > 0.95 * RHYTHM.staggerSeconds);
        assertTrue("glides span their range", longest - shortest > 0.95 * (RHYTHM.glideSeconds - RHYTHM.glideMinSeconds));
    }

    @Test
    public void theLastSlotBeforeACheckpointTakesEveryGroupHomeTogether() {
        int n = CLOCK.slotsPerCheckpoint;
        for (long c = -2; c < 50; c++) {
            long k = c * n - 1;
            assertTrue(CLOCK.homeTogether(k));
            assertTrue(!CLOCK.homeTogether(k - 1) && !CLOCK.homeTogether(k + 1));
            for (int g = 0; g < 100; g++) {
                assertEquals(0, CLOCK.delay(k, g), 0);
                assertEquals(RHYTHM.glideSeconds, CLOCK.glide(k, g), 0);
            }
        }
    }

    @Test
    public void aRhythmWhoseGlidesRunIntoTheNextSlotIsRefused() {
        long seed = Look.NAGAYA.plan.seed;
        assertThrows(IllegalArgumentException.class, () -> new MoveClock(new Look.Rhythm(7, 1, 2.4, 3.2, 1.9, 600, 3), seed));
        assertThrows(IllegalArgumentException.class, () -> new MoveClock(new Look.Rhythm(7, 1, 3.3, 3.2, 1.0, 600, 3), seed));
        new MoveClock(new Look.Rhythm(7, 1, 2.4, 3.2, 1.8, 600, 3), seed);
    }

    @Test
    public void glideStartsAndStopsAtRestAndNeverTurnsBack() {
        assertEquals(0, Ease.glide(0), 0);
        assertEquals(1, Ease.glide(1), 0);
        assertEquals(0.5, Ease.glide(0.5), 1e-12);
        assertEquals(0, Ease.glide(-1), 0);
        assertEquals(1, Ease.glide(2), 0);
        double h = 1e-6;
        assertEquals("zero speed at the start", 0, (Ease.glide(h) - Ease.glide(0)) / h, 1e-5);
        assertEquals("zero speed at the end", 0, (Ease.glide(1) - Ease.glide(1 - h)) / h, 1e-5);
        double last = 0;
        for (int i = 1; i <= 1000; i++) {
            double e = Ease.glide(i / 1000.0);
            assertTrue(e >= last && e <= 1);
            last = e;
        }
    }
}
