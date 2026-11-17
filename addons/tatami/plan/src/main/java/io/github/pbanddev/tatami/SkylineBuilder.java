package io.github.pbanddev.tatami;

import java.util.Arrays;
import java.util.Random;

/**
 * A {@link TileBuilder} that settles each room's shape class before its walls. Rooms are laid course by course onto
 * the row line the course above left behind (its skyline), and each room's number of steps (0, 1 or 2) is drawn
 * from its width, so the count is decided per room and never runs to 3.
 *
 * <p>A course is laid in one go: its joints are drawn from every joint set that fits the skyline, each set weighted
 * by how likely its rooms' widths are and how likely each room's count of skyline jogs is for its width. A joint
 * may sit exactly on a jog of the skyline (the step is then the room above's alone, and the joint runs on from it),
 * exactly on a joint of the course above (one joint through 2 courses), or clear of both. Then each room draws its
 * class, given its width and the jogs it already has, and puts the steps it still needs into its bottom row line as
 * jogs, some near its end as a deeper arm; some joints also put a jog under themselves, a step for the room below.
 * Last, the bottom line's levels are drawn round the tile so it closes, every jog is at least a step and every cell
 * keeps a sound height. Each draw of a class leans toward what the tile is short of for that width, and each course
 * is drawn a few times to keep the tile's room count on its aim.
 *
 * <p>About one row line in every 0.8 H runs straight all round, the first among them. The last course's bottom line
 * is the first line half a tile on, so that course, like any above a straight line, takes its steps from its
 * skyline alone, and the course above it leaves it more jogs.
 */
final class SkylineBuilder implements TileBuilder {
    private static final long SALT = 0x536B796C696E65L;
    private static final int ATTEMPTS = 64;
    /** Most draws of a course (4 times as many for the last); the first to meet the room aim, or else the nearest. */
    private static final int DRAWS = 16;

    /** Before the row lines jog, most courses are TALL and some THIN; their mean is {@link Look.Plan#courseUnits}. */
    private static final int TALL_MIN = 17, TALL_MAX = 22, THIN_MIN = 10, THIN_MAX = 13;
    private static final double THIN_SHARE = 0.2;
    /** Room widths in steps: a bell round the mean with this spread (a share of the mean), plus a flat tail below. */
    private static final double WIDTH_SPREAD = 0.22, WIDTH_TAIL = 0.25;
    private static final int WIDTH_MIN = 10, WIDTH_MAX = 72;
    /** How hard a room's class draw leans toward the classes its width has had too few of in the tile so far. */
    private static final double DEAL = 1.5;
    /**
     * The share of a room's steps it takes from its skyline (the rest go into its bottom line), and the chance that a
     * joint puts a jog under itself, a step for the room below. Over a course whose bottom line is straight, the
     * FEED values: that course can only take steps from its skyline, so the course above leaves it more.
     */
    private static final double TOP_SHARE = 0.5, JOINT_JOG = 0.4, FEED_SHARE = 0.3, FEED_JOINT_JOG = 0.55;
    /** The fewest jogs per H of a row line that is not straight. */
    private static final double LINE_JOGS = 1.6;
    /** Weights of a joint place on a jog of the skyline, on a jog under a joint above, and on a joint above. */
    private static final double ON_JOG = 1, ON_JOINT_JOG = 0.15, THROUGH = 5.5;
    /**
     * How far a row line's levels may sit from its base, and the spread they are drawn with; and the spread of a
     * course's height round its base height. In steps.
     */
    private static final int LEVEL_REACH = 10;
    private static final double LEVEL_SPREAD = 10, HEIGHT_SPREAD = 12;
    /** Relative weights of jog heights 0, 1, 2 ... steps; for an arm's jog, ARMS. */
    private static final double[] JOGS = {0, 0, 4, 6, 8, 9, 10, 10, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    private static final double[] ARMS = {0, 0, 0, 0, 0, 0, 1, 2, 4, 6, 8, 9, 10, 10, 9, 8, 6, 4, 2, 1};
    /** Steps a jog keeps clear of joints and other jogs, unless in line with them. */
    private static final int JOG_CLEAR = 5;
    /** The share of single new jogs put near their room's end (at most ARM_REACH past the clearance): an arm. */
    private static final double ARM_SHARE = 0.4;
    private static final int ARM_REACH = 4;
    /** Steps a cell is kept taller than the plan's least cross-section; the most a cell is ever tall. */
    private static final int HEIGHT_EXTRA = 2, HEIGHT_MAX = 27;

    @Override
    public TileLayout build(Look.Plan plan) {
        Random random = new Random(plan.seed ^ SALT);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            TileLayout tile = new Attempt(plan, random).build();
            if (tile != null) return tile;
        }
        throw new IllegalStateException("no layout for seed " + plan.seed);
    }

