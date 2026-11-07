package io.github.nottoobright.koipond;

import java.util.Random;

/**
 * Everything that moves in the pond, in pond pixels (W x H): koi, fry schools, lily pads, duckweed, fireflies and
 * the rain schedule. Pure Java and deterministic for a seed: one Random, used only here. Everything is allocated in
 * the constructor, so step() can run every frame on the render thread without feeding the garbage collector.
 */
final class PondModel {

    static final int W = 480, H = 270;
    static final int KOHAKU = 0, SHOWA = 1, OGON = 2, ASAGI = 3, CHAGOI = 4, SANKE = 5, SPINE = 10;

    private static final float PI = (float) Math.PI, TWO_PI = 2f * PI;
    private static final int KOI_COUNT = 8, SCHOOLS = 3, PER_SCHOOL = 8, FIREFLY_COUNT = 16;

    // Koi steering. Edge avoidance starts EDGE_ZONE px before the EDGE margin, so turns begin early and stay gentle.
    private static final float EDGE = 16f, EDGE_ZONE = 50f, LOOK_AHEAD = 12f;
    private static final float KOI_TURN = 0.8f, KOI_FLEE_SPEED = 45f;
    private static final float ORBIT_RATE = 0.15f; // rad/s round the breathing ring: 58..72 px radius gives 9..11 px/s

    private static final float FRY_SPEED = 20f, FRY_FLEE_SPEED = 70f, FRY_SEPARATION = 3f;
    private static final float SCATTER_RADIUS = 120f, FLEE_SECONDS = 3f;

    private static final long MINUTE_MS = 60_000L, RAIN_FADE_MS = 30_000L;

    private static final int DROP_CAPACITY = 64;

    final Koi[] koi;
    final Fry[] fry;
    final Pad[] pads;
    final int[] duckweed;
    final Firefly[] fireflies;

    private final Random rng;
    private double time; // seconds of model time, summed from the clamped dt

    // Pending ripple drops, a ring buffer: when full the oldest drop is overwritten.
    private final float[] dropX = new float[DROP_CAPACITY], dropY = new float[DROP_CAPACITY];
    private final int[] dropStrength = new int[DROP_CAPACITY];
    private int dropHead, dropCount;

    // Rain: the schedule is anchored on the first nowMs that step() sees.
    private final long firstRainDelayMs;
    private boolean clockStarted;
    private long rainStartMs, rainEndMs;
    private float rainLevel, rainAccumulator;

    // Breathing gather.
    private boolean gatherOn;
    private float gatherX = W / 2f, gatherY = H / 2f, gatherWeight, orbitPhase;
    private final int[] slot = new int[KOI_COUNT];
    private final int[] order = new int[KOI_COUNT];
    private final float[] orderAngle = new float[KOI_COUNT];

    // Fry schools: a slowly wandering target each, and per-step centroid and mean velocity.
    private final float[] schoolX = new float[SCHOOLS], schoolY = new float[SCHOOLS];
    private final float[] schoolAngle = new float[SCHOOLS], schoolTurn = new float[SCHOOLS];
    private final float[] schoolTimer = new float[SCHOOLS];
    private final float[] centreX = new float[SCHOOLS], centreY = new float[SCHOOLS];
    private final float[] meanVx = new float[SCHOOLS], meanVy = new float[SCHOOLS];
    private final int[] schoolSize = new int[SCHOOLS];

    // Duckweed: origins, the cluster each point belongs to, and each cluster's current whole-pixel drift.
    private final int[] weedX, weedY, weedCluster;
    private final float[] clusterPhase;
    private final int[] clusterDx, clusterDy;

    /** One koi. The head leads; the spine follows it like a chain. */
    static final class Koi {
        float x, y;
        float heading;
        float speed;
        final int variety;
        final float length;
        float depth;
        float wiggle;
        final float[] spineX = new float[SPINE], spineY = new float[SPINE];
        final long pattern;

        private float cruise, cruiseTimer;
        private float wanderAngle, wanderRate, wanderTimer;
        private float turn;
        private int buddy = -1;
        private float buddyTimer;
        private float depthTarget, depthTimer;
        private boolean surfacing;
        private float surfaceTimer;
        private float flee, fleeX, fleeY;
        private float orbitRadius;

        Koi(int variety, float length, long pattern) {
            this.variety = variety;
            this.length = length;
            this.pattern = pattern;
        }
    }

    /** One small fish of a school. */
    static final class Fry {
        float x, y, heading;
        final int color;
        final int school;

        private float vx, vy;
        private final float pace;
        private float flee, fleeX, fleeY;

        Fry(int color, int school, float pace) {
            this.color = color;
            this.school = school;
            this.pace = pace;
        }
    }

    /** A lily pad. x, y drift a pixel or two around the origin it was placed at. */
    static final class Pad {
        float x, y;
        final float r;
        final float notch;
        final boolean lotus;
        final float phase;

        private final float originX, originY;

        Pad(float x, float y, float r, float notch, boolean lotus, float phase) {
            this.originX = this.x = x;
            this.originY = this.y = y;
            this.r = r;
            this.notch = notch;
            this.lotus = lotus;
            this.phase = phase;
        }
    }

    /** A firefly; the renderer shows it after dark. */
    static final class Firefly {
        float x, y;
        final float phase;

        private float angle, pace, homeX, homeY, homeTimer;

        Firefly(float phase) {
            this.phase = phase;
        }
    }

