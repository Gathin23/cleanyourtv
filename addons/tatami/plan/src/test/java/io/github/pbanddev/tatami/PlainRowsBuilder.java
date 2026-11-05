package io.github.pbanddev.tatami;

import java.util.Random;

/**
 * A stand-in {@link TileBuilder}: rows of rooms with joints at random, where some rooms reach an arm down into the
 * row below. A corner arm fills the overlap of a room and the room below it a little way down, so both become an L
 * (2 cells each); a middle arm, inside both rooms, makes a T above and a U below (3 cells each). A joint that lands
 * closer than a step to a joint of the next row snaps into line with it, and an arm is kept only where it breaks no
 * rule of {@link TileLayout}. Each room takes part in one arm at most.
 */
final class PlainRowsBuilder implements TileBuilder {
    private static final int ROW_MIN = 16, ROW_MAX = 24, ROOM_MIN = 24, ROOM_MAX = 48;
    /** The share of rooms that try an arm into the row below. */
    private static final float ARM_SHARE = 0.5f;
    /** The deepest arm, and the narrowest and widest middle arm, in steps. */
    private static final int ARM_DEPTH = 10, MIDDLE_MIN = 6, MIDDLE_MAX = 14;

    @Override
    public TileLayout build(Look.Plan plan) {
        int w = plan.tileWUnits, h = plan.tileHUnits, s = w / 2;
        if (w < 2 * ROOM_MAX || h < 2 * ROW_MAX) throw new IllegalArgumentException("tile " + w + " x " + h);
        int step = plan.minStepUnits, cell = plan.minCellUnits;
        int cross = (int) Math.ceil((double) plan.minCrossH / plan.unitH - 1e-3);
        Random random = new Random(plan.seed);

        IntList tops = new IntList();
        for (int top = 0; top < h; ) {
            tops.add(top);
            top = h - top < ROW_MAX + ROW_MIN ? h : top + ROW_MIN + random.nextInt(ROW_MAX - ROW_MIN + 1);
        }
        int n = tops.size;
        int[] top = new int[n + 1];
        for (int r = 0; r < n; r++) top[r] = tops.get(r);
        top[n] = h;

        // joints, as places in [0, w) on the row's own lines; row n - 1's bottom line is row 0's top, half a tile on
        int[][] joints = new int[n][];
        for (int r = 0; r < n; r++) {
            IntList row = new IntList();
            int start = random.nextInt(w);
            for (int x = start; x < start + w; ) {
                row.add(Math.floorMod(x, w));
                int next = x + ROOM_MIN + random.nextInt(ROOM_MAX - ROOM_MIN + 1);
                x = start + w - next < ROOM_MIN ? start + w : next;
            }
            int[] p = row.array();
            for (int k = 0; k < p.length; k++) {
                p[k] = settle(p[k], w, step, r > 0 ? joints[r - 1] : null, 0, r == n - 1 ? joints[0] : null, s);
            }
            java.util.Arrays.sort(p);
            joints[r] = p;
        }

        // rooms: each a list of columns, x0 x1 top bottom
        IntList roomRow = new IntList();
        int[][][] cols = new int[n * (w / ROOM_MIN + 2)][][];
        int[] first = new int[n + 1];
        int rooms = 0;
        for (int r = 0; r < n; r++) {
            first[r] = rooms;
            int[] p = joints[r];
            for (int k = 0; k < p.length; k++) {
                int x1 = k + 1 < p.length ? p[k + 1] : p[0] + w;
                cols[rooms++] = new int[][] {{p[k], x1, top[r], top[r + 1]}};
                roomRow.add(r);
            }
        }
        first[n] = rooms;

        // the places where lines end on each row line from above and from below, as places on that line
        IntList[] above = new IntList[n], below = new IntList[n];
        for (int r = 0; r < n; r++) {
            above[r] = new IntList();
            below[r] = new IntList();
            for (int x : joints[r]) below[r].add(x);
            int[] up = r > 0 ? joints[r - 1] : joints[n - 1];
            for (int x : up) above[r].add(r > 0 ? x : Math.floorMod(x - s, w));
        }

        boolean[] used = new boolean[rooms];
        for (int r = 0; r + 1 < n; r++) {
            int depthMost = Math.min(ARM_DEPTH, top[r + 2] - top[r + 1] - cross);
            if (depthMost < step) continue;
            for (int u = first[r]; u < first[r + 1]; u++) {
                if (used[u] || random.nextFloat() >= ARM_SHARE) continue;
                int u0 = cols[u][0][0], u1 = cols[u][0][1];
                // every arm that fits: the room below, its shift by a tile, the kind, and the arm's ends
                IntList options = new IntList();
                for (int l = first[r + 1]; l < first[r + 2]; l++) {
                    if (used[l]) continue;
                    for (int shift = -w; shift <= w; shift += w) {
                        int l0 = cols[l][0][0] + shift, l1 = cols[l][0][1] + shift;
                        if (u0 + cell <= l0 && l0 + cell <= u1 && u1 + cell <= l1) addOption(options, l, shift, l0, u1);
                        if (l0 + cell <= u0 && u0 + cell <= l1 && l1 + cell <= u1) addOption(options, l, shift, u0, l1);
                        int lo = Math.max(u0, l0) + cell, hi = Math.min(u1, l1) - cell;
                        if (hi - lo >= MIDDLE_MIN) {
                            int width = MIDDLE_MIN + random.nextInt(Math.min(MIDDLE_MAX, hi - lo) - MIDDLE_MIN + 1);
                            int a = lo + random.nextInt(hi - lo - width + 1);
                            addOption(options, l, shift, a, a + width);
                        }
                    }
                }
                if (options.size == 0) continue;
                int o = 4 * random.nextInt(options.size / 4);
                int l = options.get(o), shift = options.get(o + 1), a = options.get(o + 2), b = options.get(o + 3);
                int depth = step + random.nextInt(depthMost - step + 1);
                int l0 = cols[l][0][0] + shift, l1 = cols[l][0][1] + shift;
                // cuts: the room above is cut at the arm's ends inside it, the room below likewise
                int[] cutsAbove = cuts(a, b, u0, u1), cutsBelow = cuts(a, b, l0, l1);
                IntList farBelow = r + 2 < n ? below[r + 2] : below[0];
                int farShift = r + 2 < n ? 0 : s;
                boolean fits = true;
                for (int x : cutsAbove) fits &= !clashes(x, above[r], 0, w, step);
                for (int x : cutsBelow) fits &= !clashes(x, farBelow, farShift, w, step);
                if (!fits) continue;
                for (int x : cutsAbove) below[r].add(Math.floorMod(x, w));
                IntList farAbove = r + 2 < n ? above[r + 2] : above[0];
                for (int x : cutsBelow) farAbove.add(Math.floorMod(x - farShift, w));
                cols[u] = split(u0, u1, a, b, top[r], top[r + 1], 0, depth);
                cols[l] = split(l0, l1, a, b, top[r + 1], top[r + 2], depth, 0);
                used[u] = true;
                used[l] = true;
            }
        }

        IntList x0 = new IntList(), y0 = new IntList(), x1 = new IntList(), y1 = new IntList(), room = new IntList();
        for (int k = 0; k < rooms; k++) {
            for (int[] c : cols[k]) {
                x0.add(c[0]);
                x1.add(c[1]);
                y0.add(c[2]);
                y1.add(c[3]);
                room.add(k);
            }
        }
        return new TileLayout(w, h, rooms, x0.array(), y0.array(), x1.array(), y1.array(), room.array());
    }