    /** A row line round the tile, y down, in steps. */
    private static final class Line {
        final int w;
        /** Jog places, sorted, in [0, w); level[i] holds from at[i] to the next jog. With no jogs, level[0] alone. */
        final int[] at, level;
        /**
         * Per place, what meets the line there from one side: {@link #JOG}, a {@link #JOINT}, a {@link #CUT} between
         * 2 cells of a room (its step's edge runs the room's full height); and the places next to one, where nothing
         * from the other side may end.
         */
        final int[] event;
        final boolean[] near;
        /** Jogs before each place of [0, 2w], places repeated a tile on. */
        final int[] count;
        static final int JOG = 1, JOINT = 2, CUT = 4;

        Line(int w, int[] at, int[] level, int[] joints, int[] cuts) {
            this.w = w;
            this.at = at;
            this.level = level;
            event = new int[w];
            for (int x : at) event[x] |= JOG;
            for (int x : joints) event[x] |= JOINT;
            for (int x : cuts) event[x] |= CUT;
            near = new boolean[w];
            for (int x = 0; x < w; x++) {
                if (event[x] == 0) continue;
                int reach = (event[x] & JOG) != 0 ? JOG_CLEAR - 1 : 1;
                for (int d = -reach; d <= reach; d++) {
                    if (event[Math.floorMod(x + d, w)] == 0) near[Math.floorMod(x + d, w)] = true;
                }
            }
            count = new int[2 * w + 1];
            for (int x = 0; x < 2 * w; x++) count[x + 1] = count[x] + ((event[x % w] & JOG) != 0 ? 1 : 0);
        }

        int levelAt(int x) {
            if (at.length == 0) return level[0];
            int i = Arrays.binarySearch(at, Math.floorMod(x, w));
            if (i < 0) i = -i - 2;
            return level[i < 0 ? at.length - 1 : i];
        }

        /** Jogs strictly between x0 and x1, for 0 <= x0 < w and x0 < x1 <= x0 + w. */
        int jogs(int x0, int x1) {
            return count[x1] - count[x0 + 1];
        }

        boolean jog(int x) {
            return (event[Math.floorMod(x, w)] & JOG) != 0;
        }

        /** The same line moved right by dx and down by dy, with the given joints and cuts meeting it. */
        Line moved(int dx, int dy, int[] joints, int[] cuts) {
            int m = at.length;
            int[] a = new int[m], l = new int[Math.max(1, m)];
            if (m == 0) l[0] = level[0] + dy;
            Integer[] order = new Integer[m];
            for (int i = 0; i < m; i++) order[i] = i;
            Arrays.sort(order, (p, q) -> Integer.compare(Math.floorMod(at[p] + dx, w), Math.floorMod(at[q] + dx, w)));
            for (int i = 0; i < m; i++) {
                a[i] = Math.floorMod(at[order[i]] + dx, w);
                l[i] = level[order[i]] + dy;
            }
            return new Line(w, a, l, shifted(joints, dx), shifted(cuts, dx));
        }

        private int[] shifted(int[] xs, int dx) {
            int[] out = new int[xs.length];
            for (int i = 0; i < xs.length; i++) out[i] = Math.floorMod(xs[i] + dx, w);
            return out;
        }
    }

