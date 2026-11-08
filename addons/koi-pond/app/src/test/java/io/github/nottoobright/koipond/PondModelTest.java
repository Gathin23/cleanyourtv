package io.github.nottoobright.koipond;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PondModelTest {

    private static final float DT = 1f / 30f;
    private static final float W = PondModel.W, H = PondModel.H;

    private final float[] xs = new float[256], ys = new float[256];
    private final int[] strengths = new int[256];

    /** Steps from frame `from` to `to` (exclusive) at 30 fps, with nowMs derived from the frame number. */
    private static void run(PondModel m, int from, int to) {
        for (int i = from; i < to; i++) m.step(DT, i * 1000L / 30L);
    }

    private int drainAll(PondModel m) {
        int total = 0, n;
        while ((n = m.drainDrops(xs, ys, strengths, xs.length)) > 0) total += n;
        return total;
    }

    @Test
    public void sameSeedGivesTheSameState() {
        PondModel a = new PondModel(42), b = new PondModel(42);
        run(a, 0, 300);
        run(b, 0, 300);
        for (int i = 0; i < a.koi.length; i++) {
            PondModel.Koi ka = a.koi[i], kb = b.koi[i];
            assertEquals(ka.x, kb.x, 0f);
            assertEquals(ka.y, kb.y, 0f);
            assertEquals(ka.heading, kb.heading, 0f);
            assertEquals(ka.speed, kb.speed, 0f);
            assertEquals(ka.depth, kb.depth, 0f);
            assertEquals(ka.wiggle, kb.wiggle, 0f);
            assertEquals(ka.pattern, kb.pattern);
            assertArrayEquals(ka.spineX, kb.spineX, 0f);
            assertArrayEquals(ka.spineY, kb.spineY, 0f);
        }
        for (int i = 0; i < a.fry.length; i++) {
            assertEquals(a.fry[i].x, b.fry[i].x, 0f);
            assertEquals(a.fry[i].y, b.fry[i].y, 0f);
            assertEquals(a.fry[i].heading, b.fry[i].heading, 0f);
        }
        assertEquals(a.pads.length, b.pads.length);
        for (int i = 0; i < a.pads.length; i++) {
            assertEquals(a.pads[i].x, b.pads[i].x, 0f);
            assertEquals(a.pads[i].y, b.pads[i].y, 0f);
        }
        assertArrayEquals(a.duckweed, b.duckweed);
        for (int i = 0; i < a.fireflies.length; i++) {
            assertEquals(a.fireflies[i].x, b.fireflies[i].x, 0f);
            assertEquals(a.fireflies[i].y, b.fireflies[i].y, 0f);
        }
        assertEquals(drainAll(a), drainAll(b));

        PondModel c = new PondModel(43);
        run(c, 0, 300);
        assertNotEquals(a.koi[0].x, c.koi[0].x, 0f);
    }

    @Test
    public void populationMatchesTheContract() {
        for (long seed = 1; seed <= 20; seed++) {
            PondModel m = new PondModel(seed);
            assertEquals(8, m.koi.length);
            for (PondModel.Koi k : m.koi) {
                assertTrue(k.length >= 26f && k.length <= 46f);
                assertEquals(PondModel.SPINE, k.spineX.length);
            }
            assertEquals(24, m.fry.length);
            assertTrue(m.pads.length >= 9 && m.pads.length <= 12);
            for (PondModel.Pad p : m.pads) assertTrue(p.r >= 14f && p.r <= 30f);
            assertEquals(16, m.fireflies.length);
            assertTrue("several hundred duckweed points", m.duckweed.length >= 200);
        }
    }

    @Test
    public void allSixVarietiesAppear() {
        for (long seed = 1; seed <= 20; seed++) {
            boolean[] seen = new boolean[6];
            for (PondModel.Koi k : new PondModel(seed).koi) seen[k.variety] = true;
            for (int v = 0; v < 6; v++) assertTrue("seed " + seed + " variety " + v, seen[v]);
        }
    }

    @Test
    public void spineKeepsItsSpacing() {
        PondModel m = new PondModel(7);
        run(m, 0, 1000);
        for (PondModel.Koi k : m.koi) {
            float spacing = k.length / (PondModel.SPINE - 1);
            for (int s = 1; s < PondModel.SPINE; s++) {
                float dx = k.spineX[s] - k.spineX[s - 1], dy = k.spineY[s] - k.spineY[s - 1];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                assertEquals(spacing, d, spacing * 0.05f);
            }
            assertEquals(k.x, k.spineX[0], 0f);
            assertEquals(k.y, k.spineY[0], 0f);
        }
    }

    @Test
    public void koiStayInsideThePondForHalfAnHour() {
        PondModel m = new PondModel(3);
        float tol = 2f;
        for (int i = 0; i < 30 * 60 * 30; i++) {
            m.step(DT, i * 1000L / 30L);
            for (PondModel.Koi k : m.koi) {
                assertTrue(k.x >= -tol && k.x <= W + tol && k.y >= -tol && k.y <= H + tol);
            }
            if ((i & 63) == 0) drainAll(m);
        }
    }

    @Test
    public void padsKeepOutOfTheCentre() {
        for (long seed = 1; seed <= 20; seed++) {
            PondModel m = new PondModel(seed);
            assertNoPadInCentre(m);
            run(m, 0, 3000);
            assertNoPadInCentre(m);
        }
    }

    private static void assertNoPadInCentre(PondModel m) {
        for (PondModel.Pad p : m.pads) {
            // The pad's rim, not only its centre, stays out of the central ellipse.
            float dx = Math.abs(p.x - W / 2f) / (W * 0.32f + p.r), dy = Math.abs(p.y - H / 2f) / (H * 0.30f + p.r);
            assertTrue(dx * dx + dy * dy >= 1f);
        }
    }

    @Test
    public void duckweedStaysInBounds() {
        for (long seed = 1; seed <= 20; seed++) {
            PondModel m = new PondModel(seed);
            assertDuckweedInBounds(m);
            run(m, 0, 2000);
            assertDuckweedInBounds(m);
        }
    }

    private static void assertDuckweedInBounds(PondModel m) {
        for (int p : m.duckweed) {
            int x = p >>> 16, y = p & 0xFFFF;
            assertTrue(x >= 0 && x <= PondModel.W - 1 && y >= 0 && y <= PondModel.H - 1);
        }
    }

    @Test
    public void startRainFadesInAndDrops() {
        PondModel m = new PondModel(5);
        m.startRain(0);
        int drops = 0;
        for (int i = 0; i <= 40 * 30; i++) {
            m.step(DT, i * 1000L / 30L);
            drops += drainAll(m);
        }
        assertTrue(m.rainLevel() > 0.9f);
        assertTrue(drops > 50);
    }

    @Test
    public void firstShowerComesWithinFifteenMinutes() {
        PondModel m = new PondModel(11);
        float max = 0f;
        for (int i = 0; i < 15 * 60 * 10; i++) {
            m.step(0.1f, i * 100L);
            if (i < 5 * 60 * 10) assertEquals(0f, m.rainLevel(), 0f);
            max = Math.max(max, m.rainLevel());
            drainAll(m);
        }
        assertTrue(max > 0.9f);
    }

    @Test
    public void gatherDrawsKoiIntoALooseRing() {
        PondModel m = new PondModel(9);
        run(m, 0, 60);
        float gx = W / 2f, gy = H / 2f;
        m.setGather(true, gx, gy);
        run(m, 60, 60 + 60 * 30);
        int near = 0;
        for (PondModel.Koi k : m.koi) {
            float d = (float) Math.hypot(k.x - gx, k.y - gy);
            if (d >= 30f && d <= 80f) near++;
        }
        assertTrue("koi on the ring: " + near, near >= 6);
    }

    @Test
    public void scatterQueuesADropAndSpeedsUpNearbyKoi() {
        PondModel m = new PondModel(13);
        run(m, 0, 30);
        drainAll(m);
        PondModel.Koi k = m.koi[0];
        float before = k.speed;
        float px = k.x + 5f, py = k.y;
        m.scatter(px, py);
        int n = m.drainDrops(xs, ys, strengths, xs.length);
        boolean strong = false;
        for (int i = 0; i < n; i++) {
            if (strengths[i] >= 500 && xs[i] == px && ys[i] == py) strong = true;
        }
        assertTrue(strong);
        run(m, 30, 45);
        assertTrue("speed " + before + " -> " + k.speed, k.speed > before + 5f);
    }

    @Test
    public void drainDropsRespectsMax() {
        PondModel m = new PondModel(17);
        assertEquals(0, m.drainDrops(xs, ys, strengths, xs.length));
        m.startRain(0);
        run(m, 0, 30 * 30);
        assertEquals(5, m.drainDrops(xs, ys, strengths, 5));
        assertEquals(0, m.drainDrops(xs, ys, strengths, 0));
        float[] smallX = new float[3], smallY = new float[3];
        int[] smallS = new int[3];
        assertEquals(3, m.drainDrops(smallX, smallY, smallS, 10));
        int n = m.drainDrops(xs, ys, strengths, xs.length);
        assertTrue(n > 0 && n <= 64);
        for (int i = 0; i < n; i++) assertTrue(strengths[i] >= 80 && strengths[i] <= 200);
        assertEquals(0, m.drainDrops(xs, ys, strengths, xs.length));
    }

    @Test
    public void stepClampsDt() {
        PondModel m = new PondModel(19);
        PondModel.Koi k = m.koi[0];
        float x = k.x, y = k.y;
        m.step(-1f, 0);
        assertEquals(x, k.x, 0f);
        assertEquals(y, k.y, 0f);
        m.step(10f, 10);
        assertTrue(Math.hypot(k.x - x, k.y - y) <= 0.1 * 50);
    }
}
