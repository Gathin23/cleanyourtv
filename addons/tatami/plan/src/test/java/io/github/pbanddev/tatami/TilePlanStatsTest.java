package io.github.pbanddev.tatami;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/** Nagaya's motion over many slots: how much moves, how far, and that it turns back. */
public class TilePlanStatsTest {
    private static final int SLOTS = 2000;
    private static final long[] SEEDS = {5, 11, 23};

    @Test
    public void slotsMoveTheLooksShareOfWallsAShortWayAndMostlyBack() {
        Look.Motion motion = Look.NAGAYA.motion;
        float share = motion.moveShare;
        // the most a busy slot aims for
        double most = share * (1 + motion.shareSpread) / (1 - motion.quietShare);
        for (long seed : SEEDS) {
            TilePlan plan = TilePlanTest.plan(seed, new int[] {270, 174});
            TileGraph g = plan.graph;
            int n = g.groups;
            long total = 0;
            for (int length : g.groupLength) total += length;
            int[] before = new int[n], after = new int[n], lastWay = new int[n];
            List<Double> shares = new ArrayList<>();
            List<Integer> travel = new ArrayList<>();
            int turns = 0, pairs = 0;
            long k0 = plan.motion.clock.slot(TilePlanTest.NOW);
            for (long k = k0; k < k0 + SLOTS; k++) {
                plan.motion.glide(k, before, after);
                long moving = 0;
                for (int i = 0; i < n; i++) {
                    int d = after[i] - before[i];
                    if (d == 0) continue;
                    moving += g.groupLength[i];
                    travel.add(Math.abs(d));
                    if (lastWay[i] != 0) {
                        pairs++;
                        if (Integer.signum(d) != lastWay[i]) turns++;
                    }
                    lastWay[i] = Integer.signum(d);
                }
                shares.add(moving / (double) total);
            }
            Collections.sort(shares);
            Collections.sort(travel);
            // over all slots, quiet ones too
            double median = shares.get(SLOTS / 2), p90 = shares.get(SLOTS * 9 / 10);
            String at = "seed " + seed + ": share median " + median + ", p90 " + p90;
            assertTrue(at, median >= share - 0.03 && median <= share + 0.06);
            assertTrue(at, p90 <= most + 0.08);
            int t50 = travel.get(travel.size() / 2), t90 = travel.get(travel.size() * 9 / 10);
            assertTrue("seed " + seed + ": travel median " + t50, t50 >= 5 && t50 <= 8);
            assertTrue("seed " + seed + ": travel p90 " + t90, t90 >= 10 && t90 <= 14);
            assertTrue("seed " + seed + ": reversal " + turns / (double) pairs, turns >= 0.70 * pairs);
        }
    }

    @Test
    public void aColdStartEqualsASteppedRun() {
        TilePlan stepped = TilePlanTest.plan(5, new int[] {270, 174});
        int n = stepped.graph.groups;
        int[] a = new int[n], b = new int[n], c = new int[n], d = new int[n];
        float[] x = new float[n], y = new float[n];
        MoveClock clock = stepped.motion.clock;
        long k0 = clock.slot(TilePlanTest.NOW) - 37;
        Random random = new Random(1);
        for (long k = k0; k < k0 + 450; k++) {
            stepped.motion.glide(k, a, b);
            double t = clock.start(k) + random.nextDouble() * (clock.staggerSeconds + clock.glideSeconds);
            stepped.motion.offsets(t, x);
            if (random.nextInt(10) != 0) continue;
            TilePlan cold = TilePlanTest.plan(5, new int[] {270, 174});
            cold.motion.glide(k, c, d);
            assertArrayEquals("slot " + k, a, c);
            assertArrayEquals("slot " + k, b, d);
            // and after a jump back, mid-glide too
            cold.motion.offsets(clock.start(k - 1 - random.nextInt(300)) + 2.0, y);
            cold.motion.offsets(t, y);
            assertArrayEquals("slot " + k + " t=" + t, x, y, 0f);
            cold.motion.glide(k, c, d);
            assertArrayEquals("slot " + k, b, d);
        }
    }
}
