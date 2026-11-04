package io.github.pbanddev.tatami;

import java.util.Random;

/**
 * Picks the paint of every room once, for the life of the plan. A seeded deal from the look's deck that keeps
 * each paint's share of the cards. Rooms that touch at any time never share a paint (hard rule) and try not to
 * get two paints that look alike (soft rule). When the cards and the hard rule clash, the hard rule wins.
 */
public final class RoomColours {
    /** The screen the plan is laid out for to find which rooms touch. Every 16:9 screen gives the same answer. */
    private static final float SCREEN_W = 1920, SCREEN_H = 1080;
    /** How many times the plan is laid out at, drawn at random over {@link #SPAN} seconds. */
    private static final int SAMPLES = 64;
    private static final double SPAN = 100_000;
    /**
     * Rooms whose facing sides lie on one wall line count as touching when they come within this share of the
     * screen height of each other along it. The walls keep sliding, so rooms that only nearly touch at every
     * sample time can touch in between; sampling alone misses some of those.
     */
    private static final float NEAR_H = 0.25f;
    /** Sides closer than this, in plan units, lie on one wall line. */
    private static final float EPS = 0.05f;
    /** Paints closer than this (CIEDE2000) look alike side by side. */
    private static final double ALIKE = 8;
    /** What two touching rooms with one paint cost. Two touching rooms whose paints look alike cost 1. */
    private static final int SAME = 1000;
    private static final int STEPS_PER_ROOM = 50;
    /** Keeps the deal's random numbers apart from the layout's, which start from the same seed. */
    private static final long STREAM = 0x7A7A3141L;

    private RoomColours() {}

    /** One index into {@link Look.Palette#deck} per room. A room keeps its index in every frame. */
    public static int[] deal(FloorPlan plan, Look look) {
        Random random = new Random(look.plan.seed ^ STREAM);
        boolean[][] touching = touching(plan, look.camera.angleDeg, random);
        int[] deck = look.palette.deck;
        double[][] lab = new double[deck.length][];
        for (int c = 0; c < deck.length; c++) lab[c] = lab(deck[c]);
        boolean[][] alike = new boolean[deck.length][deck.length];
        for (int c = 0; c < deck.length; c++) {
            for (int d = 0; d < deck.length; d++) alike[c][d] = c != d && deltaE2000(lab[c], lab[d]) < ALIKE;
        }
        return assign(quotas(look.palette.cards, plan.rooms), touching, alike, random);
    }

    /**
     * One index into {@link Look.Palette#deck} per room id, the quotas in a random order fixed by the seed: no rule
     * about neighbours, so equal paints may touch.
     */
    public static int[] shuffle(int rooms, Look look) {
        Random random = new Random(look.plan.seed ^ STREAM);
        int[] quotas = quotas(look.palette.cards, rooms);
        int[] paint = new int[rooms];
        int k = 0;
        for (int c = 0; c < quotas.length; c++) {
            for (int n = 0; n < quotas[c]; n++) paint[k++] = c;
        }
        for (int i = rooms - 1; i > 0; i--) swap(paint, i, random.nextInt(i + 1));
        return paint;
    }

    /**
     * How many rooms get each paint: {@code cards} scaled to {@code rooms} by largest remainder, so each count
     * is the exact share rounded down or up and the counts add up to {@code rooms}. Ties go to the paint with
     * more cards, then to the earlier paint.
     */
    static int[] quotas(int[] cards, int rooms) {
        long total = 0;
        for (int c : cards) total += c;
        int[] out = new int[cards.length];
        if (total <= 0) return out;
        long[] remainder = new long[cards.length];
        int left = rooms;
        for (int c = 0; c < cards.length; c++) {
            long share = (long) rooms * cards[c];
            out[c] = (int) (share / total);
            remainder[c] = share % total;
            left -= out[c];
        }
        for (; left > 0; left--) {
            int pick = 0;
            for (int c = 1; c < cards.length; c++) {
                if (remainder[c] > remainder[pick] || (remainder[c] == remainder[pick] && cards[c] > cards[pick])) {
                    pick = c;
                }
            }
            out[pick]++;
            remainder[pick] = -1;
        }
        return out;
    }