    /** One try at a tile; {@link #build} gives null when a course or a row line finds no fit. */
    private static final class Attempt {
        final Random random;
        final double unit;
        final int w, h, s, n, minHeight;
        final int[] base;
        final double[] widthWeight;
        final Line[] lines;
        final int[][] joints;
        /** Row lines with no jogs; line 0 always. */
        final boolean[] straight;
        /** The tile's aim for its room count. */
        final int roomAim;
        /** {@link Look.Plan#stepsByWidth}. */
        final double[][] classes;
        /** Rooms so far per width band (under 0.3 H, 0.3 to 0.5 H, over 0.5 H), and per band and step count. */
        final int[] seen = new int[3];
        final int[][] dealt = new int[3][3];
        /** Per band, the shares of 0, 1 and 2 steps leaned toward what the band is short of. */
        final double[][] lean = new double[3][3];
        /** The last course's bottom line: the first line half a tile on, with course 0's joints and cuts on it. */
        Line last;

        Attempt(Look.Plan plan, Random random) {
            this.random = random;
            classes = plan.stepsByWidth;
            unit = plan.unitH;
            w = plan.tileWUnits;
            h = plan.tileHUnits;
            s = w / 2;
            n = Math.max(2, (int) Math.round(h / plan.courseUnits));
            int cross = (int) Math.ceil((double) plan.minCrossH / plan.unitH - 1e-3);
            minHeight = Math.max(cross, plan.minStepUnits) + HEIGHT_EXTRA;
            if (w < WIDTH_MAX + WIDTH_MIN || h < n * THIN_MIN) {
                throw new IllegalArgumentException("tile " + w + " x " + h);
            }
            base = new int[n + 1];
            int[] height = courseHeights();
            base[0] = LEVEL_REACH;
            for (int k = 0; k < n; k++) base[k + 1] = base[k] + height[k];
            widthWeight = widths(n * (double) w / (plan.roomsPerH2 * w * h * unit * unit));
            lines = new Line[n];
            joints = new int[n][];
            straight = new boolean[n];
            int count = Math.max(1, (int) Math.round(h * unit / plan.straightEveryH));
            for (int i = 0; i < count; i++) straight[(int) Math.round((double) i * n / count) % n] = true;
            roomAim = (int) Math.round(plan.roomsPerH2 * w * h * unit * unit);
            for (int b = 0; b < 3; b++) leanBand(b);
        }

        TileLayout build() {
            lines[0] = new Line(w, new int[0], new int[] {base[0]}, new int[0], new int[0]);
            int rooms = 0;
            for (int k = 0; k < n; k++) {
                int aim = (int) Math.round((double) (roomAim - rooms) / (n - k));
                int[] js = lay(k, aim);
                if (js == null) return null;
                joints[k] = js;
                rooms += js.length;
                if (k == 0) last = lines[0].moved(s, h, js, new int[0]);
                if (k < n - 1 && !nextLine(k)) return null;
                if (k == 0) last = lines[0].moved(s, h, js, cuts(lines[0], lines[1], js));
            }
            return emit();
        }

        /** A straight line with nothing on it yet: what a course over a straight line sees below it. */
        private Line flat() {
            return new Line(w, new int[0], new int[] {0}, new int[0], new int[0]);
        }

        /** Where a course's rooms are cut: at every jog of its 2 row lines that is not at one of its joints. */
        private int[] cuts(Line top, Line bottom, int[] js) {
            boolean[] cut = new boolean[w];
            for (int x : top.at) cut[x] = true;
            for (int x : bottom.at) cut[x] = true;
            for (int x : js) cut[x] = false;
            IntList out = new IntList();
            for (int x = 0; x < w; x++) if (cut[x]) out.add(x);
            return out.array();
        }

        /** Base heights round the tile, summing to h. */
        private int[] courseHeights() {
            int[] height = new int[n];
            int sum = 0;
            for (int k = 0; k < n; k++) {
                boolean thin = random.nextDouble() < THIN_SHARE;
                height[k] = thin ? THIN_MIN + random.nextInt(THIN_MAX - THIN_MIN + 1)
                        : TALL_MIN + random.nextInt(TALL_MAX - TALL_MIN + 1);
                sum += height[k];
            }
            int least = THIN_MIN, most = TALL_MAX + LEVEL_REACH;
            for (int guard = 0; sum != h && guard < 100 * h; guard++) {
                int k = random.nextInt(n), d = sum < h ? 1 : -1;
                if (height[k] + d < least || height[k] + d > most) continue;
                height[k] += d;
                sum += d;
            }
            if (sum != h) throw new IllegalArgumentException("courses do not fit " + h);
            return height;
        }

