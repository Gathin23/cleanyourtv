package io.github.pbanddev.tatami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/** Nagaya's staggered glides: every rule holds at every moment, rooms keep most of their size, and slots differ. */
public class TileMotionTest {
    private static final double EPS = 1e-6;
    private static final int[][] TILES = {{270, 174}, {135, 87}};
    private static final long[] SEEDS = {5, 11, 23, 1, 2};

    static TilePlan skyline(long seed, int[] tile) {
        return new TilePlan(TilePlanTest.look(seed, tile[0], tile[1]), new SkylineBuilder());
    }

    /** The first of the slots to run: some way before a checkpoint, so the slots that lead home and the next ones run too. */
    private static long firstSlot(MoveClock clock) {
        return clock.checkpoint(clock.slot(TilePlanTest.NOW)) + clock.slotsPerCheckpoint - 60;
    }

    /** Every group of slot k at time t, by the slot's own timing: from before to after along its own glide. */
    private static void timed(MoveClock clock, long k, int[] before, int[] after, double t, double[] out) {
        double since = t - clock.start(k);
        for (int i = 0; i < out.length; i++) {
            out[i] = before[i] + (after[i] - before[i]) * Ease.glide((since - clock.delay(k, i)) / clock.glide(k, i));
        }
    }

    /**
     * Every group somewhere on its way: at its start, its end, or anywhere between, each on its own; all the same way
     * along in the slot that takes every group home together, which never staggers.
     */
    private static void anyTiming(Random random, boolean together, int[] before, int[] after, double[] out) {
        double shared = random.nextDouble();
        for (int i = 0; i < out.length; i++) {
            double e = together ? shared : random.nextInt(3) == 0 ? random.nextInt(2) : random.nextDouble();
            out[i] = before[i] + (after[i] - before[i]) * e;
        }
    }

    private static void rules(TileGraph g, double[] off, String at) {
        for (int r = 0; r < g.ruleHi.length; r++) {
            double v = g.ruleGap[r] + off[g.ruleHi[r]] - off[g.ruleLo[r]];
            if (v < g.ruleMin[r] - EPS) throw new AssertionError(at + ": rule " + r + " is " + v + ", below " + g.ruleMin[r]);
        }
    }

    @Test
    public void everyRuleHoldsAtEveryMomentWhateverEachGroupsTiming() {
        for (long seed : SEEDS) {
            for (int[] tile : TILES) {
                TilePlan plan = skyline(seed, tile);
                TileGraph g = plan.graph;
                MoveClock clock = plan.motion.clock;
                int n = g.groups;
                int[] before = new int[n], after = new int[n];
                double[] off = new double[n];
                float[] shown = new float[n];
                Random random = new Random(seed);
                long k0 = firstSlot(clock);
                for (long k = k0; k < k0 + 160; k++) {
                    plan.motion.glide(k, before, after);
                    String at = "seed " + seed + " tile " + tile[0] + " slot " + k;
                    for (int s = 0; s < 6; s++) {
                        anyTiming(random, clock.homeTogether(k), before, after, off);
                        rules(g, off, at + " any timing");
                    }
                    // the slot's own timing: at a group's start, end or inside its glide, and anywhere in the slot
                    double start = clock.start(k), window = clock.staggerSeconds + clock.glideSeconds;
                    for (int s = 0; s < 12; s++) {
                        int i = random.nextInt(n);
                        double from = start + clock.delay(k, i), u = s % 3 == 0 ? 0 : s % 3 == 1 ? 1 : random.nextDouble();
                        double t = s < 9 ? from + u * clock.glide(k, i) : start + random.nextDouble() * window;
                        timed(clock, k, before, after, t, off);
                        rules(g, off, at + " t=" + t);
                        plan.motion.offsets(t, shown);
                        for (int j = 0; j < n; j++) assertEquals(at + " t=" + t, off[j], shown[j], 1e-4);
                    }
                }
            }
        }
    }

    @Test
    public void noCellOrRoomEverGetsBelowItsKeptShareOfHome() {
        double keep = Look.NAGAYA.motion.keepShare;
        for (long seed : SEEDS) {
            for (int[] tile : TILES) {
                TilePlan plan = skyline(seed, tile);
                TileGraph g = plan.graph;
                int[] before = new int[g.groups], after = new int[g.groups];
                double[] off = new double[g.groups];
                Random random = new Random(seed);
                MoveClock clock = plan.motion.clock;
                long k0 = firstSlot(clock);
                for (long k = k0; k < k0 + 160; k++) {
                    plan.motion.glide(k, before, after);
                    anyTiming(random, clock.homeTogether(k), before, after, off);
                    String at = "seed " + seed + " tile " + tile[0] + " slot " + k;
                    for (int c = 0; c < g.cells; c++) {
                        int w = g.cellX1[c] - g.cellX0[c], h = g.cellY1[c] - g.cellY0[c];
                        assertTrue(at + " cell " + c, w + off[g.cellRight[c]] - off[g.cellLeft[c]] >= keep * w - EPS);
                        assertTrue(at + " cell " + c, h + off[g.cellBottom[c]] - off[g.cellTop[c]] >= keep * h - EPS);
                    }
                    for (int r = 0; r < g.rooms; r++) {
                        int lo = -1, hi = -1;
                        for (int i = g.roomCellStart[r]; i < g.roomCellStart[r + 1]; i++) {
                            int c = g.roomCellList[i];
                            if (lo < 0 || g.cellX0[c] < g.cellX0[lo]) lo = c;
                            if (hi < 0 || g.cellX1[c] > g.cellX1[hi]) hi = c;
                        }
                        int w = g.cellX1[hi] - g.cellX0[lo];
                        assertTrue(at + " room " + r, w + off[g.cellRight[hi]] - off[g.cellLeft[lo]] >= keep * w - EPS);
                    }
                }
            }
        }
    }

