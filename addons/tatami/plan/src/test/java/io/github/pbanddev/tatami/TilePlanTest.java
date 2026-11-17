package io.github.pbanddev.tatami;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class TilePlanTest {
    static final double NOW = 1_793_500_000.0;
    private static final double EPS = 1e-3;
    private static final int[][] TILES = {{270, 174}, {135, 87}};

    /** Nagaya with another seed and tile size. */
    static Look look(long seed, int tileW, int tileH) {
        Look n = Look.NAGAYA;
        Look.Plan p = n.plan;
        return new Look(p.withTile(seed, tileW, tileH),
                n.rhythm, n.motion, n.palette, n.walls, n.camera, n.doors, n.fixtures, n.post);
    }

    static TilePlan plan(long seed, int[] tile) {
        return new TilePlan(look(seed, tile[0], tile[1]), new PlainRowsBuilder());
    }

    /** Times all over a slot: its glides' start, middle and end, and the rest after them; and checkpoints. */
    private static double[] times(TileMotion m, int count, long seed) {
        Random random = new Random(seed);
        MoveClock clock = m.clock;
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            long k = clock.slot(NOW + random.nextDouble() * 4000 - (i % 5 == 0 ? 1e6 : 0));
            double s = clock.start(k), d = clock.staggerSeconds + clock.glideSeconds;
            double[] at = {s, s + d * random.nextDouble(), s + d / 2, s + d + 1e-3, (s + d + clock.start(k + 1)) / 2};
            out[i] = at[i % at.length];
        }
        return out;
    }

    /** Cell boxes of copy (0, 0) at time t, in steps: x0, y0, x1, y1 per cell. */
    private static double[][] cells(TilePlan plan, double t) {
        TileGraph g = plan.graph;
        float[] off = new float[g.groups];
        plan.motion.offsets(t, off);
        double[][] box = new double[g.cells][];
        for (int c = 0; c < g.cells; c++) {
            box[c] = new double[] {g.cellX0[c] + off[g.cellLeft[c]], g.cellY0[c] + off[g.cellTop[c]],
                    g.cellX1[c] + off[g.cellRight[c]], g.cellY1[c] + off[g.cellBottom[c]]};
        }
        return box;
    }

    /** Every pair of cell copies that share a stretch of edge at time t: cell a of copy (0, 0), cell b of copy (i, j). */
    private static Set<String> touching(TilePlan plan, double[][] box) {
        TileGraph g = plan.graph;
        Set<String> out = new HashSet<>();
        for (int a = 0; a < g.cells; a++) {
            for (int j = -1; j <= 1; j++) {
                for (int i = -2; i <= 2; i++) {
                    double dx = i * g.width + j * g.shift, dy = j * g.height;
                    for (int b = 0; b < g.cells; b++) {
                        double[] p = box[a], q = box[b];
                        double x0 = q[0] + dx, x1 = q[2] + dx, y0 = q[1] + dy, y1 = q[3] + dy;
                        double ox = Math.min(p[2], x1) - Math.max(p[0], x0), oy = Math.min(p[3], y1) - Math.max(p[1], y0);
                        if (ox > EPS && oy > EPS && !(a == b && i == 0 && j == 0)) {
                            throw new AssertionError("cells " + a + " and " + b + " (" + i + ", " + j + ") overlap");
                        }
                        if ((Math.abs(ox) <= EPS && oy > EPS) || (Math.abs(oy) <= EPS && ox > EPS)) out.add(a + " " + b + " " + i + " " + j);
                    }
                }
            }
        }
        return out;
    }

    @Test
    public void cellsTileTheLatticeAndKeepTheirNeighboursAtAnyTime() {
        for (long seed : new long[] {5, 1, 2}) {
            for (int[] tile : TILES) {
                TilePlan plan = plan(seed, tile);
                // t = 0 is a checkpoint: every line home
                Set<String> home = touching(plan, cells(plan, 0.0));
                for (double t : times(plan.motion, 40, seed)) {
                    double[][] box = cells(plan, t);
                    double area = 0;
                    for (double[] b : box) area += (b[2] - b[0]) * (b[3] - b[1]);
                    assertEquals("seed " + seed + " t=" + t, (double) tile[0] * tile[1], area, 0.05);
                    assertEquals("seed " + seed + " t=" + t, home, touching(plan, box));
                }
            }
        }
    }

    @Test
    public void everyMinimumAndDoorHostHoldsAtRestAndInGlides() {
        for (long seed : new long[] {5, 3, 4}) {
            for (int[] tile : TILES) {
                TilePlan plan = plan(seed, tile);
                TileGraph g = plan.graph;
                float[] off = new float[g.groups];
                for (double t : times(plan.motion, 300, seed)) {
                    plan.motion.offsets(t, off);
                    for (int c = 0; c < g.cells; c++) {
                        boolean alone = g.roomCellStart[g.cellRoom[c] + 1] - g.roomCellStart[g.cellRoom[c]] == 1;
                        double w = g.cellX1[c] + off[g.cellRight[c]] - g.cellX0[c] - off[g.cellLeft[c]];
                        double h = g.cellY1[c] + off[g.cellBottom[c]] - g.cellY0[c] - off[g.cellTop[c]];
                        assertTrue("cell width " + w, w >= (alone ? Math.max(g.minCell, g.minCross) : g.minCell) - EPS);
                        assertTrue("cell height " + h, h >= g.minCross - EPS);
                    }
                    for (int r = 0; r < g.rooms; r++) {
                        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                        for (int i = g.roomCellStart[r]; i < g.roomCellStart[r + 1]; i++) {
                            int c = g.roomCellList[i];
                            lo = Math.min(lo, g.cellX0[c] + off[g.cellLeft[c]]);
                            hi = Math.max(hi, g.cellX1[c] + off[g.cellRight[c]]);
                        }
                        assertTrue("room width", hi - lo >= g.minCross - EPS);
                    }
                    for (int p = 0; p < g.pieces; p++) {
                        double length = g.pieceTo[p] + off[g.pieceToGroup[p]] - g.pieceFrom[p] - off[g.pieceFromGroup[p]];
                        assertTrue("a step or T-offset of " + length, length >= g.minStep - EPS);
                        assertTrue(length >= g.pieceMinLength[p] - EPS);
                        if (g.pieceHost[p]) assertTrue("door host " + length, length >= g.hostMin - EPS);
                    }
                }
            }
        }
    }

    @Test
    public void roomsAreOneToThreeJoinedCellsAndWallsPartRooms() {
        for (long seed = 1; seed <= 8; seed++) {
            TilePlan plan = plan(seed, TILES[0]);
            TileGraph g = plan.graph;
            int[] cells = new int[g.rooms];
            for (int c = 0; c < g.cells; c++) cells[g.cellRoom[c]]++;
            int multi = 0;
            for (int n : cells) {
                assertTrue(n >= 1 && n <= 3);
                if (n > 1) multi++;
            }
            assertTrue("some rooms are L, T, U or Z", multi > g.rooms / 5);
            for (int p = 0; p < g.pieces; p++) assertTrue(g.pieceRoomA[p] != g.pieceRoomB[p]);
        }
    }

    @Test
    public void doorHostsAreOnePerPairAtMostOnLongPieces() {
        int hosts = 0, pairs = 0;
        for (long seed = 1; seed <= 8; seed++) {
            TileGraph g = plan(seed, TILES[0]).graph;
            int[] perPair = new int[g.pairs];
            for (int p = 0; p < g.pieces; p++) {
                if (!g.pieceHost[p]) continue;
                perPair[g.piecePair[p]]++;
                assertTrue(g.pieceTo[p] - g.pieceFrom[p] >= g.hostMin);
                hosts++;
            }
            for (int n : perPair) assertTrue(n <= 1);
            pairs += g.pairs;
        }
        double share = hosts / (double) pairs;
        assertTrue("hosts on " + share + " of touching pairs", share > 0.6 * Look.NAGAYA.doors.chance && share < 1.1 * Look.NAGAYA.doors.chance);
    }

    @Test
    public void nothingMovesBetweenGlidesAndAllIsHomeAtCheckpoints() {
        TilePlan plan = plan(5, TILES[0]);
        MoveClock clock = plan.motion.clock;
        float[] a = new float[plan.graph.groups], b = new float[a.length];
        long k0 = clock.slot(NOW);
        int moved = 0;
        for (long k = k0; k < k0 + 200; k++) {
            double window = clock.staggerSeconds + clock.glideSeconds;
            double end = clock.start(k) + window, next = clock.start(k + 1);
            plan.motion.offsets(end + 1e-6, a);
            plan.motion.offsets(next - 1e-6, b);
            assertArrayEquals("slot " + k, a, b, 0f);
            plan.motion.offsets(clock.start(k) + window / 2, b);
            if (!Arrays.equals(a, b)) moved++;
        }
        assertTrue("walls glide in most slots", moved > 140);
        for (long c = clock.checkpoint(k0); c < k0 + 400; c += clock.slotsPerCheckpoint) {
            plan.motion.offsets(c * clock.slotSeconds, a);
            for (float o : a) assertEquals(0f, o, 0f);
        }
    }

    @Test
    public void sameSeedAndTimeGiveTheSameFrameWhicheverWayTheClockWent() {
        Scene a = new Scene(Look.NAGAYA), b = new Scene(Look.NAGAYA);
        Random random = new Random(3);
        for (int i = 0; i < 60; i++) {
            double t = NOW + random.nextDouble() * 3000;
            a.build(t, 1920, 1080, true, true, true);
            b.build(t + (i % 2 == 0 ? -1700 : 900), 1920, 1080, true, true, true);
            b.build(t, 1920, 1080, true, true, true);
            assertEquals(a.frame.cellCount, b.frame.cellCount);
            assertArrayEquals(Arrays.copyOf(a.frame.cellX, a.frame.cellCount), Arrays.copyOf(b.frame.cellX, b.frame.cellCount), 0f);
            assertArrayEquals(Arrays.copyOf(a.frame.wallY0, a.frame.wallCount), Arrays.copyOf(b.frame.wallY0, b.frame.wallCount), 0f);
            assertArrayEquals(Arrays.copyOf(a.mesh.data, a.mesh.floats), Arrays.copyOf(b.mesh.data, b.mesh.floats), 0f);
        }
    }

    @Test
    public void framesHoldWholePiecesBetweenRoomsAndFlagTheirHosts() {
        Scene s = new Scene(Look.NAGAYA);
        float bar = Look.NAGAYA.doors.hostBars * Look.NAGAYA.doors.lengthH * 1080, step = Look.NAGAYA.plan.minStepUnits * Look.NAGAYA.plan.unitH * 1080;
        int hosts = 0;
        for (double t : times(((TilePlan) s.layout).motion, 50, 9)) {
            s.build(t, 1920, 1080, true, true, true);
            Frame f = s.frame;
            assertFalse(f.clipped);
            for (int w = 0; w < f.wallCount; w++) {
                float length = f.wallX1[w] - f.wallX0[w] + f.wallY1[w] - f.wallY0[w];
                assertTrue(f.wallRoomA[w] != f.wallRoomB[w]);
                assertTrue("piece " + length, length >= step - 1e-2);
                assertTrue(length >= f.wallMinLength[w] - 1e-2);
                if (f.wallDoor[w]) {
                    hosts++;
                    assertTrue("host " + length, length >= bar - 1e-2);
                }
            }
            for (int r = 0; r < f.roomCount; r++) assertTrue(Float.isNaN(f.roomMinW[r]) || f.roomW[r] >= f.roomMinW[r] - 1e-2);
        }
        assertTrue(hosts > 0);
    }

    @Test
    public void everySeedAndBothTileSizesBuildQuickly() {
        double[] ms = new double[100];
        View view = new View();
        view.set(1920, 1080, -20f, 0.89f, 0.0, 0.0, Scene.margin(Look.NAGAYA));
        for (int round = 0; round < 2; round++) {
            for (long seed = 1; seed <= 50; seed++) {
                for (int s = 0; s < 2; s++) {
                    long start = System.nanoTime();
                    TilePlan plan = plan(seed, TILES[s]);
                    plan.layout(NOW, view, plan.newFrame());
                    ms[(int) (seed - 1) * 2 + s] = (System.nanoTime() - start) / 1e6;
                }
            }
        }
        Arrays.sort(ms);
        assertTrue("median build " + ms[50] + " ms", ms[50] < 20);
    }
}