    PondModel(long seed) {
        rng = new Random(seed);
        koi = makeKoi();
        fry = makeFry();
        pads = makePads();

        int clusters = 6 + rng.nextInt(5);
        clusterPhase = new float[clusters];
        clusterDx = new int[clusters];
        clusterDy = new int[clusters];
        int[] tx = new int[clusters * 90], ty = new int[clusters * 90], tc = new int[clusters * 90];
        int n = makeDuckweed(clusters, tx, ty, tc);
        weedX = new int[n];
        weedY = new int[n];
        weedCluster = new int[n];
        System.arraycopy(tx, 0, weedX, 0, n);
        System.arraycopy(ty, 0, weedY, 0, n);
        System.arraycopy(tc, 0, weedCluster, 0, n);
        duckweed = new int[n];

        fireflies = makeFireflies();
        firstRainDelayMs = 6 * MINUTE_MS + (long) (rng.nextFloat() * 8 * MINUTE_MS);

        driftPads();
        driftDuckweed(true);
    }

    // ---- public API --------------------------------------------------------------------------------------------

    void step(float dt, long nowMs) {
        if (!(dt > 0f)) dt = 0f; // also catches NaN
        if (dt > 0.1f) dt = 0.1f;
        time += dt;

        stepRain(dt, nowMs);

        float gatherGoal = gatherOn ? 1f : 0f;
        gatherWeight += clamp(gatherGoal - gatherWeight, -dt / 2.5f, dt / 2.5f);
        orbitPhase = wrap(orbitPhase + ORBIT_RATE * dt);

        stepKoi(dt);
        stepSchools(dt);
        stepFry(dt);
        driftPads();
        driftDuckweed(false);
        stepFireflies(dt);
    }

    void setGather(boolean on, float x, float y) {
        if (!on) {
            gatherOn = false;
            return;
        }
        if (gatherOn && x == gatherX && y == gatherY) return; // already gathering there: keep the slots
        gatherOn = true;
        gatherX = x;
        gatherY = y;
        // Hand out ring slots in the order the koi already lie round the point, so nobody has to cross the circle.
        for (int i = 0; i < KOI_COUNT; i++) {
            order[i] = i;
            orderAngle[i] = (float) Math.atan2(koi[i].y - y, koi[i].x - x);
        }
        for (int i = 1; i < KOI_COUNT; i++) {
            int o = order[i];
            float a = orderAngle[o];
            int j = i - 1;
            while (j >= 0 && orderAngle[order[j]] > a) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = o;
        }
        for (int j = 0; j < KOI_COUNT; j++) {
            Koi k = koi[order[j]];
            slot[order[j]] = j;
            // Outside the largest ring (46 px, glow to about 51), so the koi circle round it, not under it.
            k.orbitRadius = 58f + 14f * rng.nextFloat();
        }
        orbitPhase = orderAngle[order[0]];
    }

