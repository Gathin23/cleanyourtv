package io.github.pbanddev.tatami;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

/** Nagaya's tile from {@link SkylineBuilder}: valid at both tile sizes, about 15.5 rooms per H², steps by width. */
public class SkylineBuilderTest {
    private static final int[][] TILES = {{270, 174}, {135, 87}};
    private static final int SEEDS = 16;

    private static Look look(long seed, int[] tile) {
        return TilePlanTest.look(seed, tile[0], tile[1]);
    }

    /** Each room's step count: where its next cell's top or bottom differs, a cell at a time from left to right. */
    private static int[] steps(TileLayout tile) {
        int[] steps = new int[tile.rooms];
        Integer[] order = new Integer[tile.cells()];
        for (int c = 0; c < order.length; c++) order[c] = c;
        Arrays.sort(order, (a, b) -> tile.cellRoom[a] != tile.cellRoom[b]
                ? Integer.compare(tile.cellRoom[a], tile.cellRoom[b]) : Integer.compare(tile.cellX0[a], tile.cellX0[b]));
        for (int i = 1; i < order.length; i++) {
            int a = order[i - 1], b = order[i];
            if (tile.cellRoom[a] != tile.cellRoom[b]) continue;
            if (tile.cellY0[a] != tile.cellY0[b]) steps[tile.cellRoom[b]]++;
            if (tile.cellY1[a] != tile.cellY1[b]) steps[tile.cellRoom[b]]++;
        }
        return steps;
    }

    /** Each room's width in H, from its leftmost cell edge to its rightmost. */
    private static double[] widths(TileLayout tile, float unitH) {
        int[] x0 = new int[tile.rooms], x1 = new int[tile.rooms];
        Arrays.fill(x0, Integer.MAX_VALUE);
        Arrays.fill(x1, Integer.MIN_VALUE);
        for (int c = 0; c < tile.cells(); c++) {
            x0[tile.cellRoom[c]] = Math.min(x0[tile.cellRoom[c]], tile.cellX0[c]);
            x1[tile.cellRoom[c]] = Math.max(x1[tile.cellRoom[c]], tile.cellX1[c]);
        }
        double[] out = new double[tile.rooms];
        for (int r = 0; r < tile.rooms; r++) out[r] = (x1[r] - x0[r]) * (double) unitH;
        return out;
    }

    private static int band(double width) {
        return width < 0.3 ? 0 : width <= 0.5 ? 1 : 2;
    }

    @Test
    public void everySeedBuildsATileTheGraphAcceptsAtBothSizes() {
        for (int[] size : TILES) {
            for (long seed = 1; seed <= SEEDS; seed++) {
                Look look = look(seed, size);
                // throws, naming the rule, on any tile the contract does not allow
                TileGraph graph = new TileGraph(new SkylineBuilder().build(look.plan), look);
                assertTrue(graph.rooms > 0);
            }
        }
    }

    @Test
    public void noRoomEverHasThreeSteps() {
        for (int[] size : TILES) {
            for (long seed = 1; seed <= SEEDS; seed++) {
                int[] steps = steps(new SkylineBuilder().build(look(seed, size).plan));
                for (int r = 0; r < steps.length; r++) assertTrue("seed " + seed + " room " + r, steps[r] <= 2);
            }
        }
    }

    @Test
    public void aboutFifteenRoomsPerH2InAMixOfBoxesLsAndTwoSteps() {
        int[] mix = new int[3];
        for (int[] size : TILES) {
            for (long seed = 1; seed <= SEEDS; seed++) {
                Look look = look(seed, size);
                TileLayout tile = new SkylineBuilder().build(look.plan);
                double area = size[0] * size[1] * (double) look.plan.unitH * look.plan.unitH;
                double density = tile.rooms / area;
                assertTrue("seed " + seed + ": " + density + " rooms per H^2", density >= 12.7 && density <= 16.4);
                if (size != TILES[0]) continue;
                for (int s : steps(tile)) mix[s]++;
            }
        }
        double rooms = mix[0] + mix[1] + mix[2];
        String at = Arrays.toString(mix);
        // between 30 / 59 / 11 as seen on screen and 26 / 57 / 17 over all rooms, give or take a few
        assertTrue(at, mix[0] / rooms >= 0.20 && mix[0] / rooms <= 0.32);
        assertTrue(at, mix[1] / rooms >= 0.53 && mix[1] / rooms <= 0.66);
        assertTrue(at, mix[2] / rooms >= 0.09 && mix[2] / rooms <= 0.20);
    }

    @Test
    public void widerRoomsHaveMoreSteps() {
        int[][] mix = new int[3][3];
        for (long seed = 1; seed <= SEEDS; seed++) {
            Look look = look(seed, TILES[0]);
            TileLayout tile = new SkylineBuilder().build(look.plan);
            int[] steps = steps(tile);
            double[] width = widths(tile, look.plan.unitH);
            for (int r = 0; r < tile.rooms; r++) mix[band(width[r])][steps[r]]++;
        }
        double[] share = new double[3];
        double[] mean = new double[3];
        for (int b = 0; b < 3; b++) {
            int rooms = mix[b][0] + mix[b][1] + mix[b][2];
            assertTrue("band " + b + " has rooms", rooms > 0);
            share[b] = mix[b][2] / (double) rooms;
            mean[b] = (mix[b][1] + 2.0 * mix[b][2]) / rooms;
        }
        String at = Arrays.deepToString(mix);
        // under 0.3 H: half boxes, half Ls; 0.3 to 0.5 H: 20 / 70 / 10; over 0.5 H: mostly two steps
        assertTrue(at, mix[0][2] == 0);
        double narrowBoxes = mix[0][0] / (double) (mix[0][0] + mix[0][1]);
        assertTrue(at, narrowBoxes >= 0.4 && narrowBoxes <= 0.6);
        assertTrue(at, share[1] >= 0.05 && share[1] <= 0.2);
        assertTrue(at, share[2] >= 0.5);
        assertTrue(at, mean[0] < mean[1] && mean[1] < mean[2]);
    }
}
