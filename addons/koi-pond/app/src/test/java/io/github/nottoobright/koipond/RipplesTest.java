package io.github.nottoobright.koipond;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

public class RipplesTest {

    private static final int W = 240, H = 135, CX = 120, CY = 67;

    private static int maxAbs(Ripples r) {
        int m = 0;
        for (int v : r.heights()) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }

    private static void assertBordersZero(Ripples r) {
        for (int x = 0; x < r.width(); x++) {
            assertEquals(0, r.at(x, 0));
            assertEquals(0, r.at(x, r.height() - 1));
        }
        for (int y = 0; y < r.height(); y++) {
            assertEquals(0, r.at(0, y));
            assertEquals(0, r.at(r.width() - 1, y));
        }
    }

    @Test
    public void dropRaisesTheCentre() {
        Ripples r = new Ripples(W, H);
        r.drop(CX, CY, 3, 256);
        assertEquals(256, r.at(CX, CY));
        assertEquals(0, r.at(CX + 3, CY));
        Ripples down = new Ripples(W, H);
        down.drop(CX, CY, 3, -256);
        assertEquals(-256, down.at(CX, CY));
    }

    @Test
    public void dropSpreads() {
        Ripples r = new Ripples(W, H);
        r.drop(CX, CY, 3, 256);
        assertEquals(0, r.at(CX + 10, CY));
        boolean reached = false;
        for (int s = 0; s < 15 && !reached; s++) {
            r.step();
            reached = r.at(CX + 10, CY) != 0;
        }
        assertTrue("ring did not reach 10 cells within 15 steps", reached);
    }

    @Test
    public void ringFadesWithinTwoSeconds() {
        Ripples r = new Ripples(W, H);
        r.drop(CX, CY, 3, 256);
        for (int s = 0; s < 60; s++) {
            r.step();
        }
        assertTrue("max " + maxAbs(r) + " after 60 steps", maxAbs(r) < 256 * 4 / 100);
    }

    @Test
    public void energyDecaysToNearZero() {
        Ripples r = new Ripples(W, H);
        r.drop(CX, CY, 4, 512);
        for (int s = 0; s < 120; s++) {
            r.step();
        }
        assertTrue("max " + maxAbs(r) + " after 120 steps", maxAbs(r) <= 2);
    }

    @Test
    public void bordersStayZero() {
        Ripples r = new Ripples(W, H);
        r.drop(3, 3, 6, 1000);
        r.drop(W - 4, H - 4, 6, -1000);
        r.drop(CX, 2, 8, 800);
        assertBordersZero(r);
        for (int s = 0; s < 50; s++) {
            r.step();
            assertBordersZero(r);
        }
    }

    @Test
    public void dropsAtOrOverTheEdgeDoNotThrow() {
        Ripples r = new Ripples(W, H);
        r.drop(0, 0, 5, 500);
        r.drop(W - 1, H - 1, 5, 500);
        r.drop(-20, -20, 5, 500);
        r.drop(W + 20, H + 20, 5, 500);
        r.drop(-3, CY, 6, 500);
        r.drop(CX, H + 2, 6, -500);
        r.drop(CX, CY, 0, 500);
        r.step();
        assertBordersZero(r);
        assertEquals(0, r.at(-1, 0));
        assertEquals(0, r.at(W, H));
    }

    @Test
    public void clearZeroes() {
        Ripples r = new Ripples(W, H);
        r.drop(CX, CY, 5, 400);
        for (int s = 0; s < 5; s++) {
            r.step();
        }
        r.clear();
        assertEquals(0, maxAbs(r));
        r.step();
        assertEquals(0, maxAbs(r));
    }

    @Test
    public void heightsStayWithinClampUnderManyDrops() {
        Ripples r = new Ripples(W, H);
        Random rng = new Random(42);
        for (int i = 0; i < 1000; i++) {
            int strength = (64 + rng.nextInt(4000)) * (rng.nextBoolean() ? 1 : -1);
            r.drop(rng.nextInt(W + 20) - 10, rng.nextInt(H + 20) - 10, 1 + rng.nextInt(8), strength);
            if (i % 3 == 0) {
                r.step();
            }
            assertTrue(maxAbs(r) <= Ripples.MAX_HEIGHT);
        }
        assertBordersZero(r);
        assertEquals(W * H, r.heights().length);
    }
}