    void scatter(float x, float y) {
        queueDrop(x, y, 600);
        for (Koi k : koi) {
            float dx = k.x - x, dy = k.y - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d >= SCATTER_RADIUS) continue;
            float level = 0.35f + 0.65f * (1f - d / SCATTER_RADIUS);
            if (level > k.flee) k.flee = level;
            if (d > 0.5f) {
                k.fleeX = dx / d;
                k.fleeY = dy / d;
            } else {
                k.fleeX = (float) Math.cos(k.heading);
                k.fleeY = (float) Math.sin(k.heading);
            }
        }
        for (Fry f : fry) {
            float dx = f.x - x, dy = f.y - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d >= SCATTER_RADIUS) continue;
            float level = 0.4f + 0.6f * (1f - d / SCATTER_RADIUS);
            if (level > f.flee) f.flee = level;
            if (d > 0.5f) {
                f.fleeX = dx / d;
                f.fleeY = dy / d;
            } else {
                float a = rng.nextFloat() * TWO_PI;
                f.fleeX = (float) Math.cos(a);
                f.fleeY = (float) Math.sin(a);
            }
        }
        // Move each nearby school's target away too, or the fry would dart straight back.
        for (int s = 0; s < SCHOOLS; s++) {
            float dx = schoolX[s] - x, dy = schoolY[s] - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d >= SCATTER_RADIUS) continue;
            if (d < 0.5f) {
                dx = 1f;
                dy = 0f;
                d = 1f;
            }
            float push = SCATTER_RADIUS - d;
            schoolX[s] = clamp(schoolX[s] + dx / d * push, 30f, W - 30f);
            schoolY[s] = clamp(schoolY[s] + dy / d * push, 25f, H - 25f);
            schoolAngle[s] = (float) Math.atan2(dy, dx);
        }
    }

    void startRain(long nowMs) {
        clockStarted = true;
        // If a shower is already falling, carry on from its current level instead of dropping to zero.
        rainStartMs = nowMs - (long) (rainLevelAt(nowMs) * RAIN_FADE_MS);
        rainEndMs = nowMs + rainDurationMs();
    }

    float rainLevel() {
        return rainLevel;
    }

    int drainDrops(float[] xs, float[] ys, int[] strengths, int max) {
        int n = Math.min(Math.min(dropCount, max), Math.min(xs.length, Math.min(ys.length, strengths.length)));
        if (n <= 0) return 0;
        for (int i = 0; i < n; i++) {
            int j = (dropHead + i) % DROP_CAPACITY;
            xs[i] = dropX[j];
            ys[i] = dropY[j];
            strengths[i] = dropStrength[j];
        }
        dropHead = (dropHead + n) % DROP_CAPACITY;
        dropCount -= n;
        return n;
    }

    // ---- rain --------------------------------------------------------------------------------------------------

    private void stepRain(float dt, long nowMs) {
        if (!clockStarted) {
            clockStarted = true;
            rainStartMs = nowMs + firstRainDelayMs;
            rainEndMs = rainStartMs + rainDurationMs();
        }
        if (nowMs >= rainEndMs) {
            rainStartMs = rainEndMs + 20 * MINUTE_MS + (long) (rng.nextFloat() * 20 * MINUTE_MS);
            rainEndMs = rainStartMs + rainDurationMs();
        }
        rainLevel = rainLevelAt(nowMs);
        rainAccumulator += rainLevel * 10f * dt;
        while (rainAccumulator >= 1f) {
            rainAccumulator -= 1f;
            queueDrop(6f + rng.nextFloat() * (W - 12f), 6f + rng.nextFloat() * (H - 12f), 80 + rng.nextInt(121));
        }
        if (rainLevel == 0f) rainAccumulator = 0f;
    }

    private float rainLevelAt(long nowMs) {
        if (!clockStarted || nowMs < rainStartMs || nowMs >= rainEndMs) return 0f;
        float in = (nowMs - rainStartMs) / (float) RAIN_FADE_MS;
        float out = (rainEndMs - nowMs) / (float) RAIN_FADE_MS;
        return Math.min(1f, Math.min(in, out));
    }

    private long rainDurationMs() {
        return 3 * MINUTE_MS + (long) (rng.nextFloat() * 3 * MINUTE_MS);
    }

    private void queueDrop(float x, float y, int strength) {
        if (dropCount == DROP_CAPACITY) {
            dropHead = (dropHead + 1) % DROP_CAPACITY;
            dropCount--;
        }
        int j = (dropHead + dropCount) % DROP_CAPACITY;
        dropX[j] = x;
        dropY[j] = y;
        dropStrength[j] = strength;
        dropCount++;
    }

    // ---- koi ---------------------------------------------------------------------------------------------------

    private void stepKoi(float dt) {
        float free = 1f - gatherWeight;
        for (int i = 0; i < koi.length; i++) {
            Koi k = koi[i];

            if ((k.cruiseTimer -= dt) <= 0f) {
                k.cruise = 8f + 8f * rng.nextFloat();
                k.cruiseTimer = 4f + 8f * rng.nextFloat();
            }
            if ((k.wanderTimer -= dt) <= 0f) {
                // Now and then a straight stretch, otherwise a gentle curve one way or the other.
                k.wanderRate = rng.nextFloat() < 0.25f ? 0f : (rng.nextFloat() - 0.5f) * 0.6f;
                k.wanderTimer = 2f + 6f * rng.nextFloat();
            }
            // The wander heading curves on its own and is pulled back towards the real heading, so other forces
            // can bend the path without the two drifting apart.
            k.wanderAngle = wrap(k.wanderAngle + k.wanderRate * dt
                    + wrap(k.heading - k.wanderAngle) * Math.min(1f, dt * 0.4f));

            float fx = (float) Math.cos(k.wanderAngle) * free;
            float fy = (float) Math.sin(k.wanderAngle) * free;
            float speedGoal = k.cruise;

            // Koi often swim in pairs: now and then one picks the nearest fish and keeps loosely beside it.
            if ((k.buddyTimer -= dt) <= 0f) {
                k.buddy = rng.nextFloat() < 0.55f ? nearestKoi(i) : -1;
                k.buddyTimer = 12f + 25f * rng.nextFloat();
            }
            if (k.buddy >= 0 && free > 0f) {
                Koi b = koi[k.buddy];
                float dx = b.x - k.x, dy = b.y - k.y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 150f) {
                    k.buddy = -1;
                } else {
                    float pull = clamp((d - 22f) / 40f, 0f, 1f);
                    if (d > 0.01f) {
                        fx += dx / d * pull * 0.8f * free;
                        fy += dy / d * pull * 0.8f * free;
                    }
                    fx += (float) Math.cos(b.heading) * 0.5f * free;
                    fy += (float) Math.sin(b.heading) * 0.5f * free;
                    speedGoal = speedGoal * 0.6f + b.speed * 0.4f + pull * 3f;
                }
            }

            // Separation: the head keeps clear of every other koi's body.
            for (int j = 0; j < koi.length; j++) {
                if (j == i) continue;
                Koi o = koi[j];
                float minD = 0.6f * Math.max(k.length, o.length);
                float reach = o.length + minD;
                if (Math.abs(k.x - o.x) > reach || Math.abs(k.y - o.y) > reach) continue;
                for (int s = 0; s < SPINE; s++) {
                    float dx = k.x - o.spineX[s], dy = k.y - o.spineY[s];
                    float d2 = dx * dx + dy * dy;
                    if (d2 >= minD * minD || d2 < 1e-6f) continue;
                    float d = (float) Math.sqrt(d2);
                    float w = (1f - d / minD) * 2.5f;
                    fx += dx / d * w;
                    fy += dy / d * w;
                }
            }

            // Pond edge, tested a little ahead of the head so the turn starts before the edge.
            float hx = (float) Math.cos(k.heading), hy = (float) Math.sin(k.heading);
            float px = k.x + hx * LOOK_AHEAD, py = k.y + hy * LOOK_AHEAD;
            float urgency = 0f;
            float lo = EDGE + EDGE_ZONE;
            float e;
            if (px < lo) {
                e = Math.min(2f, (lo - px) / EDGE_ZONE);
                fx += e * e * 3f;
                urgency = Math.max(urgency, e);
            } else if (px > W - lo) {
                e = Math.min(2f, (px - (W - lo)) / EDGE_ZONE);
                fx -= e * e * 3f;
                urgency = Math.max(urgency, e);
            }
            if (py < lo) {
                e = Math.min(2f, (lo - py) / EDGE_ZONE);
                fy += e * e * 3f;
                urgency = Math.max(urgency, e);
            } else if (py > H - lo) {
                e = Math.min(2f, (py - (H - lo)) / EDGE_ZONE);
                fy -= e * e * 3f;
                urgency = Math.max(urgency, e);
            }
            urgency = Math.min(urgency, 1f);

            if (k.flee > 0f) {
                fx += k.fleeX * k.flee * 3f;
                fy += k.fleeY * k.flee * 3f;
            }

            if (gatherWeight > 0f) {
                float a = orbitPhase + slot[i] * TWO_PI / KOI_COUNT;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                float r = k.orbitRadius > 0f ? k.orbitRadius : 65f;
                float dx = gatherX + ca * r - k.x, dy = gatherY + sa * r - k.y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float w = gatherWeight * 1.6f;
                if (d > 0.01f) {
                    float pull = Math.min(1f, d / 12f);
                    fx += dx / d * pull * w;
                    fy += dy / d * pull * w;
                }
                // Along the circle, so a koi that has reached its slot keeps swimming round with it.
                fx -= sa * 0.7f * w;
                fy += ca * 0.7f * w;
                float orbitSpeed = clamp(ORBIT_RATE * r + d * 0.15f, 6f, 14f);
                speedGoal += (orbitSpeed - speedGoal) * gatherWeight;
            }

            float goal = (fx * fx + fy * fy > 1e-8f) ? (float) Math.atan2(fy, fx) : k.heading;
            float diff = wrap(goal - k.heading);
            float maxTurn = KOI_TURN + k.flee * 2.2f + urgency * 1.4f;
            float want = clamp(diff * 1.8f, -maxTurn, maxTurn);
            // The turn rate itself eases, so a fish never snaps from one curve into another.
            k.turn += (want - k.turn) * Math.min(1f, dt * (3f + 6f * (urgency + k.flee)));
            k.heading = wrap(k.heading + k.turn * dt);

            if (k.surfacing) speedGoal *= 0.75f;
            speedGoal += (KOI_FLEE_SPEED - speedGoal) * k.flee;
            speedGoal *= 1f - Math.min(0.3f, Math.abs(k.turn) * 0.2f);
            k.speed += (speedGoal - k.speed) * Math.min(1f, dt * (0.6f + 5f * k.flee));

            k.x = clamp(k.x + (float) Math.cos(k.heading) * k.speed * dt, 1f, W - 1f);
            k.y = clamp(k.y + (float) Math.sin(k.heading) * k.speed * dt, 1f, H - 1f);
            followSpine(k);

            k.wiggle += dt * (2f + k.speed * 0.4f);
            if (k.wiggle > TWO_PI) k.wiggle -= TWO_PI;

            stepDepth(k, dt);
            k.flee = Math.max(0f, k.flee - dt / FLEE_SECONDS);
        }
    }

    private void stepDepth(Koi k, float dt) {
        if ((k.depthTimer -= dt) <= 0f) {
            k.depthTarget = 0.1f + 0.7f * rng.nextFloat();
            k.depthTimer = 8f + 14f * rng.nextFloat();
        }
        if (!k.surfacing && (k.surfaceTimer -= dt) <= 0f && k.flee < 0.05f) k.surfacing = true;

        float target, rate;
        if (k.surfacing) {
            target = 0.05f;
            rate = 0.6f;
        } else {
            target = Math.min(1f, k.depthTarget + 0.25f * rainLevel);
            rate = 0.12f;
        }
        k.depth += (target - k.depth) * Math.min(1f, dt * rate);

        if (k.surfacing && k.depth < 0.08f) {
            float hx = (float) Math.cos(k.heading), hy = (float) Math.sin(k.heading);
            queueDrop(k.x + hx * 1.5f, k.y + hy * 1.5f, 110 + rng.nextInt(21));
            k.surfacing = false;
            k.surfaceTimer = 20f + 40f * rng.nextFloat();
        }
    }

    private static void followSpine(Koi k) {
        float spacing = k.length / (SPINE - 1);
        k.spineX[0] = k.x;
        k.spineY[0] = k.y;
        // The direction the previous segment points backwards; each joint may bend at most MAX_BEND from it, so a
        // sharp turn curves the whole body a little instead of folding it in two.
        float px = -(float) Math.cos(k.heading), py = -(float) Math.sin(k.heading);
        for (int s = 1; s < SPINE; s++) {
            float dx = k.spineX[s] - k.spineX[s - 1], dy = k.spineY[s] - k.spineY[s - 1];
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float ux, uy;
            if (d < 1e-4f) {
                ux = px;
                uy = py;
            } else {
                ux = dx / d;
                uy = dy / d;
            }
            if (ux * px + uy * py < COS_BEND) {
                float side = px * uy - py * ux >= 0f ? SIN_BEND : -SIN_BEND;
                ux = px * COS_BEND - py * side;
                uy = px * side + py * COS_BEND;
            }
            k.spineX[s] = k.spineX[s - 1] + ux * spacing;
            k.spineY[s] = k.spineY[s - 1] + uy * spacing;
            px = ux;
            py = uy;
        }
    }

    private static final float MAX_BEND = 0.36f;
    private static final float COS_BEND = (float) Math.cos(MAX_BEND), SIN_BEND = (float) Math.sin(MAX_BEND);

    private int nearestKoi(int i) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int j = 0; j < koi.length; j++) {
            if (j == i) continue;
            float dx = koi[j].x - koi[i].x, dy = koi[j].y - koi[i].y;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = j;
            }
        }
        return best;
    }

    // ---- fry ---------------------------------------------------------------------------------------------------

    private void stepSchools(float dt) {
        for (int s = 0; s < SCHOOLS; s++) {
            if ((schoolTimer[s] -= dt) <= 0f) {
                schoolTurn[s] = (rng.nextFloat() - 0.5f) * 0.8f;
                schoolTimer[s] = 2f + 5f * rng.nextFloat();
            }
            float a = schoolAngle[s] + schoolTurn[s] * dt;
            float tx = schoolX[s], ty = schoolY[s];
            float cx = 0f, cy = 0f;
            if (tx < 60f) cx += 1f;
            if (tx > W - 60f) cx -= 1f;
            if (ty < 45f) cy += 1f;
            if (ty > H - 45f) cy -= 1f;
            if (gatherWeight > 0f) {
                float dx = tx - gatherX, dy = ty - gatherY;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 110f && d > 0.01f) {
                    cx += dx / d * 2f * gatherWeight;
                    cy += dy / d * 2f * gatherWeight;
                }
            }
            if (cx != 0f || cy != 0f) {
                float want = (float) Math.atan2(cy, cx);
                a += clamp(wrap(want - a), -1.2f * dt, 1.2f * dt);
            }
            schoolAngle[s] = wrap(a);
            schoolX[s] = clamp(tx + (float) Math.cos(a) * 14f * dt, 20f, W - 20f);
            schoolY[s] = clamp(ty + (float) Math.sin(a) * 14f * dt, 20f, H - 20f);
        }
    }

    private void stepFry(float dt) {
        for (int s = 0; s < SCHOOLS; s++) {
            centreX[s] = centreY[s] = meanVx[s] = meanVy[s] = 0f;
            schoolSize[s] = 0;
        }
        for (Fry f : fry) {
            centreX[f.school] += f.x;
            centreY[f.school] += f.y;
            meanVx[f.school] += f.vx;
            meanVy[f.school] += f.vy;
            schoolSize[f.school]++;
        }
        for (int s = 0; s < SCHOOLS; s++) {
            int n = Math.max(1, schoolSize[s]);
            centreX[s] /= n;
            centreY[s] /= n;
            meanVx[s] /= n;
            meanVy[s] /= n;
        }

        for (int i = 0; i < fry.length; i++) {
            Fry f = fry[i];
            int s = f.school;
            float ax = 0f, ay = 0f;

            float dx = schoolX[s] - f.x, dy = schoolY[s] - f.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 0.01f) {
                float w = Math.min(1f, d / 25f);
                ax += dx / d * w;
                ay += dy / d * w;
            }
            dx = centreX[s] - f.x;
            dy = centreY[s] - f.y;
            d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 0.01f) {
                float w = Math.min(1f, d / 15f) * 0.6f;
                ax += dx / d * w;
                ay += dy / d * w;
            }
            ax += meanVx[s] / FRY_SPEED * 0.6f;
            ay += meanVy[s] / FRY_SPEED * 0.6f;

            float sep = FRY_SEPARATION + 1f;
            for (int j = 0; j < fry.length; j++) {
                if (j == i) continue;
                dx = f.x - fry[j].x;
                dy = f.y - fry[j].y;
                float d2 = dx * dx + dy * dy;
                if (d2 >= sep * sep || d2 < 1e-6f) continue;
                d = (float) Math.sqrt(d2);
                float w = (1f - d / sep) * 2.5f;
                ax += dx / d * w;
                ay += dy / d * w;
            }

            for (Koi k : koi) {
                dx = f.x - k.x;
                dy = f.y - k.y;
                float d2 = dx * dx + dy * dy;
                if (d2 >= 22f * 22f || d2 < 1e-6f) continue;
                d = (float) Math.sqrt(d2);
                float w = (1f - d / 22f) * 1.5f;
                ax += dx / d * w;
                ay += dy / d * w;
            }

            if (gatherWeight > 0f) {
                dx = f.x - gatherX;
                dy = f.y - gatherY;
                d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 85f && d > 0.01f) {
                    float w = (1f - d / 85f) * 2f * gatherWeight;
                    ax += dx / d * w;
                    ay += dy / d * w;
                }
            }

            if (f.flee > 0f) {
                ax += f.fleeX * f.flee * 4f;
                ay += f.fleeY * f.flee * 4f;
            }

            if (f.x < 12f) ax += (12f - f.x) / 12f * 2f;
            if (f.x > W - 12f) ax -= (f.x - (W - 12f)) / 12f * 2f;
            if (f.y < 12f) ay += (12f - f.y) / 12f * 2f;
            if (f.y > H - 12f) ay -= (f.y - (H - 12f)) / 12f * 2f;

            ax += (rng.nextFloat() - 0.5f) * 0.4f;
            ay += (rng.nextFloat() - 0.5f) * 0.4f;

            float m = (float) Math.sqrt(ax * ax + ay * ay);
            float speed = f.pace * (FRY_SPEED + (FRY_FLEE_SPEED - FRY_SPEED) * f.flee) * clamp(0.55f + 0.45f * m, 0.55f, 1f);
            float wantVx = m > 1e-4f ? ax / m * speed : f.vx;
            float wantVy = m > 1e-4f ? ay / m * speed : f.vy;
            float ease = Math.min(1f, dt * (2.5f + 8f * f.flee));
            f.vx += (wantVx - f.vx) * ease;
            f.vy += (wantVy - f.vy) * ease;

            f.x = clamp(f.x + f.vx * dt, 2f, W - 2f);
            f.y = clamp(f.y + f.vy * dt, 2f, H - 2f);
            if (f.vx * f.vx + f.vy * f.vy > 0.25f) f.heading = (float) Math.atan2(f.vy, f.vx);
            f.flee = Math.max(0f, f.flee - dt / FLEE_SECONDS);
        }
    }

    // ---- pads, duckweed, fireflies -----------------------------------------------------------------------------

    private void driftPads() {
        for (Pad p : pads) {
            p.x = p.originX + (float) (Math.sin(time * 0.05 + p.phase) * 1.4 + Math.sin(time * 0.13 + p.phase * 1.7) * 0.6);
            p.y = p.originY + (float) (Math.cos(time * 0.04 + p.phase) * 1.4 + Math.sin(time * 0.11 + p.phase * 2.3) * 0.6);
        }
    }

    private void driftDuckweed(boolean force) {
        boolean changed = force;
        for (int c = 0; c < clusterPhase.length; c++) {
            int dx = (int) Math.round(Math.sin(time * 0.04 + clusterPhase[c]) * 1.4);
            int dy = (int) Math.round(Math.cos(time * 0.033 + clusterPhase[c] * 1.3) * 1.2);
            if (dx != clusterDx[c] || dy != clusterDy[c]) {
                clusterDx[c] = dx;
                clusterDy[c] = dy;
                changed = true;
            }
        }
        if (!changed) return;
        for (int i = 0; i < duckweed.length; i++) {
            int c = weedCluster[i];
            int x = Math.max(0, Math.min(W - 1, weedX[i] + clusterDx[c]));
            int y = Math.max(0, Math.min(H - 1, weedY[i] + clusterDy[c]));
            duckweed[i] = (x << 16) | y;
        }
    }

    private void stepFireflies(float dt) {
        for (Firefly f : fireflies) {
            if ((f.homeTimer -= dt) <= 0f) pickFireflyHome(f);
            f.angle += (rng.nextFloat() - 0.5f) * dt * 3f;
            float dx = f.homeX - f.x, dy = f.homeY - f.y;
            if (dx * dx + dy * dy > 20f * 20f) {
                float want = (float) Math.atan2(dy, dx);
                f.angle += clamp(wrap(want - f.angle), -0.8f * dt, 0.8f * dt);
            }
            f.angle = wrap(f.angle);
            f.x = clamp(f.x + (float) Math.cos(f.angle) * f.pace * dt, 3f, W - 3f);
            float bob = (float) Math.cos(time * 1.6 + f.phase) * 3f;
            f.y = clamp(f.y + ((float) Math.sin(f.angle) * f.pace + bob) * dt, 3f, H - 3f);
        }
    }

    private void pickFireflyHome(Firefly f) {
        float x = 0f, y = 0f;
        for (int t = 0; t < 8; t++) {
            x = 10f + rng.nextFloat() * (W - 20f);
            y = 10f + rng.nextFloat() * (H - 20f);
            if (outerness(x, y) > 0.45f || rng.nextFloat() < 0.15f) break;
        }
        f.homeX = x;
        f.homeY = y;
        f.homeTimer = 10f + 20f * rng.nextFloat();
    }

    /** 0 at the centre, 1 on the ellipse touching the pond's edges. */
    private static float outerness(float x, float y) {
        float dx = (x - W / 2f) / (W / 2f), dy = (y - H / 2f) / (H / 2f);
        return dx * dx + dy * dy;
    }

    /** Below 1 where a pad of radius r centred at (x, y) would reach into the central ellipse that pads keep out of. */
    private static float centralEllipse(float x, float y, float r) {
        float dx = (x - W / 2f) / (W * 0.32f + r), dy = (y - H / 2f) / (H * 0.30f + r);
        return dx * dx + dy * dy;
    }

    // ---- construction ------------------------------------------------------------------------------------------

    private Koi[] makeKoi() {
        int[] varieties = {KOHAKU, SHOWA, OGON, ASAGI, CHAGOI, SANKE, rng.nextInt(6), rng.nextInt(6)};
        for (int i = varieties.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = varieties[i];
            varieties[i] = varieties[j];
            varieties[j] = t;
        }
        Koi[] out = new Koi[KOI_COUNT];
        for (int i = 0; i < KOI_COUNT; i++) {
            float length = 26f + 20f * rng.nextFloat();
            Koi k = new Koi(varieties[i], length, makePattern(varieties[i]));
            for (int t = 0; t < 60; t++) {
                k.x = 60f + rng.nextFloat() * (W - 120f);
                k.y = 55f + rng.nextFloat() * (H - 110f);
                boolean clear = true;
                for (int j = 0; j < i && clear; j++) {
                    float dx = out[j].x - k.x, dy = out[j].y - k.y;
                    clear = dx * dx + dy * dy > 45f * 45f;
                }
                if (clear) break;
            }
            k.heading = rng.nextFloat() * TWO_PI - PI;
            k.wanderAngle = k.heading;
            k.cruise = 8f + 8f * rng.nextFloat();
            k.speed = k.cruise;
            k.cruiseTimer = 4f + 8f * rng.nextFloat();
            k.wanderTimer = rng.nextFloat() * 4f;
            k.buddyTimer = 3f + 10f * rng.nextFloat();
            k.depthTarget = 0.1f + 0.7f * rng.nextFloat();
            k.depth = k.depthTarget;
            k.depthTimer = 8f + 14f * rng.nextFloat();
            k.surfaceTimer = 5f + 55f * rng.nextFloat();
            k.wiggle = rng.nextFloat() * TWO_PI;
            float spacing = length / (SPINE - 1);
            float bx = (float) Math.cos(k.heading), by = (float) Math.sin(k.heading);
            for (int s = 0; s < SPINE; s++) {
                k.spineX[s] = k.x - bx * spacing * s;
                k.spineY[s] = k.y - by * spacing * s;
            }
            out[i] = k;
        }
        return out;
    }

    /**
     * Pattern bits per segment (0 = head .. 9 = tail): 0-9 centre band, 10-19 left band, 20-29 right band, 30-39 black.
     * Patches are runs of neighbouring segments across the bands, so they read as markings rather than noise.
     */
    private long makePattern(int variety) {
        long p = 0L;
        switch (variety) {
            case KOHAKU:
                if (rng.nextFloat() < 0.25f) {
                    p = bit(0) | bit(1); // tancho: one red spot on the head and nothing else
                } else {
                    p = redRuns(2 + rng.nextInt(2), 0.6f);
                }
                break;
            case SANKE:
                p = redRuns(2 + rng.nextInt(2), 0.5f) | blackSpots(1 + rng.nextInt(3));
                break;
            case SHOWA:
                p = redRuns(2 + rng.nextInt(2), 0.5f) | blackRuns(2 + rng.nextInt(2), 0.7f);
                break;
            case OGON:
                if (rng.nextFloat() < 0.5f) p = redRuns(1, 0.3f);
                break;
            case ASAGI:
                // Asagi carry their colour low on the flanks, behind the head.
                for (int s = 2 + rng.nextInt(2), end = 6 + rng.nextInt(3); s <= end; s++) {
                    p |= bit(10 + s) | bit(20 + s);
                }
                if (rng.nextFloat() < 0.5f) p |= bit(0);
                break;
            case CHAGOI:
            default:
                p = redRuns(1 + rng.nextInt(2), 0.3f);
                break;
        }
        return p;
    }

    private long redRuns(int runs, float headChance) {
        long p = 0L;
        for (int r = 0; r < runs; r++) {
            int start = (r == 0 && rng.nextFloat() < headChance) ? 0 : rng.nextInt(8);
            int len = 2 + rng.nextInt(4);
            boolean left = rng.nextFloat() < 0.75f, right = rng.nextFloat() < 0.75f;
            for (int s = start; s < Math.min(9, start + len); s++) {
                p |= bit(s);
                boolean rim = s == start || s == start + len - 1;
                if (left && !(rim && rng.nextFloat() < 0.3f)) p |= bit(10 + s);
                if (right && !(rim && rng.nextFloat() < 0.3f)) p |= bit(20 + s);
            }
        }
        return p;
    }

    private long blackSpots(int spots) {
        long p = 0L;
        for (int i = 0; i < spots; i++) p |= bit(30 + 2 + rng.nextInt(7));
        return p;
    }

    private long blackRuns(int runs, float headChance) {
        long p = 0L;
        for (int r = 0; r < runs; r++) {
            int start = (r == 0 && rng.nextFloat() < headChance) ? 0 : 1 + rng.nextInt(7);
            int len = 1 + rng.nextInt(3);
            for (int s = start; s < Math.min(9, start + len); s++) p |= bit(30 + s);
        }
        return p;
    }

    private static long bit(int i) {
        return 1L << i;
    }

    private Fry[] makeFry() {
        Fry[] out = new Fry[SCHOOLS * PER_SCHOOL];
        for (int s = 0; s < SCHOOLS; s++) {
            schoolX[s] = 50f + rng.nextFloat() * (W - 100f);
            schoolY[s] = 40f + rng.nextFloat() * (H - 80f);
            schoolAngle[s] = rng.nextFloat() * TWO_PI - PI;
            schoolTimer[s] = rng.nextFloat() * 3f;
            float a = rng.nextFloat() * TWO_PI;
            for (int i = 0; i < PER_SCHOOL; i++) {
                int color;
                if (s == 0) color = rng.nextBoolean() ? 0xFFF2A03A : 0xFFF7D04A;
                else if (s == 1) color = 0xFF4AA8E8;
                else color = 0xFFE86A8A;
                Fry f = new Fry(color, s, 0.85f + 0.3f * rng.nextFloat());
                f.x = schoolX[s] + (rng.nextFloat() - 0.5f) * 20f;
                f.y = schoolY[s] + (rng.nextFloat() - 0.5f) * 20f;
                f.heading = a + (rng.nextFloat() - 0.5f) * 0.6f;
                f.vx = (float) Math.cos(f.heading) * 15f;
                f.vy = (float) Math.sin(f.heading) * 15f;
                out[s * PER_SCHOOL + i] = f;
            }
        }
        return out;
    }

    private Pad[] makePads() {
        int n = 9 + rng.nextInt(4);
        float[] px = new float[n], py = new float[n], pr = new float[n];
        int placed = 0;
        for (int tries = 0; placed < n && tries < 4000; tries++) {
            float r = tries > 2500 ? 14f : 14f + 16f * rng.nextFloat();
            float x, y;
            if (rng.nextFloat() < 0.3f) {
                int corner = rng.nextInt(4);
                float ix = r * (-0.4f + 2.6f * rng.nextFloat()), iy = r * (-0.4f + 2.6f * rng.nextFloat());
                x = (corner & 1) == 0 ? ix : W - ix;
                y = (corner & 2) == 0 ? iy : H - iy;
            } else {
                int edge = rng.nextInt(4);
                float inset = r * (-0.4f + 2.2f * rng.nextFloat());
                if (edge < 2) {
                    x = rng.nextFloat() * W;
                    y = edge == 0 ? inset : H - inset;
                } else {
                    y = rng.nextFloat() * H;
                    x = edge == 2 ? inset : W - inset;
                }
            }
            // The whole pad stays out, with a margin so the drift never carries its rim inside.
            if (centralEllipse(x, y, r) < 1.12f) continue;
            boolean ok = true;
            for (int j = 0; j < placed && ok; j++) {
                float dx = px[j] - x, dy = py[j] - y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                ok = d >= r + pr[j] - 0.3f * Math.min(r, pr[j]);
            }
            if (!ok) continue;
            px[placed] = x;
            py[placed] = y;
            pr[placed] = r;
            placed++;
        }

        boolean[] lotus = new boolean[placed];
        int lotuses = Math.round(placed * 0.4f);
        for (int l = 0; l < lotuses; l++) {
            int j = rng.nextInt(placed);
            while (lotus[j]) j = (j + 1) % placed;
            lotus[j] = true;
        }
        Pad[] out = new Pad[placed];
        for (int i = 0; i < placed; i++) {
            out[i] = new Pad(px[i], py[i], pr[i], rng.nextFloat() * TWO_PI - PI, lotus[i], rng.nextFloat() * TWO_PI);
        }
        return out;
    }

    private int makeDuckweed(int clusters, int[] tx, int[] ty, int[] tc) {
        int n = 0;
        float[] subX = new float[3], subY = new float[3];
        for (int c = 0; c < clusters; c++) {
            clusterPhase[c] = rng.nextFloat() * TWO_PI;
            float cx, cy, sx, sy;
            if (pads.length > 0 && rng.nextFloat() < 0.45f) {
                // Round a pad's rim.
                Pad p = pads[rng.nextInt(pads.length)];
                float a = rng.nextFloat() * TWO_PI;
                float d = p.r + 3f + 6f * rng.nextFloat();
                cx = p.originX + (float) Math.cos(a) * d;
                cy = p.originY + (float) Math.sin(a) * d;
                sx = sy = 6f;
            } else {
                // Along an edge, stretched along it.
                int edge = rng.nextInt(4);
                float inset = 3f + 22f * rng.nextFloat();
                if (edge < 2) {
                    cx = 20f + rng.nextFloat() * (W - 40f);
                    cy = edge == 0 ? inset : H - 1 - inset;
                    sx = 14f;
                    sy = 5f;
                } else {
                    cy = 15f + rng.nextFloat() * (H - 30f);
                    cx = edge == 2 ? inset : W - 1 - inset;
                    sx = 5f;
                    sy = 14f;
                }
            }
            cx = clamp(cx, 2f, W - 3f);
            cy = clamp(cy, 2f, H - 3f);
            for (int i = 0; i < 3; i++) {
                subX[i] = cx + (float) rng.nextGaussian() * sx * 0.6f;
                subY[i] = cy + (float) rng.nextGaussian() * sy * 0.6f;
            }
            int count = 45 + rng.nextInt(45);
            for (int i = 0; i < count && n < tx.length; i++) {
                for (int t = 0; t < 6; t++) {
                    int sub = rng.nextInt(3);
                    float spread = 0.35f + 0.65f * rng.nextFloat();
                    int x = Math.round(subX[sub] + (float) rng.nextGaussian() * sx * spread);
                    int y = Math.round(subY[sub] + (float) rng.nextGaussian() * sy * spread);
                    if (x < 2 || x > W - 3 || y < 2 || y > H - 3 || onPad(x, y)) continue;
                    tx[n] = x;
                    ty[n] = y;
                    tc[n] = c;
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    private boolean onPad(float x, float y) {
        for (Pad p : pads) {
            float dx = x - p.originX, dy = y - p.originY, r = p.r - 1.5f;
            if (dx * dx + dy * dy < r * r) return true;
        }
        return false;
    }

    private Firefly[] makeFireflies() {
        Firefly[] out = new Firefly[FIREFLY_COUNT];
        for (int i = 0; i < FIREFLY_COUNT; i++) {
            Firefly f = new Firefly(rng.nextFloat() * TWO_PI);
            pickFireflyHome(f);
            f.x = f.homeX;
            f.y = f.homeY;
            pickFireflyHome(f);
            f.angle = rng.nextFloat() * TWO_PI - PI;
            f.pace = 4f + 4f * rng.nextFloat();
            out[i] = f;
        }
        return out;
    }

    // ---- maths -------------------------------------------------------------------------------------------------

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float wrap(float a) {
        while (a > PI) a -= TWO_PI;
        while (a < -PI) a += TWO_PI;
        return a;
    }
}