        /** Relative weights of room widths, for a mean width of about {@code mean}. */
        private static double[] widths(double mean) {
            double[] weight = new double[WIDTH_MAX + 1];
            double spread = WIDTH_SPREAD * mean, total = 0;
            for (int x = WIDTH_MIN; x <= WIDTH_MAX; x++) {
                double z = (x - mean) / spread;
                weight[x] = (1 - WIDTH_TAIL) * Math.exp(-z * z / 2) / (spread * Math.sqrt(2 * Math.PI))
                        + (x < mean ? WIDTH_TAIL / (mean - WIDTH_MIN) : 0);
                total += weight[x];
            }
            for (int x = WIDTH_MIN; x <= WIDTH_MAX; x++) weight[x] /= total;
            return weight;
        }

        /** True when course k's bottom line is set before it is laid: straight, or the first line half a tile on. */
        private boolean fixedBottom(int k) {
            return k == n - 1 || straight[k + 1];
        }

        /** The share of course k's steps its rooms take from their skyline. */
        private double share(int k) {
            if (lines[k].at.length == 0) return 0;
            return k + 1 < n && fixedBottom(k + 1) ? FEED_SHARE : TOP_SHARE;
        }

        private int band(int width) {
            double x = width * unit;
            return x < 0.3 ? 0 : x <= 0.5 ? 1 : 2;
        }

        private double[] classes(int width) {
            return lean[band(width)];
        }

        /** Counts a room of this width with m steps, and leans its band's shares again. */
        private void deal(int width, int m) {
            int b = band(width);
            seen[b]++;
            dealt[b][m]++;
            leanBand(b);
        }

        private void leanBand(int b) {
            for (int m = 0; m < 3; m++) {
                lean[b][m] = classes[b][m] * Math.exp(DEAL * (seen[b] * classes[b][m] - dealt[b][m]));
            }
        }

        /** The chance that t of a room's n steps come from its skyline. */
        private static double split(double top, int t, int n) {
            if (t > n) return 0;
            double c = n == 2 && t == 1 ? 2 : 1;
            return c * Math.pow(top, t) * Math.pow(1 - top, n - t);
        }

        /**
         * Course k's joints, sorted: of up to {@link #DRAWS} draws round the tile, each in proportion to its weight,
         * the first with {@code aim} rooms or else the nearest; null when none fit.
         */
        private int[] lay(int k, int aim) {
            Line top = lines[k];
            Line bottom = k == n - 1 ? last : straight[k + 1] ? flat() : null;
            double share = share(k);
            double[] place = new double[w];
            double placeTotal = 0;
            for (int x = 0; x < w; x++) {
                place[x] = placeWeight(top, x);
                if (bottom != null) place[x] *= placeWeight(bottom, x) * overlap(top, bottom, x);
                placeTotal += place[x];
            }
            if (placeTotal == 0) return null;
            double[][] room = new double[w][WIDTH_MAX + 1];
            for (int x = 0; x < w; x++) {
                for (int d = WIDTH_MIN; d <= WIDTH_MAX; d++) room[x][d] = room(top, bottom, share, x, d);
            }
            int[] best = null;
            int draws = k == n - 1 ? 4 * DRAWS : DRAWS;
            for (int draw = 0; draw < draws && (best == null || best.length != aim); draw++) {
                int[] js = chain(place, placeTotal, room);
                if (js != null && (best == null || Math.abs(js.length - aim) < Math.abs(best.length - aim))) best = js;
            }
            return best;
        }

