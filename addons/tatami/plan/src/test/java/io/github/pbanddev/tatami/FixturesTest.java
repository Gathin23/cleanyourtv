package io.github.pbanddev.tatami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class FixturesTest {

    private static final float EPS = 0.05f;
    private static final int[][] SCREENS = {{1920, 1080}, {1280, 720}};
    /** The fixtures' boxes in px at 1080p, unturned, by kind: tub, toilet, stairs. */
    private static final float[][] BOX = {{117.5f, 67.6f}, {40f, 60f}, {211.7f, 95.1f}};

    /** A minute of steps, then wall-clock times like the TV's. */
    private static double time(int i) {
        return i < 60 ? i * 23.1 : 1_793_500_000.0 + i * 401.3;
    }

    /** Px at 1080p (on screen at the middle zoom 1.075) in plan units on a screen this tall. */
    private static float px(float px, float screenH) {
        return px / 1080f * screenH / 1.075f;
    }

    /** From fixture k's box to its room's wall faces: left, top, right, bottom. */
    private static float[] faceGaps(Frame f, int k, float screenH) {
        float half = screenH * Look.YOJOHAN.walls.widthH / 2;
        int r = f.fixtureRoom[k];
        return new float[] {
                f.fixtureX[k] - (f.roomX[r] + half),
                f.fixtureY[k] - (f.roomY[r] + half),
                f.roomX[r] + f.roomW[r] - half - (f.fixtureX[k] + f.fixtureW[k]),
                f.roomY[r] + f.roomH[r] - half - (f.fixtureY[k] + f.fixtureH[k])};
    }

    @Test
    public void fixturesStayInsideTheirWallFacesWithTheirGapsAtAnyTime() {
        Scene scene = new Scene(Look.YOJOHAN);
        int seen = 0;
        for (int i = 0; i < 80; i++) {
            int[] screen = SCREENS[i % 2];
            float h = screen[1];
            scene.build(time(i), screen[0], screen[1], false, true, false);
            Frame f = scene.frame;
            for (int k = 0; k < f.fixtureCount; k++) {
                seen++;
                float[] gap = faceGaps(f, k, h);
                int kind = f.fixtureKind[k], turn = f.fixtureTurn[k];
                String at = "kind " + kind + " turn " + turn + " in room " + f.fixtureRoom[k] + " at t=" + time(i)
                        + ", " + screen[1] + "p, gaps " + Arrays.toString(gap);
                for (float g : gap) assertTrue("crosses a wall face: " + at, g >= -EPS);
                if (kind == Fixtures.TOILET) {
                    // turn 0 backs onto the top wall, then clockwise: right, bottom, left
                    float back = gap[(turn + 1) % 4];
                    assertTrue("toilet gap: " + at, back >= px(0.020f * 1080, h) && back <= px(0.040f * 1080, h));
                    assertEquals("toilet off centre: " + at, gap[turn % 4], gap[(turn + 2) % 4], EPS);
                } else if (kind == Fixtures.TUB) {
                    boolean corner = false;
                    for (int s = 0; s < 4; s++) corner |= inTubGap(gap[s], h) && inTubGap(gap[(s + 1) % 4], h);
                    assertTrue("tub not in a corner: " + at, corner);
                }
            }
        }
        assertTrue(seen > 0);
    }

    private static boolean inTubGap(float gap, float h) {
        return gap >= px(0.025f * 1080, h) - EPS && gap <= px(0.028f * 1080, h) + EPS;
    }

    @Test
    public void stairsSitFlushInTheCornerTheirArrowPointsInto() {
        Scene scene = new Scene(Look.YOJOHAN);
        int seen = 0;
        for (int i = 0; i < 80; i++) {
            scene.build(time(i), 1920, 1080, false, true, false);
            Frame f = scene.frame;
            for (int k = 0; k < f.fixtureCount; k++) {
                if (f.fixtureKind[k] != Fixtures.STAIRS) continue;
                seen++;
                int turn = f.fixtureTurn[k];
                float[] gap = faceGaps(f, k, 1080);
                // turn 0 runs right into the bottom-right corner, then clockwise
                String at = "turn " + turn + " at t=" + time(i) + ", gaps " + Arrays.toString(gap);
                assertEquals("end not flush: " + at, 0f, gap[(turn + 2) % 4], EPS);
                assertEquals("side not flush: " + at, 0f, gap[(turn + 3) % 4], EPS);
            }
        }
        assertTrue(seen > 0);
    }

    @Test
    public void fixturesKeepTheirRoomKindTurnAndSizeOverTime() {
        Scene scene = new Scene(Look.YOJOHAN);
        Frame f = scene.frame;
        scene.build(time(0), 1920, 1080, false, true, false);
        int count = f.fixtureCount;
        int[] room = Arrays.copyOf(f.fixtureRoom, count), kind = Arrays.copyOf(f.fixtureKind, count),
                turn = Arrays.copyOf(f.fixtureTurn, count);
        for (int i = 0; i < 80; i++) {
            scene.build(time(i), 1920, 1080, false, true, false);
            assertEquals(count, f.fixtureCount);
            for (int k = 0; k < count; k++) {
                assertEquals(room[k], f.fixtureRoom[k]);
                assertEquals(kind[k], f.fixtureKind[k]);
                assertEquals(turn[k], f.fixtureTurn[k]);
                int odd = turn[k] % 2;
                assertEquals(px(BOX[kind[k]][odd], 1080), f.fixtureW[k], px(1, 1080));
                assertEquals(px(BOX[kind[k]][1 - odd], 1080), f.fixtureH[k], px(1, 1080));
            }
        }
    }

    @Test
    public void aboutOneRoomInFiveHoldsAFixtureAndTheKindsFollowTheirWeights() {
        Scene scene = new Scene(Look.YOJOHAN);
        scene.build(0, 1920, 1080, false, true, false);
        Frame f = scene.frame;
        int[] count = new int[3];
        for (int k = 0; k < f.fixtureCount; k++) count[f.fixtureKind[k]]++;
        float share = (float) f.fixtureCount / f.roomCount;
        assertTrue("share " + share, share >= 0.15f && share <= 0.25f);
        assertTrue("tub, toilet, stairs: " + Arrays.toString(count),
                count[Fixtures.TOILET] >= count[Fixtures.STAIRS] && count[Fixtures.STAIRS] >= count[Fixtures.TUB]
                        && count[Fixtures.TUB] > 0);
    }

    @Test
    public void everyShapeFillsItsBoxAtEveryTurnWithinTheVertexBudget() {
        float h = 1080f;
        Frame f = new Frame(1);
        SceneMesh mesh = new SceneMesh(1, Look.YOJOHAN);
        for (int kind = 0; kind < 3; kind++) {
            for (int turn = 0; turn < 4; turn++) {
                f.fixtureKind[0] = kind;
                f.fixtureTurn[0] = turn;
                f.fixtureX[0] = 300f;
                f.fixtureY[0] = -200f;
                f.fixtureW[0] = px(BOX[kind][turn % 2], h);
                f.fixtureH[0] = px(BOX[kind][1 - turn % 2], h);
                mesh.floats = 0;
                FixtureShapes.add(mesh, f, 0, h, Look.YOJOHAN);
                String at = "kind " + kind + " turn " + turn;
                assertTrue(at, mesh.floats / SceneMesh.FLOATS_PER_VERTEX <= FixtureShapes.MAX_VERTICES);
                float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
                for (int i = 0; i < mesh.floats; i += SceneMesh.FLOATS_PER_VERTEX) {
                    x0 = Math.min(x0, mesh.data[i]);
                    x1 = Math.max(x1, mesh.data[i]);
                    y0 = Math.min(y0, mesh.data[i + 1]);
                    y1 = Math.max(y1, mesh.data[i + 1]);
                }
                // the stairs' dot sits on the free-end line, so it reaches 1.75 px past the box
                float tolerance = px(2, h);
                assertEquals(at, f.fixtureX[0], x0, tolerance);
                assertEquals(at, f.fixtureY[0], y0, tolerance);
                assertEquals(at, f.fixtureX[0] + f.fixtureW[0], x1, tolerance);
                assertEquals(at, f.fixtureY[0] + f.fixtureH[0], y1, tolerance);
            }
        }
    }
}