    private static void addOption(IntList options, int room, int shift, int a, int b) {
        options.add(room);
        options.add(shift);
        options.add(a);
        options.add(b);
    }

    /** The arm's ends that fall strictly inside [x0, x1]: where that room gets cut. */
    private static int[] cuts(int a, int b, int x0, int x1) {
        IntList out = new IntList();
        if (a > x0 && a < x1) out.add(a);
        if (b > x0 && b < x1) out.add(b);
        return out.array();
    }

    /**
     * A room [x0, x1] x [top, bottom] cut at the arm [a, b]: the arm's part has its top lowered by {@code lower}
     * and its bottom dropped by {@code drop}.
     */
    private static int[][] split(int x0, int x1, int a, int b, int top, int bottom, int lower, int drop) {
        int from = Math.max(x0, a), to = Math.min(x1, b);
        IntList out = new IntList();
        if (from > x0) addColumn(out, x0, from, top, bottom);
        addColumn(out, from, to, top + lower, bottom + drop);
        if (to < x1) addColumn(out, to, x1, top, bottom);
        int[] flat = out.array();
        int[][] columns = new int[flat.length / 4][];
        for (int c = 0; c < columns.length; c++) columns[c] = java.util.Arrays.copyOfRange(flat, 4 * c, 4 * c + 4);
        return columns;
    }

    private static void addColumn(IntList out, int x0, int x1, int top, int bottom) {
        out.add(x0);
        out.add(x1);
        out.add(top);
        out.add(bottom);
    }

    /** True when x lies closer than a step to a place in {@code line} (each moved by {@code shift}), but not on it. */
    private static boolean clashes(int x, IntList line, int shift, int w, int step) {
        for (int i = 0; i < line.size; i++) {
            int d = Math.floorMod(x - line.get(i) - shift, w);
            d = Math.min(d, w - d);
            if (d > 0 && d < step) return true;
        }
        return false;
    }

    /**
     * A joint at x, moved as little as it takes so it is in line with, or a step clear of, every joint of the row
     * above (moved by shiftAbove) and the row below (moved by shiftBelow). Either may be null.
     */
    private static int settle(int x, int w, int step, int[] above, int shiftAbove, int[] below, int shiftBelow) {
        for (int d = 0; d <= 2 * step + 2; d++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int c = Math.floorMod(x + sign * d, w);
                if (clear(c, w, step, above, shiftAbove) && clear(c, w, step, below, shiftBelow)) return c;
                if (d == 0) break;
            }
        }
        throw new IllegalStateException("no place for a joint near " + x);
    }

    private static boolean clear(int x, int w, int step, int[] row, int shift) {
        if (row == null) return true;
        for (int q : row) {
            int d = Math.floorMod(x - q - shift, w);
            d = Math.min(d, w - d);
            if (d > 0 && d < step) return false;
        }
        return true;
    }
}