    /** Which rooms touch, or nearly touch, at any of the sample times. */
    static boolean[][] touching(FloorPlan plan, float angleDeg, Random random) {
        int n = plan.rooms;
        Frame f = new Frame(n);
        float w = Camera.planWidth(SCREEN_W, SCREEN_H, angleDeg), h = Camera.planHeight(SCREEN_W, SCREEN_H, angleDeg);
        float near = NEAR_H * SCREEN_H;
        boolean[][] out = new boolean[n][n];
        for (int s = 0; s < SAMPLES; s++) {
            plan.layout(random.nextDouble() * SPAN, -w / 2, -h / 2, w, h, f);
            for (int i = 0; i < f.roomCount; i++) {
                for (int j = i + 1; j < f.roomCount; j++) {
                    if (gap(f, i, j) < near) {
                        out[i][j] = true;
                        out[j][i] = true;
                    }
                }
            }
        }
        return out;
    }

    /**
     * How far apart rooms i and j are along the wall line their facing sides lie on: below 0 when they share
     * a wall that long, infinite when no sides of theirs face each other on one line.
     */
    private static float gap(Frame f, int i, int j) {
        float ax0 = f.roomX[i], ay0 = f.roomY[i], ax1 = ax0 + f.roomW[i], ay1 = ay0 + f.roomH[i];
        float bx0 = f.roomX[j], by0 = f.roomY[j], bx1 = bx0 + f.roomW[j], by1 = by0 + f.roomH[j];
        float gap = Float.POSITIVE_INFINITY;
        if (Math.abs(ax1 - bx0) <= EPS || Math.abs(bx1 - ax0) <= EPS) {
            gap = Math.max(ay0, by0) - Math.min(ay1, by1);
        }
        if (Math.abs(ay1 - by0) <= EPS || Math.abs(by1 - ay0) <= EPS) {
            gap = Math.min(gap, Math.max(ax0, bx0) - Math.min(ax1, bx1));
        }
        return gap;
    }

    /**
     * Shuffles the cards onto the rooms, then swaps rooms' cards until no touching rooms share a paint or look
     * alike, or the steps run out (swaps keep every paint's count). Any room still sharing a paint with a
     * neighbour then gets a paint none of its neighbours has, whatever its count.
     */
    static int[] assign(int[] quotas, boolean[][] touching, boolean[][] alike, Random random) {
        int n = touching.length;
        int[][] neighbours = neighbours(touching);
        int[] paint = new int[n];
        for (int c = 0, k = 0; c < quotas.length; c++) {
            for (int i = 0; i < quotas[c] && k < n; i++) paint[k++] = c;
        }
        for (int k = n - 1; k > 0; k--) swap(paint, k, random.nextInt(k + 1));
        int cost = 0;
        for (int k = 0; k < n; k++) cost += cost(k, paint[k], paint, neighbours, alike);
        cost /= 2;
        int[] best = paint.clone();
        int bestCost = cost;
        int[] conflicted = new int[n];
        for (int step = 0; step < STEPS_PER_ROOM * n && bestCost > 0; step++) {
            int count = 0;
            for (int k = 0; k < n; k++) {
                if (cost(k, paint[k], paint, neighbours, alike) > 0) conflicted[count++] = k;
            }
            if (count == 0) break;
            int a = conflicted[random.nextInt(count)];
            // the swap that helps most; among equals, one at random
            int pick = -1, pickDelta = Integer.MAX_VALUE, ties = 0;
            for (int b = 0; b < n; b++) {
                if (paint[b] == paint[a]) continue;
                int delta = swapDelta(a, b, paint, neighbours, alike);
                if (delta < pickDelta) {
                    pick = b;
                    pickDelta = delta;
                    ties = 1;
                } else if (delta == pickDelta && random.nextInt(++ties) == 0) {
                    pick = b;
                }
            }
            if (pick < 0) break;
            swap(paint, a, pick);
            cost += pickDelta;
            if (cost < bestCost) {
                bestCost = cost;
                System.arraycopy(paint, 0, best, 0, n);
            }
        }
        keepApart(best, neighbours, alike);
        return best;
    }

    private static int[][] neighbours(boolean[][] touching) {
        int n = touching.length;
        int[][] out = new int[n][];
        for (int i = 0; i < n; i++) {
            int count = 0;
            for (int j = 0; j < n; j++) if (touching[i][j] && i != j) count++;
            out[i] = new int[count];
            count = 0;
            for (int j = 0; j < n; j++) if (touching[i][j] && i != j) out[i][count++] = j;
        }
        return out;
    }

    /** What room k costs with paint p, against its neighbours as painted now. */
    private static int cost(int k, int p, int[] paint, int[][] neighbours, boolean[][] alike) {
        int cost = 0;
        for (int m : neighbours[k]) {
            int q = paint[m];
            if (q == p) cost += SAME;
            else if (alike[p][q]) cost++;
        }
        return cost;
    }

