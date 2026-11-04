package io.github.pbanddev.tatami;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class FloorPlanTest {

    private static final Look LOOK = Look.YOJOHAN;
    private static final float SCREEN_H = 1080f;
    /** The plan Scene lays out for a 1080p screen: plan units are screen pixels at zoom 1. */
    private static final float W = Camera.planWidth(1920, SCREEN_H, LOOK.camera.angleDeg);
    private static final float H = Camera.planHeight(1920, SCREEN_H, LOOK.camera.angleDeg);
    private static final float EPS = 0.05f;
    /** Epoch seconds in November 2026: the clock Home and the screensaver both read. */
    private static final double NOW = 1_793_500_000.0;
    private static final long[] SEEDS = {LOOK.plan.seed, 1, 2, 3, 4, 5, 6, 8};

    private static Frame layout(FloorPlan plan, double t) {
        Frame frame = new Frame(plan.rooms);
        plan.layout(t, -W / 2, -H / 2, W, H, frame);
        return frame;
    }

    private static double[] times(int count) {
        double[] out = new double[count];
        for (int i = 0; i < count; i++) out[i] = i % 2 == 0 ? i * 13.7 : NOW + i * 97.3;
        return out;
    }

    @Test
    public void roomsFillThePlanExactlyAtAnyTime() {
        FloorPlan plan = new FloorPlan(LOOK);
        for (double t : times(60)) {
            Frame f = layout(plan, t);
            assertEquals(plan.rooms, f.roomCount);
            double area = 0;
            for (int i = 0; i < f.roomCount; i++) area += (double) f.roomW[i] * f.roomH[i];
            assertEquals("t=" + t, (double) W * H, area, W * H * 1e-4);
        }
    }

    @Test
    public void roomsNeverOverlapAndStayInsideThePlan() {
        FloorPlan plan = new FloorPlan(LOOK);
        for (double t : times(60)) {
            Frame f = layout(plan, t);
            for (int i = 0; i < f.roomCount; i++) {
                assertTrue(f.roomW[i] > 0 && f.roomH[i] > 0);
                assertTrue(f.roomX[i] >= -W / 2 - EPS && f.roomX[i] + f.roomW[i] <= W / 2 + EPS);
                assertTrue(f.roomY[i] >= -H / 2 - EPS && f.roomY[i] + f.roomH[i] <= H / 2 + EPS);
                for (int j = i + 1; j < f.roomCount; j++) {
                    float ox = Math.min(f.roomX[i] + f.roomW[i], f.roomX[j] + f.roomW[j]) - Math.max(f.roomX[i], f.roomX[j]);
                    float oy = Math.min(f.roomY[i] + f.roomH[i], f.roomY[j] + f.roomH[j]) - Math.max(f.roomY[i], f.roomY[j]);
                    assertTrue("rooms " + i + " and " + j + " overlap at t=" + t, ox <= EPS || oy <= EPS);
                }
            }
        }
    }

    @Test
    public void sameSeedAndTimeGiveTheSameFrame() {
        Frame a = layout(new FloorPlan(LOOK), NOW);
        Frame b = layout(new FloorPlan(LOOK), NOW);
        assertArrayEquals(a.roomX, b.roomX, 0f);
        assertArrayEquals(a.roomY, b.roomY, 0f);
        assertArrayEquals(a.roomW, b.roomW, 0f);
        assertArrayEquals(a.roomH, b.roomH, 0f);
        assertArrayEquals(a.wallX0, b.wallX0, 0f);
        assertArrayEquals(a.wallY0, b.wallY0, 0f);
        assertArrayEquals(a.wallX1, b.wallX1, 0f);
        assertArrayEquals(a.wallY1, b.wallY1, 0f);
    }

    @Test
    public void wallsMoveOverTime() {
        FloorPlan plan = new FloorPlan(LOOK);
        Frame a = layout(plan, NOW);
        Frame b = layout(plan, NOW + 2);
        float moved = 0;
        for (int i = 0; i < a.roomCount; i++) moved = Math.max(moved, Math.abs(a.roomW[i] - b.roomW[i]));
        assertTrue(moved > 0.5f);
    }

    @Test
    public void everyWallIsARoomBoundaryOnBothSidesAndThereIsOneFewerThanRooms() {
        FloorPlan plan = new FloorPlan(LOOK);
        for (double t : times(60)) {
            Frame f = layout(plan, t);
            assertEquals(f.roomCount - 1, f.wallCount);
            for (int k = 0; k < f.wallCount; k++) {
                boolean vertical = f.wallVertical[k];
                float line = vertical ? f.wallX0[k] : f.wallY0[k];
                float from = vertical ? f.wallY0[k] : f.wallX0[k], to = vertical ? f.wallY1[k] : f.wallX1[k];
                assertEquals(line, vertical ? f.wallX1[k] : f.wallY1[k], 0f);
                assertTrue("wall " + k + " has no length at t=" + t, to > from);
                float before = 0, after = 0;
                for (int i = 0; i < f.roomCount; i++) {
                    float lo = vertical ? f.roomY[i] : f.roomX[i], hi = lo + (vertical ? f.roomH[i] : f.roomW[i]);
                    float near = vertical ? f.roomX[i] : f.roomY[i], far = near + (vertical ? f.roomW[i] : f.roomH[i]);
                    float overlap = Math.min(hi, to) - Math.max(lo, from);
                    if (overlap <= 0) continue;
                    if (Math.abs(far - line) < EPS) before += overlap;
                    if (Math.abs(near - line) < EPS) after += overlap;
                }
                assertEquals("wall " + k + " at t=" + t, to - from, before, 0.5f);
                assertEquals("wall " + k + " at t=" + t, to - from, after, 0.5f);
            }
        }
    }

    @Test
    public void everyRoomStaysAtLeastItsSmallestSize() {
        for (long seed : SEEDS) {
            FloorPlan plan = new FloorPlan(LOOK, seed);
            for (double t : times(400)) {
                Frame f = layout(plan, t);
                for (int i = 0; i < f.roomCount; i++) {
                    assertTrue(f.roomMinW[i] > 0 && f.roomMinH[i] > 0);
                    assertTrue("seed " + seed + " room " + i + " narrower than its smallest at t=" + t, f.roomW[i] >= f.roomMinW[i] - EPS);
                    assertTrue("seed " + seed + " room " + i + " shorter than its smallest at t=" + t, f.roomH[i] >= f.roomMinH[i] - EPS);
                }
            }
        }
    }

    /** A wall sliding past a windmill centre's corner, or past a wall in line with it, lines up only for a moment. */
    private static final double MOMENT = 3;

    @Test
    public void atLeastOneRoomInTwelveIsAWindmillCentre() {
        for (long seed : SEEDS) {
            FloorPlan plan = new FloorPlan(LOOK, seed);
            for (double t : times(40)) {
                int mills = Math.max(windmillCentres(layout(plan, t)), windmillCentres(layout(plan, t + MOMENT)));
                assertTrue("seed " + seed + ": " + mills + " windmill centres in " + plan.rooms + " rooms at t=" + t,
                        mills * 12 >= plan.rooms);
            }
        }
    }

    @Test
    public void noStraightWallRunsFromOneEdgeOfThePlanToTheOtherForMoreThanAMoment() {
        for (long seed : SEEDS) {
            FloorPlan plan = new FloorPlan(LOOK, seed);
            for (double t : times(400)) {
                assertTrue("seed " + seed + ": a wall crosses the plan at t=" + t,
                        !crossesThePlan(layout(plan, t)) || !crossesThePlan(layout(plan, t + MOMENT)));
            }
        }
    }

    private static int windmillCentres(Frame f) {
        int mills = 0;
        for (int k = 0; k < f.roomCount; k++) if (PlanGeometry.isWindmillCentre(f, k, 1f)) mills++;
        return mills;
    }

    private static boolean crossesThePlan(Frame f) {
        for (float[] line : straightWalls(f)) {
            boolean vertical = line[0] == 1;
            float start = vertical ? -H / 2 : -W / 2, end = -start;
            if (line[2] <= start + 1 && line[3] >= end - 1) return true;
        }
        return false;
    }

    @Test
    public void everyRoomKeepsItsShortSideAndShapeAtAnyTime() {
        float shortest = LOOK.plan.minSideH * SCREEN_H;
        for (long seed : SEEDS) {
            FloorPlan plan = new FloorPlan(LOOK, seed);
            for (double t : times(3000)) {
                Frame f = layout(plan, t);
                for (int i = 0; i < f.roomCount; i++) {
                    float lo = Math.min(f.roomW[i], f.roomH[i]), hi = Math.max(f.roomW[i], f.roomH[i]);
                    assertTrue("seed " + seed + " room " + i + " short side " + lo + " at t=" + t, lo >= shortest - EPS);
                    assertTrue("seed " + seed + " room " + i + " aspect " + hi / lo + " at t=" + t, hi <= LOOK.plan.maxAspect * lo);
                }
            }
        }
    }

    /** Joins collinear walls that touch: {vertical 1 or 0, coordinate, from, to}. */
    private static List<float[]> straightWalls(Frame f) {
        List<float[]> pieces = new ArrayList<>();
        for (int k = 0; k < f.wallCount; k++) {
            boolean v = f.wallVertical[k];
            pieces.add(new float[] {v ? 1 : 0, v ? f.wallX0[k] : f.wallY0[k], v ? f.wallY0[k] : f.wallX0[k], v ? f.wallY1[k] : f.wallX1[k]});
        }
        pieces.sort((a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : a[1] != b[1] ? Float.compare(a[1], b[1]) : Float.compare(a[2], b[2]));
        List<float[]> lines = new ArrayList<>();
        for (float[] p : pieces) {
            float[] last = lines.isEmpty() ? null : lines.get(lines.size() - 1);
            if (last != null && last[0] == p[0] && Math.abs(last[1] - p[1]) < EPS && p[2] <= last[3] + EPS) {
                last[3] = Math.max(last[3], p[3]);
            } else {
                lines.add(p.clone());
            }
        }
        return lines;
    }
}
