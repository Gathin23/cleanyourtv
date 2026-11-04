package io.github.pbanddev.tatami;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

public class RoomColoursTest {
    private static final Look LOOK = Look.YOJOHAN;
    private static final int[] CARDS = LOOK.palette.cards;
    /** Epoch seconds in November 2026: the clock Home and the screensaver both read. */
    private static final double NOW = 1_793_500_000.0;
    private static final float EPS = 0.05f;

    private static int sum(int[] values) {
        int total = 0;
        for (int v : values) total += v;
        return total;
    }

    @Test
    public void quotasAreTheCardsFor64Rooms() {
        assertEquals(LOOK.palette.deck.length, CARDS.length);
        assertEquals(64, sum(CARDS));
        assertArrayEquals(CARDS, RoomColours.quotas(CARDS, 64));
    }

    @Test
    public void quotasStayInProportionForOtherRoomCounts() {
        for (int rooms : new int[] {1, 9, 30, 41, 63, 65, 100, 200}) {
            int[] quotas = RoomColours.quotas(CARDS, rooms);
            assertEquals(rooms, sum(quotas));
            for (int c = 0; c < CARDS.length; c++) {
                double share = (double) rooms * CARDS[c] / 64;
                assertTrue(rooms + " rooms, paint " + c + ": " + quotas[c] + " for " + share,
                        Math.abs(quotas[c] - share) < 1);
            }
        }
    }

    @Test
    public void dealHandsOutExactlyTheQuotas() {
        FloorPlan plan = new FloorPlan(LOOK);
        int[] paints = RoomColours.deal(plan, LOOK);
        assertEquals(plan.rooms, paints.length);
        int[] count = new int[LOOK.palette.deck.length];
        for (int p : paints) count[p]++;
        assertArrayEquals(RoomColours.quotas(CARDS, plan.rooms), count);
    }

    @Test
    public void shuffleHandsOutExactlyTheQuotasTheSameWayEachTime() {
        Look ember = Look.NAGAYA;
        assertEquals(64, sum(ember.palette.cards));
        assertEquals(ember.palette.deck.length, ember.palette.cards.length);
        int[] paints = RoomColours.shuffle(70, ember);
        int[] count = new int[ember.palette.deck.length];
        for (int p : paints) count[p]++;
        assertArrayEquals(RoomColours.quotas(ember.palette.cards, 70), count);
        assertArrayEquals(paints, RoomColours.shuffle(70, ember));
    }

    @Test
    public void touchingRoomsNeverSharePaintAtAnyTime() {
        FloorPlan plan = new FloorPlan(LOOK);
        int[] paints = RoomColours.deal(plan, LOOK);
        float angle = LOOK.camera.angleDeg;
        float w = Camera.planWidth(1920, 1080, angle), h = Camera.planHeight(1920, 1080, angle);
        Frame f = new Frame(plan.rooms);
        float[] edge = new float[4];
        Random random = new Random(1);
        for (int s = 0; s < 4000; s++) {
            double t = s % 2 == 0 ? random.nextDouble() * 100_000 : NOW + random.nextDouble() * 1_000_000;
            plan.layout(t, -w / 2, -h / 2, w, h, f);
            for (int i = 0; i < f.roomCount; i++) {
                for (int j = i + 1; j < f.roomCount; j++) {
                    if (PlanGeometry.sharedEdge(f, i, j, EPS, edge) > EPS) {
                        assertNotEquals("rooms " + i + " and " + j + " touch at t=" + t, paints[i], paints[j]);
                    }
                }
            }
        }
    }

    @Test
    public void hardRuleWinsOverTheCards() {
        // 3 rooms that all touch, but only 2 paints have cards
        boolean[][] triangle = {{false, true, true}, {true, false, true}, {true, true, false}};
        int[] paints = RoomColours.assign(new int[] {2, 1, 0}, triangle, new boolean[3][3], new Random(1));
        assertNotEquals(paints[0], paints[1]);
        assertNotEquals(paints[1], paints[2]);
        assertNotEquals(paints[0], paints[2]);
    }

    @Test
    public void touchingRoomsAvoidPaintsThatLookAlikeWhenTheyCan() {
        // a row of 3 rooms where paints 0 and 1 look alike, so paint 2 belongs in the middle
        boolean[][] row = {{false, true, false}, {true, false, true}, {false, true, false}};
        boolean[][] alike = new boolean[3][3];
        alike[0][1] = true;
        alike[1][0] = true;
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(2, RoomColours.assign(new int[] {1, 1, 1}, row, alike, new Random(seed))[1]);
        }
    }

    @Test
    public void sameSeedGivesTheSameDeal() {
        assertArrayEquals(RoomColours.deal(new FloorPlan(LOOK), LOOK), RoomColours.deal(new FloorPlan(LOOK), LOOK));
    }

    @Test
    public void labMatchesTheSrgbReference() {
        assertArrayEquals(new double[] {53.585, 0, 0}, RoomColours.lab(0xFF808080), 0.01);
        assertArrayEquals(new double[] {39.589, 38.928, 40.101}, RoomColours.lab(0xFFA03E1C), 0.01);
    }

    /** Pairs from Sharma, Wu and Dalal (2005), "The CIEDE2000 color-difference formula", table 1. */
    @Test
    public void ciede2000MatchesSharmaReferencePairs() {
        double[][] pairs = {
                {50, 2.6772, -79.7751, 50, 0, -82.7485, 2.0425},
                {50, 0, 0, 50, -1, 2, 2.3669},
                {50, 2.49, -0.001, 50, -2.49, 0.0011, 7.2195},
                {50, -0.001, 2.49, 50, 0.0011, -2.49, 4.7461},
                {50, 2.5, 0, 73, 25, -18, 27.1492},
                {50, 2.5, 0, 56, -27, -3, 31.9030},
                {60.2574, -34.0099, 36.2677, 60.4626, -34.1751, 39.4387, 1.2644},
                {2.0776, 0.0795, -1.135, 0.9033, -0.0636, -0.5514, 0.9082},
        };
        for (double[] p : pairs) {
            double[] a = {p[0], p[1], p[2]}, b = {p[3], p[4], p[5]};
            assertEquals(p[6], RoomColours.deltaE2000(a, b), 1e-4);
            assertEquals(p[6], RoomColours.deltaE2000(b, a), 1e-4);
        }
    }
}
