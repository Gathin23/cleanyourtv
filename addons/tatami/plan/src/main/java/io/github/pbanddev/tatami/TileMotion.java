package io.github.pbanddev.tatami;

import java.util.Arrays;

/**
 * Nagaya's walls in motion: how far every group of lines of a {@link TileGraph} is from home at any time, in steps.
 *
 * <p>Between glides the plan is at a rest state: one whole-step offset per group that keeps every rule of the graph.
 * Each slot of the {@link MoveClock} makes the next rest state from the last. With {@link Look.Motion#quietShare} a
 * slot moves nothing. Otherwise groups are picked in a shuffled order fixed by the seed and the slot until the slot's
 * share of the wall length moves ({@link Look.Motion#moveShare}, spread per slot by {@link Look.Motion#shareSpread}).
 * A picked group aims at home plus 1 to {@link Look.Motion#maxUnits} steps to one side (it turns to the other side of
 * home with {@link Look.Motion#reverseShare}, else a coin picks), or with {@link Look.Motion#alignShare} at the place
 * that lines it up with a nearby parallel line.
 *
 * <p>In the glide each moving group starts at its own {@link MoveClock#delay} and goes from its old offset to its new
 * one along its own {@link Ease#glide} of {@link MoveClock#glide} seconds. Each moves one way only, so a rule on 2
 * groups is at its least when each of them is at one end of its glide: both at the start, both at the end, or one
 * at each. A pick goes as far toward its aim as every rule lets it at those ends, given the picks before it; one that
 * aims home or into line goes all the way or stays put. So every moment of every glide keeps every rule, whatever
 * the timing.
 *
 * <p>Every checkpoint ({@link MoveClock#slotsPerCheckpoint} slots) all groups are home. In the last
 * {@link Look.Rhythm#homeSlots} slots before it, groups aim home, and the last slot takes every group home together
 * along one shared glide, which is always valid. The state at any slot is simulated from its checkpoint with integers
 * and hashes in a fixed order, so every view and every start agree. Going forward costs one step per slot, a jump at
 * most one checkpoint's worth; nothing is allocated.
 */
final class TileMotion {
    private static final long STREAM = 0x2545F4914F6CDD1DL;
    private static final int ORDER = 1, PICK = 2, ALIGN = 3, PARTNER = 4, TURN = 5, SIDE = 6, AMP = 7, QUIET = 8, SHARE = 9;

    final MoveClock clock;
    private final TileGraph g;
    private final long seed;
    private final double reverse, align, quiet, spread;
    private final int maxUnits, homeSlots;
    /** The wall length a busy slot moves on average: the share of all of it, made up for the quiet slots. */
    private final double wanted;
    private final int[] order;
    /** The rest state before glide {@link #slot}, and after it. */
    private int[] from, to;
    /** When each moving group's glide in slot {@link #slot} starts, after the slot does, and how long it lasts. */
    private final double[] delay, span;
    private long slot;
    private boolean ready, aligning;
    /** True when {@link #delay} and {@link #span} are worked out for slot {@link #slot}. */
    private boolean timed;
    /** The range {@link #room} found. */
    private int low, high;
    /**
     * What the last slot's step did: picks that would move, picks turned down by a rule, picks a rule cut short,
     * aligns that went through.
     */
    int tried, rejected, shortened, aligned;

    TileMotion(Look look, TileGraph graph) {
        g = graph;
        clock = new MoveClock(look.rhythm, look.plan.seed);
        seed = look.plan.seed ^ STREAM;
        Look.Motion motion = look.motion;
        reverse = motion.reverseShare;
        align = motion.alignShare;
        quiet = motion.quietShare;
        spread = motion.shareSpread;
        maxUnits = motion.maxUnits;
        homeSlots = look.rhythm.homeSlots;
        if (homeSlots < 1 || homeSlots >= clock.slotsPerCheckpoint) throw new IllegalArgumentException("home slots " + homeSlots);
        if (!(quiet >= 0 && quiet < 1 && spread >= 0 && spread <= 1)) {
            throw new IllegalArgumentException("quiet " + quiet + ", spread " + spread);
        }
        long total = 0;
        for (int length : g.groupLength) total += length;
        wanted = motion.moveShare / (1 - quiet) * total;
        order = new int[g.groups];
        from = new int[g.groups];
        to = new int[g.groups];
        delay = new double[g.groups];
        span = new double[g.groups];
    }

    /** Every group's offset from home at time t, in steps: whole between glides, eased within them. */
    void offsets(double t, float[] out) {
        long k = clock.slot(t);
        seek(k);
        if (!timed) time(k);
        double since = t - clock.start(k);
        int n = g.groups;
        for (int i = 0; i < n; i++) {
            int a = from[i], b = to[i];
            out[i] = a == b ? b : (float) (a + (b - a) * Ease.glide((since - delay[i]) / span[i]));
        }
    }

    /** The rest states before and after slot k's glide. */
    void glide(long k, int[] before, int[] after) {
        seek(k);
        System.arraycopy(from, 0, before, 0, g.groups);
        System.arraycopy(to, 0, after, 0, g.groups);
    }