        /** One draw of joints round the tile: a first joint, then the rest over all chains of rooms back to it. */
        private int[] chain(double[] place, double placeTotal, double[][] room) {
            double pick = random.nextDouble() * placeTotal;
            int p0 = 0;
            while (p0 < w - 1 && (pick -= place[p0]) > 0) p0++;
            if (place[p0] == 0) return null;
            double[] chain = new double[w + 1];
            chain[0] = 1;
            for (int i = 1; i <= w; i++) {
                double weight = i == w ? 1 : place[(p0 + i) % w];
                if (weight == 0) continue;
                double sum = 0;
                for (int d = WIDTH_MIN; d <= Math.min(WIDTH_MAX, i); d++) {
                    sum += chain[i - d] * room[(p0 + i - d) % w][d];
                }
                chain[i] = weight * sum;
            }
            if (!(chain[w] > 0)) return null;
            IntList out = new IntList();
            double[] odds = new double[WIDTH_MAX + 1];
            for (int i = w; i > 0; ) {
                Arrays.fill(odds, 0);
                for (int d = WIDTH_MIN; d <= Math.min(WIDTH_MAX, i); d++) {
                    odds[d] = chain[i - d] * room[(p0 + i - d) % w][d];
                }
                int d = draw(odds);
                if (d < 0) return null;
                i -= d;
                out.add((p0 + i) % w);
            }
            int[] js = out.array();
            Arrays.sort(js);
            return js;
        }

        /** A joint's weight at x against one row line: 0 next to what meets the line, more on some of it. */
        private double placeWeight(Line line, int x) {
            if (line.near[x]) return 0;
            int e = line.event[x];
            if ((e & Line.JOG) != 0) return (e & Line.JOINT) != 0 ? ON_JOINT_JOG : ON_JOG;
            return (e & Line.JOINT) != 0 ? THROUGH : 1;
        }

        /** 0 where a joint at x would sit on a jog of both lines with too little of a cell's side between them. */
        private int overlap(Line top, Line bottom, int x) {
            if (!top.jog(x) || !bottom.jog(x)) return 1;
            int upper = Math.max(top.levelAt(x - 1), top.levelAt(x));
            int lower = Math.min(bottom.levelAt(x - 1), bottom.levelAt(x));
            return lower - upper >= minHeight ? 1 : 0;
        }

        /** The weight of a room from x0, d wide: its width and its count of steps for that width. */
        private double room(Line top, Line bottom, double share, int x0, int d) {
            int x = Math.floorMod(x0, w), t = top.jogs(x, x + d);
            if (t > 2) return 0;
            double[] c = classes(d);
            if (bottom == null) {
                double sum = 0;
                for (int m = t; m <= 2; m++) sum += c[m] * split(share, t, m);
                return widthWeight[d] * sum;
            }
            int b = bottom.jogs(x, x + d);
            if (t + b > 2) return 0;
            if (t == 1 && b == 1 && !apart(top, bottom, x, x + d)) return 0;
            return widthWeight[d] * c[t + b];
        }

        /** True when a room's one skyline jog and one bottom jog are a cell apart or in line with room to join. */
        private boolean apart(Line top, Line bottom, int x0, int x1) {
            int a = -1, b = -1;
            for (int x = x0 + 1; x < x1; x++) {
                if (top.jog(x)) a = x;
                if (bottom.jog(x)) b = x;
            }
            if (a == b) return overlap(top, bottom, a) == 1;
            return Math.abs(a - b) >= JOG_CLEAR;
        }

