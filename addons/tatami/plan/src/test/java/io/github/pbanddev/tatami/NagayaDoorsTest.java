package io.github.pbanddev.tatami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class NagayaDoorsTest {
    private static final double NOW = 1_793_500_000.0, FRAME = 1.0 / 60;
    private static final int W = 1920, H = 1080;
    /** Nagaya's camera: its rest zoom, and its rest roll and the ends of its roll. */
    private static final float ZOOM = Look.NAGAYA.camera.restZoom;
    private static final float[] ROLLS = {Look.NAGAYA.camera.angleDeg, Look.NAGAYA.camera.rollFromDeg, Look.NAGAYA.camera.rollToDeg};
    private static final float EPS = 1e-2f;

    /** Nagaya with another seed. */
    private static Look look(long seed) {
        Look n = Look.NAGAYA;
        Look.Plan p = n.plan;
        return new Look(p.withTile(seed, p.tileWUnits, p.tileHUnits),
                n.rhythm, n.motion, n.palette, n.walls, n.camera, n.doors, n.fixtures, n.post);
    }

    /** The scene's frame at time t, seen with this roll over plan point (cx, cy), doors on. */
    private static Frame frame(Scene s, double t, float roll, double cx, double cy) {
        s.view.set(W, H, roll, ZOOM, cx, cy, Scene.margin(s.look));
        s.view.matrix(s.matrix);
        s.fill(t, true, false, null);
        return s.frame;
    }

    private static MoveClock clock(Scene s) {
        return ((TilePlan) s.layout).motion.clock;
    }

    /** Times at rest, early in a slot's glides, mid-way and at their end. */
    private static double time(MoveClock clock, Random random, int i) {
        long k = clock.slot(NOW + random.nextDouble() * 5000);
        double s = clock.start(k), d = clock.staggerSeconds + clock.glideSeconds;
        double[] at = {s - 0.5, s + 0.1 * d, s + 0.5 * d, s + d, s + d * random.nextDouble()};
        return at[i % at.length];
    }

    private static float along(Frame f, int w, float x, float y) {
        return f.wallVertical[w] ? y : x;
    }

    @Test
    public void everyDoorFollowsTheOneTrackWithItsOwnRipple() {
        Scene s = new Scene(Look.NAGAYA);
        DoorTrack track = new DoorTrack(Look.NAGAYA.doors, Look.NAGAYA.plan.seed);
        Random random = new Random(1);
        boolean apart = false;
        for (int i = 0; i < 200; i++) {
            double t = NOW + random.nextDouble() * 3000;
            s.build(t, W, H, true, false, true);
            Frame f = s.frame;
            assertTrue(f.doorCount > 0);
            for (int d = 0; d < f.doorCount; d++) {
                assertEquals(track.opening(t, f.wallId[f.doorWall[d]]), f.doorOpening[d], 0);
                apart |= f.doorOpening[d] != f.doorOpening[0];
            }
        }
        assertTrue("doors ripple apart", apart);
    }

    @Test
    public void doorsSitOnlyOnHostsOneEachAndClearOfThePieceEnds() {
        Look l = Look.NAGAYA;
        float length = H * l.doors.lengthH, gap = H * l.doors.joinGapH, host = l.doors.hostBars * length;
        Random random = new Random(2);
        for (long seed = 1; seed <= 4; seed++) {
            Scene s = new Scene(look(seed));
            for (int i = 0; i < 60; i++) {
                double t = time(clock(s), random, i);
                Frame f = frame(s, t, ROLLS[i % 3], random.nextDouble() * 2000 - 1000, random.nextDouble() * 1200 - 600);
                int hosts = 0;
                for (int w = 0; w < f.wallCount; w++) if (f.wallDoor[w]) hosts++;
                assertEquals("every host shows its door", hosts, f.doorCount);
                Set<Integer> walls = new HashSet<>();
                for (int d = 0; d < f.doorCount; d++) {
                    int w = f.doorWall[d];
                    String at = "seed " + seed + " t=" + t + " door on piece " + f.wallId[w];
                    assertTrue(at, f.wallDoor[w] && walls.add(w));
                    // the hinge on the piece's centre line, the bar inside it a gap from its ends, or centred
                    assertEquals(at, f.wallVertical[w] ? f.wallX0[w] : f.wallY0[w], f.wallVertical[w] ? f.doorX[d] : f.doorY[d], 0);
                    float from = along(f, w, f.wallX0[w], f.wallY0[w]), to = along(f, w, f.wallX1[w], f.wallY1[w]);
                    float middle = along(f, w, f.doorX[d], f.doorY[d]) + f.doorAlong[d] * length / 2;
                    assertTrue(at + " host " + (to - from), to - from >= host - EPS);
                    if (to - from >= length + 2 * gap) {
                        assertTrue(at, middle - length / 2 >= from + gap - EPS && middle + length / 2 <= to - gap + EPS);
                    } else {
                        assertEquals(at, (from + to) / 2, middle, EPS);
                    }
                }
            }
        }
    }

    @Test
    public void everyDoorOpensClockwiseOnScreenAndMostSwingUpOrRight() {
        int doors = 0, upOrRight = 0;
        float[] p = new float[6];
        for (long seed = 1; seed <= 8; seed++) {
            Scene s = new Scene(look(seed));
            Set<Integer> seen = new HashSet<>();
            for (float roll : ROLLS) {
                Frame f = frame(s, NOW, roll, 0, 0);
                float[] m = s.matrix;
                for (int d = 0; d < f.doorCount; d++) {
                    boolean vertical = f.wallVertical[f.doorWall[d]];
                    // the hinge, a step along the bar (u) and a step toward the swing (v), on screen with y down
                    float x = f.doorX[d], y = f.doorY[d], a = f.doorAlong[d], b = f.doorSide[d];
                    float[] plan = {x, y, x + (vertical ? 0 : a), y + (vertical ? a : 0), x + (vertical ? b : 0), y + (vertical ? 0 : b)};
                    for (int i = 0; i < 3; i++) {
                        p[2 * i] = (m[0] * plan[2 * i] + m[4] * plan[2 * i + 1] + m[12] + 1) / 2 * W;
                        p[2 * i + 1] = (1 - (m[1] * plan[2 * i] + m[5] * plan[2 * i + 1] + m[13])) / 2 * H;
                    }
                    float ux = p[2] - p[0], uy = p[3] - p[1], vx = p[4] - p[0], vy = p[5] - p[1];
                    assertTrue("seed " + seed + " roll " + roll + " door on piece " + f.wallId[f.doorWall[d]] + " opens clockwise",
                            ux * vy - uy * vx > 0);
                    if (seen.add(f.wallId[f.doorWall[d]])) {
                        doors++;
                        if (vertical ? b > 0 : b < 0) upOrRight++;
                    }
                }
            }
        }
        double share = upOrRight / (double) doors;
        assertTrue(doors + " doors, " + share + " swing up or right", doors > 100 && share > 0.68 && share < 0.88);
    }

    @Test
    public void anOpenDoorSwingsWithinTheRoomItOpensInto() {
        float radius = H * Look.NAGAYA.doors.radiusH, wall = H * Look.NAGAYA.walls.widthH;
        int doors = 0;
        for (long seed = 1; seed <= 8; seed++) {
            Scene s = new Scene(look(seed));
            MoveClock clock = clock(s);
            for (int i = 0; i < 40; i++) {
                // wide open, the walls near the end of a glide
                long k = clock.slot(NOW + i * 41.3);
                Frame f = frame(s, clock.start(k) + 2.3, ROLLS[i % 3], 0, 0);
                for (int d = 0; d < f.doorCount; d++) {
                    if (Math.abs(f.doorX[d]) > 600 || Math.abs(f.doorY[d]) > 350) continue;
                    boolean vertical = f.wallVertical[f.doorWall[d]];
                    float ux = vertical ? 0 : f.doorAlong[d], uy = vertical ? f.doorAlong[d] : 0;
                    float vx = vertical ? f.doorSide[d] : 0, vy = vertical ? 0 : f.doorSide[d];
                    float hx = f.doorX[d] + vx * wall / 2, hy = f.doorY[d] + vy * wall / 2;
                    int room = PlanGeometry.roomAt(f, hx + ux + vx, hy + uy + vy);
                    for (int q = 1; q <= 10; q++) {
                        double a = f.doorOpening[d] * q / 10;
                        float x = hx + (float) (radius * (ux * Math.cos(a) + vx * Math.sin(a)));
                        float y = hy + (float) (radius * (uy * Math.cos(a) + vy * Math.sin(a)));
                        assertEquals("seed " + seed + " door on piece " + f.wallId[f.doorWall[d]] + " at " + Math.round(Math.toDegrees(a)) + " deg",
                                f.roomId[room], f.roomId[PlanGeometry.roomAt(f, x, y)]);
                    }
                    doors++;
                }
            }
        }
        assertTrue(doors + " doors", doors > 1000);
    }

    @Test
    public void atMostOneDoorPerRoomPairOnAboutTheWantedShareOfPairsAndAlmostEveryRoomHasOne() {
        int hosts = 0, rooms = 0, without = 0;
        for (long seed = 1; seed <= 8; seed++) {
            TileGraph g = new TilePlan(look(seed), new PlainRowsBuilder()).graph;
            int[] perPair = new int[g.pairs], perRoom = new int[g.rooms];
            int here = 0;
            for (int p = 0; p < g.pieces; p++) {
                if (!g.pieceHost[p]) continue;
                if (++perPair[g.piecePair[p]] > 1) throw new AssertionError("seed " + seed + ": 2 doors on a pair");
                perRoom[g.pieceRoomA[p]]++;
                perRoom[g.pieceRoomB[p]]++;
                here++;
            }
            double share = here / (double) g.pairs;
            assertTrue("seed " + seed + ": doors on " + share + " of touching pairs", share > 0.24 && share < 0.32);
            for (int n : perRoom) if (n == 0) without++;
            hosts += here;
            rooms += g.rooms;
        }
        double perRoom = hosts / (double) rooms;
        assertTrue(perRoom + " doors per room", perRoom > 0.7 && perRoom < 0.95);
        assertTrue(without + " of " + rooms + " rooms without a door", without <= 0.03 * rooms);
    }

    @Test
    public void aDoorRidesItsPieceThroughAGlideWithoutAJump() {
        Scene s = new Scene(Look.NAGAYA);
        Look.Doors doors = Look.NAGAYA.doors;
        float length = H * doors.lengthH, gap = H * doors.joinGapH;
        // the fastest a line glides, in plan units per frame, and a little more for the line under the door
        double fastest = Math.PI / 2 * 2 * Look.NAGAYA.motion.maxUnits * H * Look.NAGAYA.plan.unitH / Look.NAGAYA.rhythm.glideSeconds * FRAME;
        MoveClock clock = clock(s);
        long k0 = clock.slot(NOW);
        int moved = 0, followed = 0;
        for (long k = k0; k < k0 + 6; k++) {
            float[][] last = null;
            for (double t = clock.start(k) - 0.2; t < clock.start(k) + clock.glideSeconds + 0.2; t += FRAME) {
                Frame f = frame(s, t, ROLLS[0], 0, 0);
                // per door: piece id, hinge x, y, share along its piece (NaN where held from an end), screen-inside flag
                float[][] now = new float[f.doorCount][];
                for (int d = 0; d < f.doorCount; d++) {
                    int w = f.doorWall[d];
                    float from = along(f, w, f.wallX0[w], f.wallY0[w]), to = along(f, w, f.wallX1[w], f.wallY1[w]);
                    float middle = along(f, w, f.doorX[d], f.doorY[d]) + f.doorAlong[d] * length / 2;
                    boolean free = middle - length / 2 > from + gap + 0.5f && middle + length / 2 < to - gap - 0.5f;
                    float share = free ? (middle - from) / (to - from) : Float.NaN;
                    boolean inside = Math.abs(f.doorX[d]) < 600 && Math.abs(f.doorY[d]) < 350;
                    now[d] = new float[] {f.wallId[w], f.doorX[d], f.doorY[d], share, inside ? 1 : 0};
                }
                if (last != null) {
                    for (float[] a : last) {
                        if (a[4] == 0) continue;
                        float[] b = nearest(now, a);
                        assertTrue("door on piece " + (int) a[0] + " left or jumped at t=" + t, b != null);
                        double step = Math.hypot(b[1] - a[1], b[2] - a[2]);
                        assertTrue("door on piece " + (int) a[0] + " moved " + step + " in a frame", step <= fastest * 1.5);
                        if (!Float.isNaN(a[3]) && !Float.isNaN(b[3])) assertEquals(a[3], b[3], 1e-4);
                        followed++;
                        if (step > 1e-3) moved++;
                    }
                }
                last = now;
            }
        }
        assertTrue(followed + " door frames, " + moved + " moving", followed > 1000 && moved > 50);
    }

    /** The door in {@code doors} on the same piece as {@code a} nearest to it, if within a bar's length. */
    private static float[] nearest(float[][] doors, float[] a) {
        float[] best = null;
        double least = H * Look.NAGAYA.doors.lengthH;
        for (float[] b : doors) {
            double d = Math.hypot(b[1] - a[1], b[2] - a[2]);
            if (b[0] == a[0] && d < least) {
                least = d;
                best = b;
            }
        }
        return best;
    }
}