    @Test
    public void groupsInASlotStartAtDifferentTimesAndAllRestBeforeTheNextSlot() {
        TilePlan plan = skyline(5, TILES[0]);
        MoveClock clock = plan.motion.clock;
        int n = plan.graph.groups;
        int[] before = new int[n], after = new int[n];
        float[] shown = new float[n];
        List<Double> spreads = new ArrayList<>();
        int busy = 0, staggered = 0;
        long k0 = clock.slot(TilePlanTest.NOW);
        for (long k = k0; k < k0 + 300; k++) {
            plan.motion.glide(k, before, after);
            double start = clock.start(k), first = Double.MAX_VALUE, last = 0;
            int movers = 0;
            for (int i = 0; i < n; i++) {
                if (before[i] == after[i]) continue;
                movers++;
                first = Math.min(first, clock.delay(k, i));
                last = Math.max(last, clock.delay(k, i));
                assertTrue("slot " + k + " group " + i, start + clock.delay(k, i) + clock.glide(k, i) <= clock.start(k + 1));
            }
            // all at rest from the end of the slot's glides until the next slot starts
            for (double t : new double[] {start + clock.staggerSeconds + clock.glideSeconds, clock.start(k + 1) - 1e-6}) {
                plan.motion.offsets(t, shown);
                for (int i = 0; i < n; i++) assertEquals("slot " + k, after[i], shown[i], 0f);
            }
            if (movers < 4) continue;
            busy++;
            spreads.add(last - first);
            // halfway through the starts some groups are on their way and some still wait
            plan.motion.offsets(start + (first + last) / 2, shown);
            int going = 0, waiting = 0;
            for (int i = 0; i < n; i++) {
                if (before[i] == after[i]) continue;
                if (shown[i] == before[i]) waiting++; else going++;
            }
            if (going > 0 && waiting > 0) staggered++;
        }
        Collections.sort(spreads);
        double median = spreads.get(spreads.size() / 2);
        assertTrue("median spread of starts " + median + " s", median > 0.6 * clock.staggerSeconds);
        assertTrue(staggered + " of " + busy + " slots staggered", busy > 200 && staggered > 0.9 * busy);
    }

    @Test
    public void quietSlotsComeAtAboutTheirShareAndBusySlotsVaryInShare() {
        Look.Motion motion = Look.NAGAYA.motion;
        for (long seed : new long[] {5, 11, 23}) {
            TilePlan plan = skyline(seed, TILES[0]);
            TileGraph g = plan.graph;
            MoveClock clock = plan.motion.clock;
            int n = g.groups;
            long total = 0;
            for (int length : g.groupLength) total += length;
            int[] before = new int[n], after = new int[n];
            List<Double> busy = new ArrayList<>();
            int free = 0, quiet = 0, slots = 3000;
            double sum = 0;
            long k0 = clock.slot(TilePlanTest.NOW);
            for (long k = k0; k < k0 + slots; k++) {
                plan.motion.glide(k, before, after);
                long moving = 0;
                for (int i = 0; i < n; i++) if (before[i] != after[i]) moving += g.groupLength[i];
                double share = moving / (double) total;
                sum += share;
                if (k - clock.checkpoint(k) >= clock.slotsPerCheckpoint - Look.NAGAYA.rhythm.homeSlots) continue;
                free++;
                if (moving == 0) quiet++; else busy.add(share);
            }
            Collections.sort(busy);
            double p10 = busy.get(busy.size() / 10), p90 = busy.get(busy.size() * 9 / 10), mean = sum / slots;
            String at = "seed " + seed + ": quiet " + quiet / (double) free + ", busy p10 " + p10 + " p90 " + p90 + ", mean " + mean;
            assertEquals(at, motion.quietShare, quiet / (double) free, 0.03);
            assertTrue(at, p90 >= 1.6 * p10);
            assertTrue(at, mean >= motion.moveShare - 0.03 && mean <= motion.moveShare + 0.05);
        }
    }
}