        /** Course k's rooms take their classes and put their bottom jogs into line k + 1; then its levels. */
        private boolean nextLine(int k) {
            Line top = lines[k];
            int[] js = joints[k];
            double share = share(k);
            boolean[] jog = new boolean[w], arms = new boolean[w];
            // a straight line takes no jogs: its rooms above took all their steps from their skyline
            for (int i = 0; i < js.length; i++) {
                int x0 = js[i], x1 = i + 1 < js.length ? js[i + 1] : js[0] + w, d = x1 - x0, t = top.jogs(x0, x1);
                if (straight[k + 1]) {
                    deal(d, t);
                    continue;
                }
                IntList free = new IntList();
                for (int x = x0 + JOG_CLEAR; x <= x1 - JOG_CLEAR; x++) {
                    boolean clear = !top.near[x % w] && (k + 1 < n - 1 || !last.near[x % w]);
                    for (int y = x - JOG_CLEAR + 1; y < x + JOG_CLEAR && clear; y++) clear = !top.jog(y);
                    if (clear) free.add(x);
                }
                double[] c = classes(d), odds = new double[3];
                double total = 0;
                for (int m = t; m <= 2; m++) {
                    boolean fits = m - t == 0 || (m - t == 1 && free.size > 0)
                            || (m - t == 2 && free.size > 0 && free.get(free.size - 1) - free.get(0) >= JOG_CLEAR);
                    odds[m] = fits ? c[m] * split(share, t, m) : 0;
                    total += odds[m];
                }
                int m = t;
                if (total > 0) {
                    double r = random.nextDouble() * total;
                    for (m = t; m < 2 && (r -= odds[m]) > 0; ) m++;
                    while (odds[m] == 0) m--;
                }
                deal(d, m);
                if (m - t == 1) {
                    IntList arm = new IntList();
                    for (int a = 0; a < free.size; a++) {
                        int x = free.get(a);
                        if (Math.min(x - x0, x1 - x) <= JOG_CLEAR + ARM_REACH) arm.add(x);
                    }
                    boolean deep = arm.size > 0 && random.nextDouble() < ARM_SHARE;
                    IntList from = deep ? arm : free;
                    int x = from.get(random.nextInt(from.size)) % w;
                    jog[x] = true;
                    arms[x] = deep;
                }
                if (m - t == 2) {
                    // a pair drawn evenly from the pairs a cell apart
                    IntList pairs = new IntList();
                    for (int a = 0; a < free.size; a++) {
                        for (int b = a + 1; b < free.size; b++) {
                            if (free.get(b) - free.get(a) >= JOG_CLEAR) pairs.add(a * free.size + b);
                        }
                    }
                    int pair = pairs.get(random.nextInt(pairs.size));
                    jog[free.get(pair / free.size) % w] = true;
                    jog[free.get(pair % free.size) % w] = true;
                }
            }
            // jogs under joints: a step for the room below
            IntList under = new IntList();
            for (int x : js) {
                if (!top.jog(x) && (k + 1 < n - 1 || !last.near[x])) under.add(x);
            }
            for (int i = 0; i < under.size && !straight[k + 1]; i++) {
                if (random.nextDouble() < (fixedBottom(k + 1) ? FEED_JOINT_JOG : JOINT_JOG)) jog[under.get(i)] = true;
            }
            int count = 0;
            for (boolean b : jog) if (b) count++;
            // a line that is not straight jogs a few times at least (and never once: it could not close)
            while (!straight[k + 1] && count < Math.max(2, Math.round(LINE_JOGS * w * unit))) {
                IntList spare = new IntList();
                for (int i = 0; i < under.size; i++) if (!jog[under.get(i)]) spare.add(under.get(i));
                if (spare.size == 0) break;
                jog[spare.get(random.nextInt(spare.size))] = true;
                count++;
            }
            if (count == 1) return false;
            IntList at = new IntList();
            for (int x = 0; x < w; x++) if (jog[x]) at.add(x);
            boolean[] deep = new boolean[at.size];
            for (int i = 0; i < at.size; i++) deep[i] = arms[at.get(i)];
            int[] level = levels(k + 1, at.array(), deep);
            if (level == null) return false;
            Line next = new Line(w, at.array(), level, new int[0], new int[0]);
            lines[k + 1] = new Line(w, next.at, level, js, cuts(top, next, js));
            return true;
        }

