package io.github.pbanddev.tatami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class DoorTrackTest {
    private static final Look LOOK = Look.NAGAYA;
    private static final DoorTrack TRACK = new DoorTrack(LOOK.doors, LOOK.plan.seed);
    private static final double NOW = 1_793_500_000.0, FRAME = 1.0 / 60;

    private static double deg(double t) {
        return Math.toDegrees(TRACK.opening(t));
    }

    @Test
    public void staysBetweenShutAndItsMostAndReachesBoth() {
        double most = 0, least = Double.MAX_VALUE;
        for (double t = NOW; t < NOW + 600; t += FRAME / 3) {
            double a = deg(t);
            most = Math.max(most, a);
            least = Math.min(least, a);
        }
        assertEquals(0, least, 0);
        assertEquals(LOOK.doors.swingMaxDeg, most, 1e-9);
    }

    @Test
    public void neverJumpsBetweenFrames() {
        long c0 = TRACK.cycle(NOW);
        double most = 0;
        for (long base : new long[] {c0, -3, 0}) {
            for (long c = base; c < base + (base == c0 ? 300 : 10); c++) {
                for (double t = TRACK.start(c) - 0.5; t < TRACK.start(c + 1); t += FRAME) {
                    most = Math.max(most, Math.abs(deg(t + FRAME) - deg(t)));
                }
            }
        }
        assertTrue("largest step " + most + " deg in 1/60 s", most <= 2);
    }

    @Test
    public void opensEachCycleAndIsShutBeforeTheNext() {
        long c0 = TRACK.cycle(NOW);
        for (long c = c0; c < c0 + 400; c++) {
            double start = TRACK.start(c);
            assertEquals("cycle " + c, 0, deg(start), 0);
            assertTrue("cycle " + c + " opens", deg(start + 0.25) > 0);
            assertEquals("cycle " + c + " shut before the next", 0, deg(TRACK.start(c + 1) - 1e-6), 0);
        }
    }

    @Test
    public void cyclesComeAtUnevenTimes() {
        long c0 = TRACK.cycle(NOW);
        double least = Double.MAX_VALUE, most = 0;
        for (long c = c0; c < c0 + 400; c++) {
            double gap = TRACK.start(c + 1) - TRACK.start(c);
            least = Math.min(least, gap);
            most = Math.max(most, gap);
        }
        assertTrue("gaps " + least + " to " + most + " s", most - least > 2 * LOOK.doors.track.cycleJitterSeconds);
    }

    @Test
    public void doorsDoNotStartWithTheWalls() {
        MoveClock walls = new MoveClock(LOOK.rhythm, LOOK.plan.seed);
        long c0 = TRACK.cycle(NOW);
        int near = 0, cycles = 3000;
        for (long c = c0; c < c0 + cycles; c++) {
            double start = TRACK.start(c);
            long k = walls.slot(start);
            double gap = Math.min(start - walls.start(k), walls.start(k + 1) - start);
            if (gap < 0.25) near++;
        }
        assertTrue(near + " of " + cycles + " door cycles start with a wall slot", near < cycles / 5);
    }

    @Test
    public void aboutAThirdShutAThirdMovingAThirdOpen() {
        int shut = 0, open = 0, all = 0;
        for (double t = NOW; t < NOW + 30_000; t += 0.05) {
            double a = deg(t);
            if (a <= 5) shut++;
            if (a >= 75) open++;
            all++;
        }
        double s = shut / (double) all, o = open / (double) all, m = 1 - s - o;
        String shares = String.format("shut %.3f, moving %.3f, open %.3f", s, m, o);
        assertTrue(shares, s > 0.28 && s < 0.39);
        assertTrue(shares, m > 0.28 && m < 0.39);
        assertTrue(shares, o > 0.28 && o < 0.39);
    }

    @Test
    public void eachDoorRipplesUpToItsMostBehindTheTrack() {
        double ripple = LOOK.doors.track.rippleSeconds, least = Double.MAX_VALUE, most = 0;
        for (int piece = 0; piece < 500; piece++) {
            double d = TRACK.delay(piece);
            least = Math.min(least, d);
            most = Math.max(most, d);
            assertEquals(TRACK.opening(NOW - d), TRACK.opening(NOW, piece), 0);
        }
        assertTrue(least >= 0 && most <= ripple);
        assertTrue("delays spread over the ripple", most - least > 0.8 * ripple);
    }

    @Test
    public void theSameAtAnyTimeWhicheverWayTheClockRuns() {
        DoorTrack fresh = new DoorTrack(LOOK.doors, LOOK.plan.seed);
        Random random = new Random(11);
        double[] times = new double[500];
        for (int i = 0; i < times.length; i++) times[i] = NOW + random.nextDouble() * 1e5;
        double[] forward = new double[times.length];
        for (int i = 0; i < times.length; i++) forward[i] = TRACK.opening(times[i]);
        for (int i = times.length - 1; i >= 0; i--) assertEquals(forward[i], fresh.opening(times[i]), 0);
    }
}