    /** Each moving group's timing in slot k, which {@link #from} and {@link #to} are at. */
    private void time(long k) {
        for (int i = 0; i < g.groups; i++) {
            if (from[i] == to[i]) continue;
            delay[i] = clock.delay(k, i);
            span[i] = clock.glide(k, i);
        }
        timed = true;
    }

    /** Brings {@link #from} and {@link #to} to slot k: one step from the slot before, else from its checkpoint. */
    private void seek(long k) {
        if (ready && k == slot) return;
        timed = false;
        long checkpoint = clock.checkpoint(k);
        if (!ready || k < slot || checkpoint > slot) {
            slot = checkpoint;
            Arrays.fill(from, 0);
            next(slot);
            ready = true;
        }
        while (slot < k) {
            int[] swap = from;
            from = to;
            to = swap;
            slot++;
            next(slot);
        }
    }

    /** The rest state after slot k's glide, into {@link #to}, from the one before it in {@link #from}. */
    private void next(long k) {
        int n = g.groups;
        System.arraycopy(from, 0, to, 0, n);
        tried = 0;
        rejected = 0;
        shortened = 0;
        aligned = 0;
        if (clock.homeTogether(k)) {
            Arrays.fill(to, 0);
            return;
        }
        long local = k - clock.checkpoint(k);
        // in the slots before the last, an even share of the groups still away go home each time
        double homeOdds = local >= clock.slotsPerCheckpoint - homeSlots ? 1.0 / (clock.slotsPerCheckpoint - local) : 0;
        long want = 0;
        if (homeOdds == 0) {
            if (MoveClock.unit(MoveClock.hash(seed, k, 0, QUIET)) < quiet) return;
            want = Math.round(wanted * (1 + spread * (2 * MoveClock.unit(MoveClock.hash(seed, k, 0, SHARE)) - 1)));
        }
        for (int i = 0; i < n; i++) order[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = (int) (MoveClock.unit(MoveClock.hash(seed, k, -1 - i, ORDER)) * (i + 1));
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        long moving = 0;
        for (int i = 0; i < n && (homeOdds > 0 || moving < want); i++) {
            int gr = order[i];
            if (g.groupLength[gr] == 0) continue;
            int target;
            if (homeOdds > 0) {
                if (to[gr] == 0 || MoveClock.unit(MoveClock.hash(seed, k, gr, PICK)) >= homeOdds) continue;
                target = 0;
                aligning = false;
            } else {
                target = aim(k, gr);
            }
            if (target == to[gr]) continue;
            tried++;
            room(gr);
            if (target < low || target > high) {
                // a free aim goes as far toward its target as the rules let it; home or into line is all or nothing
                if (homeOdds > 0 || aligning) {
                    rejected++;
                    continue;
                }
                target = Math.max(low, Math.min(high, target));
                if (target == to[gr]) {
                    rejected++;
                    continue;
                }
                shortened++;
            }
            to[gr] = target;
            moving += g.groupLength[gr];
            if (aligning) aligned++;
        }
    }

    /** Where a picked group aims: in line with a partner, or home plus a few steps to one side. */
    private int aim(long k, int gr) {
        aligning = false;
        int a0 = g.alignStart[gr], a1 = g.alignStart[gr + 1];
        if (a1 > a0 && MoveClock.unit(MoveClock.hash(seed, k, gr, ALIGN)) < align) {
            int a = a0 + (int) (MoveClock.unit(MoveClock.hash(seed, k, gr, PARTNER)) * (a1 - a0));
            int target = g.alignDelta[a] + to[g.alignGroup[a]];
            if (Math.abs(target) <= maxUnits) {
                aligning = true;
                return target;
            }
        }
        int now = Integer.signum(to[gr]);
        int side = now != 0 && MoveClock.unit(MoveClock.hash(seed, k, gr, TURN)) < reverse ? -now
                : MoveClock.unit(MoveClock.hash(seed, k, gr, SIDE)) < 0.5 ? 1 : -1;
        return side * (1 + (int) (maxUnits * MoveClock.unit(MoveClock.hash(seed, k, gr, AMP))));
    }

    /**
     * The offsets group gr may glide to, into [{@link #low}, {@link #high}]: every rule on it holds with it at its end
     * and each other group at either end of its own glide as it stands. With gr at its start every rule holds already:
     * at the rest state before the slot, and against each group picked before it, whose own pick checked that. The
     * range always holds where gr is now.
     */
    private void room(int gr) {
        low = Integer.MIN_VALUE;
        high = Integer.MAX_VALUE;
        for (int i = g.ruleStart[gr]; i < g.ruleStart[gr + 1]; i++) {
            int r = g.ruleList[i], least = g.ruleMin[r] - g.ruleGap[r];
            // offset[hi] - offset[lo] >= least, with the other group wherever it is in its glide
            if (g.ruleHi[r] == gr) {
                int other = g.ruleLo[r];
                low = Math.max(low, least + Math.max(from[other], to[other]));
            } else {
                int other = g.ruleHi[r];
                high = Math.min(high, Math.min(from[other], to[other]) - least);
            }
        }
    }
}