    /** How the total cost changes when rooms a and b swap paints. */
    private static int swapDelta(int a, int b, int[] paint, int[][] neighbours, boolean[][] alike) {
        int pa = paint[a], pb = paint[b];
        int before = cost(a, pa, paint, neighbours, alike) + cost(b, pb, paint, neighbours, alike);
        swap(paint, a, b);
        int after = cost(a, pb, paint, neighbours, alike) + cost(b, pa, paint, neighbours, alike);
        swap(paint, a, b);
        return after - before;
    }

    /** The hard rule wins: a room that shares a paint with a neighbour takes the best paint no neighbour has. */
    private static void keepApart(int[] paint, int[][] neighbours, boolean[][] alike) {
        for (int k = 0; k < paint.length; k++) {
            if (cost(k, paint[k], paint, neighbours, alike) < SAME) continue;
            int pick = -1, pickCost = SAME;
            for (int c = 0; c < alike.length; c++) {
                int cost = cost(k, c, paint, neighbours, alike);
                if (cost < pickCost) {
                    pick = c;
                    pickCost = cost;
                }
            }
            if (pick >= 0) paint[k] = pick;
        }
    }

    private static void swap(int[] values, int i, int j) {
        int v = values[i];
        values[i] = values[j];
        values[j] = v;
    }

    /** CIELAB (D65) of an sRGB colour: L*, a*, b*. */
    static double[] lab(int argb) {
        double r = linear((argb >> 16) & 0xFF), g = linear((argb >> 8) & 0xFF), b = linear(argb & 0xFF);
        double fx = labF((0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047);
        double fy = labF(0.2126729 * r + 0.7151522 * g + 0.0721750 * b);
        double fz = labF((0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double labF(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    /** The CIEDE2000 colour difference of two CIELAB colours (Sharma, Wu and Dalal 2005). */
    static double deltaE2000(double[] lab1, double[] lab2) {
        double l1 = lab1[0], a1 = lab1[1], b1 = lab1[2];
        double l2 = lab2[0], a2 = lab2[1], b2 = lab2[2];
        double cMean7 = Math.pow((Math.hypot(a1, b1) + Math.hypot(a2, b2)) / 2, 7);
        double g = 0.5 * (1 - Math.sqrt(cMean7 / (cMean7 + 6103515625.0)));   // 25^7
        double a1p = (1 + g) * a1, a2p = (1 + g) * a2;
        double c1p = Math.hypot(a1p, b1), c2p = Math.hypot(a2p, b2);
        double h1p = hueDeg(a1p, b1), h2p = hueDeg(a2p, b2);
        boolean grey = c1p * c2p == 0;

        double dL = l2 - l1, dC = c2p - c1p;
        double dh = grey ? 0 : h2p - h1p;
        if (dh > 180) dh -= 360;
        else if (dh < -180) dh += 360;
        double dH = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dh / 2));

        double lMean = (l1 + l2) / 2, cMeanP = (c1p + c2p) / 2;
        double hMean;
        if (grey) hMean = h1p + h2p;
        else if (Math.abs(h1p - h2p) <= 180) hMean = (h1p + h2p) / 2;
        else if (h1p + h2p < 360) hMean = (h1p + h2p + 360) / 2;
        else hMean = (h1p + h2p - 360) / 2;

        double t = 1 - 0.17 * cosDeg(hMean - 30) + 0.24 * cosDeg(2 * hMean) + 0.32 * cosDeg(3 * hMean + 6)
                - 0.20 * cosDeg(4 * hMean - 63);
        double dTheta = 30 * Math.exp(-Math.pow((hMean - 275) / 25, 2));
        double cMeanP7 = Math.pow(cMeanP, 7);
        double rc = 2 * Math.sqrt(cMeanP7 / (cMeanP7 + 6103515625.0));
        double l50 = (lMean - 50) * (lMean - 50);
        double sl = 1 + 0.015 * l50 / Math.sqrt(20 + l50);
        double sc = 1 + 0.045 * cMeanP;
        double sh = 1 + 0.015 * cMeanP * t;
        double rt = -Math.sin(Math.toRadians(2 * dTheta)) * rc;
        double kl = dL / sl, kc = dC / sc, kh = dH / sh;
        return Math.sqrt(kl * kl + kc * kc + kh * kh + rt * kc * kh);
    }

    private static double hueDeg(double a, double b) {
        if (a == 0 && b == 0) return 0;
        double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0 ? h + 360 : h;
    }

    private static double cosDeg(double deg) {
        return Math.cos(Math.toRadians(deg));
    }
}
