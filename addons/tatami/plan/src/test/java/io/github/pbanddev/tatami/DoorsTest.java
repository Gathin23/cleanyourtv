package io.github.pbanddev.tatami;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class DoorsTest {

    private static final Look LOOK = Look.YOJOHAN;
    private static final double EPOCH = 1_793_500_000.0;
    private static final float EPS = 0.05f;
    private static final double ANGLE_EPS = 1e-9;

    /** Early times and wall-clock times like the TV's, on two screen sizes. */
    private static double time(int i) {
        return i % 2 == 0 ? i * 7.3 : EPOCH + i * 61.7;
    }

    private static int height(int i) {
        return i % 3 == 0 ? 720 : 1080;
    }

    private static Scene build(Scene scene, int i) {
        int h = height(i);
        scene.build(time(i), h * 16 / 9, h, true, false, true);
        return scene;
    }

    @Test
    public void swingStaysBetweenShutAndOpenAndPausesAtBoth() {
        Scene scene = new Scene(LOOK);
        double open = Math.toRadians(LOOK.doors.swingMaxDeg), step = 1.0 / 60;
        int steps = 12 * 60;   // more than 2 swings
        int walls = scene.layout.rooms() - 1;
        int[] present = new int[walls], shutRun = new int[walls], openRun = new int[walls];
        int[] longestShut = new int[walls], longestOpen = new int[walls];
        for (int s = 0; s < steps; s++) {
            scene.build(EPOCH + s * step, 1920, 1080, true, false, true);
            Frame f = scene.frame;
            for (int d = 0; d < f.doorCount; d++) {
                double a = f.doorOpening[d];
                assertTrue("opening " + a, a >= 0 && a <= open + ANGLE_EPS);
                int w = f.doorWall[d];
                present[w]++;
                shutRun[w] = a == 0 ? shutRun[w] + 1 : 0;
                openRun[w] = a >= open - ANGLE_EPS ? openRun[w] + 1 : 0;
                longestShut[w] = Math.max(longestShut[w], shutRun[w]);
                longestOpen[w] = Math.max(longestOpen[w], openRun[w]);
            }
        }
        int followed = 0;
        for (int w = 0; w < walls; w++) {
            if (present[w] < steps) continue;
            followed++;
            assertTrue("wall " + w + " never pauses shut", longestShut[w] * step >= 0.5);
            assertTrue("wall " + w + " never pauses open", longestOpen[w] * step >= 0.5);
        }
        assertTrue("no door stayed for the whole run", followed > 0);
    }

    @Test
    public void everyDoorSitsOnItsWallClearOfTheWallsEnds() {
        Scene scene = new Scene(LOOK);
        int doors = 0;
        for (int i = 0; i < 150; i++) {
            Frame f = build(scene, i).frame;
            float length = height(i) * LOOK.doors.lengthH, gap = height(i) * LOOK.doors.joinGapH;
            for (int d = 0; d < f.doorCount; d++) {
                int w = f.doorWall[d];
                boolean vertical = f.wallVertical[w];
                String at = "door " + d + " on wall " + w + " at t=" + time(i);
                // the hinge is on the wall's centre line
                assertEquals(at, vertical ? f.wallX0[w] : f.wallY0[w], vertical ? f.doorX[d] : f.doorY[d], EPS);
                float hinge = vertical ? f.doorY[d] : f.doorX[d];
                float end = hinge + f.doorAlong[d] * length;
                float lo = Math.min(hinge, end), hi = Math.max(hinge, end);
                float from = vertical ? f.wallY0[w] : f.wallX0[w], to = vertical ? f.wallY1[w] : f.wallX1[w];
                float clear = Math.min(gap, (to - from - length) / 2);
                assertTrue(at + ": too near the wall's start", lo - from >= clear - EPS);
                assertTrue(at + ": too near the wall's end", to - hi >= clear - EPS);
                doors++;
            }
        }
        assertTrue("no doors", doors > 0);
    }

    /** Joins slide past a door: doors ride their wall and never hide, jump or pop back. */
    @Test
    public void doorsNeverPopInOrOutOrJump() {
        Scene scene = new Scene(LOOK);
        double step = 1.0 / 60;
        int[] walls = null;
        float[] x = null, y = null;
        float most = 1080 * 0.01f;
        for (int s = 0; s < 60 * 60; s++) {
            scene.build(EPOCH + s * step, 1920, 1080, true, false, true);
            Frame f = scene.frame;
            int n = f.doorCount;
            int[] nowWalls = Arrays.copyOf(f.doorWall, n);
            float[] nowX = Arrays.copyOf(f.doorX, n), nowY = Arrays.copyOf(f.doorY, n);
            if (walls != null) {
                assertArrayEquals("the doors changed at frame " + s, walls, nowWalls);
                for (int d = 0; d < n; d++) {
                    float moved = (float) Math.hypot(nowX[d] - x[d], nowY[d] - y[d]);
                    assertTrue("door on wall " + walls[d] + " jumped " + moved + " at frame " + s, moved < most);
                }
            }
            walls = nowWalls;
            x = nowX;
            y = nowY;
        }
        assertTrue("no doors", walls.length > 0);
    }

    @Test
    public void sameSeedAndTimeGiveTheSameDoors() {
        Scene a = new Scene(LOOK), b = new Scene(LOOK);
        for (int i = 0; i < 40; i++) {
            build(b, 39 - i);   // a different past changes nothing
            Frame fa = build(a, i).frame, fb = build(b, i).frame;
            assertTrue(fa.doorCount > 0);
            assertEquals(fa.doorCount, fb.doorCount);
            int n = fa.doorCount;
            assertArrayEquals(Arrays.copyOf(fa.doorWall, n), Arrays.copyOf(fb.doorWall, n));
            assertArrayEquals(Arrays.copyOf(fa.doorAlong, n), Arrays.copyOf(fb.doorAlong, n));
            assertArrayEquals(Arrays.copyOf(fa.doorSide, n), Arrays.copyOf(fb.doorSide, n));
            assertArrayEquals(Arrays.copyOf(fa.doorX, n), Arrays.copyOf(fb.doorX, n), 0f);
            assertArrayEquals(Arrays.copyOf(fa.doorY, n), Arrays.copyOf(fb.doorY, n), 0f);
            assertArrayEquals(Arrays.copyOf(fa.doorOpening, n), Arrays.copyOf(fb.doorOpening, n), 0.0);
        }
    }

    @Test
    public void eachWallKeepsItsHingeEndAndSwingSide() {
        Scene scene = new Scene(LOOK);
        int walls = scene.layout.rooms() - 1;
        int[] along = new int[walls], side = new int[walls];
        for (int i = 0; i < 150; i++) {
            Frame f = build(scene, i).frame;
            for (int d = 0; d < f.doorCount; d++) {
                int w = f.doorWall[d];
                if (along[w] == 0) {
                    along[w] = f.doorAlong[d];
                    side[w] = f.doorSide[d];
                }
                assertEquals("wall " + w, along[w], f.doorAlong[d]);
                assertEquals("wall " + w, side[w], f.doorSide[d]);
            }
        }
        // and they are drawn at random: both ends and both sides show up
        assertNotEquals(0, Arrays.stream(along).filter(s -> s > 0).count());
        assertNotEquals(0, Arrays.stream(along).filter(s -> s < 0).count());
        assertNotEquals(0, Arrays.stream(side).filter(s -> s > 0).count());
        assertNotEquals(0, Arrays.stream(side).filter(s -> s < 0).count());
    }
}