        /** Levels for row line j between jogs at the given places, drawn round the tile; null when none fit. */
        private int[] levels(int j, int[] at, boolean[] deep) {
            int m = at.length, reach = LEVEL_REACH, size = 2 * reach + 1, y = base[j];
            Line above = lines[j - 1];
            boolean lastLine = j == n - 1;
            int segments = Math.max(1, m);
            // per segment, the levels the cells above and below it allow, and the course height it would give
            int[] lo = new int[segments], hi = new int[segments];
            double[] mean = new double[segments];
            for (int i = 0; i < segments; i++) {
                int x0 = m == 0 ? 0 : at[i], x1 = m == 0 ? w : i + 1 < m ? at[i + 1] : at[0] + w;
                int deepest = Integer.MIN_VALUE, shallowest = Integer.MAX_VALUE;
                int highest = Integer.MAX_VALUE, lowest = Integer.MIN_VALUE;
                for (int x = x0; x < x1; x++) {
                    int top = above.levelAt(x);
                    deepest = Math.max(deepest, top);
                    shallowest = Math.min(shallowest, top);
                    mean[i] += top / (double) (x1 - x0);
                    if (lastLine) {
                        highest = Math.min(highest, last.levelAt(x));
                        lowest = Math.max(lowest, last.levelAt(x));
                    }
                }
                lo[i] = deepest + minHeight;
                hi[i] = Math.min(lastLine ? highest - minHeight : base[j + 1] + reach - minHeight,
                        shallowest + HEIGHT_MAX);
                if (lastLine) lo[i] = Math.max(lo[i], lowest - HEIGHT_MAX);
            }
            double[][] weight = new double[segments][size];
            int course = base[j] - base[j - 1];
            for (int i = 0; i < segments; i++) {
                for (int v = 0; v < size; v++) {
                    int level = y - reach + v;
                    double z = (level - y) / LEVEL_SPREAD, zh = (level - mean[i] - course) / HEIGHT_SPREAD;
                    weight[i][v] = level >= lo[i] && level <= hi[i] ? Math.exp(-(z * z + zh * zh) / 2) : 0;
                }
            }
            if (m == 0) {
                int v = draw(weight[0]);
                return v < 0 ? null : new int[] {y - reach + v};
            }
            // forward over the cycle for each first level, then draw the first level and walk back
            double[][][] f = new double[size][][];
            double[] first = new double[size];
            for (int v0 = 0; v0 < size; v0++) {
                if (weight[0][v0] == 0) continue;
                double[][] g = new double[m][size];
                g[0][v0] = weight[0][v0];
                for (int i = 1; i < m; i++) {
                    for (int v = 0; v < size; v++) {
                        if (weight[i][v] == 0) continue;
                        double sum = 0;
                        for (int u = 0; u < size; u++) if (g[i - 1][u] > 0) sum += g[i - 1][u] * jog(u, v, deep[i]);
                        g[i][v] = weight[i][v] * sum;
                    }
                }
                double sum = 0;
                for (int u = 0; u < size; u++) sum += g[m - 1][u] * jog(u, v0, deep[0]);
                first[v0] = sum;
                f[v0] = g;
            }
            int v0 = draw(first);
            if (v0 < 0) return null;
            int[] out = new int[m];
            out[0] = v0;
            int next = v0;
            for (int i = m - 1; i >= 1; i--) {
                double[] odds = new double[size];
                for (int u = 0; u < size; u++) odds[u] = f[v0][i][u] * jog(u, next, deep[(i + 1) % m]);
                next = draw(odds);
                out[i] = next;
            }
            for (int i = 0; i < m; i++) out[i] += y - reach;
            return out;
        }

        private static double jog(int u, int v, boolean arm) {
            double[] table = arm ? ARMS : JOGS;
            int d = Math.abs(u - v);
            return d < table.length ? table[d] : 0;
        }

        /** An index drawn in proportion to its weight; -1 when all are 0. */
        private int draw(double[] odds) {
            double total = 0;
            for (double o : odds) total += o;
            if (!(total > 0)) return -1;
            double r = random.nextDouble() * total;
            int i = 0;
            while (i < odds.length - 1 && (r -= odds[i]) > 0) i++;
            while (odds[i] == 0) i--;
            return i;
        }

        /** Cells: each room cut at its steps, as tall as its course is there. */
        private TileLayout emit() {
            IntList x0s = new IntList(), y0s = new IntList(), x1s = new IntList(), y1s = new IntList();
            IntList rooms = new IntList();
            int room = 0;
            for (int k = 0; k < n; k++) {
                Line top = lines[k], bottom = k < n - 1 ? lines[k + 1] : last;
                int[] js = joints[k];
                for (int i = 0; i < js.length; i++, room++) {
                    int x0 = js[i], x1 = i + 1 < js.length ? js[i + 1] : js[0] + w;
                    int from = x0;
                    for (int x = x0 + 1; x <= x1; x++) {
                        if (x < x1 && !top.jog(x) && !bottom.jog(x)) continue;
                        x0s.add(from);
                        x1s.add(x);
                        y0s.add(top.levelAt(from));
                        y1s.add(bottom.levelAt(from));
                        rooms.add(room);
                        from = x;
                    }
                }
            }
            return new TileLayout(w, h, room, x0s.array(), y0s.array(), x1s.array(), y1s.array(), rooms.array());
        }
    }
}
