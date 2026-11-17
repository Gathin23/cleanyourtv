package io.github.pbanddev.tatami;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The static shape of a {@link TileLayout}, worked out once and checked: the lines its cells sit on, the wall pieces
 * on those lines, the groups of lines that move as one, and the rules that keep every rest state of the motion
 * valid. Sizes and places are whole steps.
 *
 * <p>Places are on the plane with the tile's copy (0, 0) at the origin; copy (i, j) sits at
 * {@code (i * width + j * shift, j * height)}. At time t, a cell's edge or a piece's end is at its place here plus
 * the offset of the group its line moves with ({@link TileMotion}).
 *
 * <ul>
 *   <li><b>Lines:</b> maximal straight runs of cell edges, followed across the tile's seams. A row line that runs
 *       all the way round is one periodic line.</li>
 *   <li><b>Pieces:</b> the stretches of a line between the same 2 rooms, merged, and listed once per tile.</li>
 *   <li><b>Rules:</b> each says {@code gap + offset[hi] - offset[lo] >= min} for 2 groups: every cell's width and
 *       height and every room's width (each at least {@link Look.Motion#keepShare} of its size at home), every step
 *       and T-offset (2 lines that end on a third from its 2 sides), every door host's length. They hold at home.
 *       Any offsets that keep them keep every line's order, so the cells still tile the plane and rooms keep their
 *       neighbours. {@link TileMotion} keeps them at every moment of every glide.</li>
 *   <li><b>Groups:</b> lines glued to move as one. A rule with less than {@link #GLUE_SLACK} to spare at home glues
 *       its 2 lines, so thin cells, small steps and short door hosts move whole instead of standing still.</li>
 * </ul>
 */
final class TileGraph {
    /** Rules with less slack than this at home glue their 2 lines into one group. */
    static final int GLUE_SLACK = 3;
    /** How far along its length a line looks for another to line up with, and the most it keeps. */
    private static final int ALIGN_REACH = 40, ALIGN_PARTNERS = 6;

    final int width, height, shift, rooms, cells;
    /** Least cell height and room width, least step, least cell width, least door host, farthest offset: steps. */
    final int minCross, minStep, minCell, hostMin, maxUnits;
    /** The share of its size at home that a cell's width or height or a room's width always keeps. */
    private final float keepShare;
    final int[] cellX0, cellY0, cellX1, cellY1, cellRoom;
    /** The group each cell edge moves with. */
    final int[] cellLeft, cellRight, cellTop, cellBottom;
    /** Room r's cells: roomCellList[roomCellStart[r] .. roomCellStart[r + 1]). */
    final int[] roomCellStart, roomCellList;
    /** Lower bounds of a one-cell room's width and height at any time; -1 for a room of more cells. */
    final int[] roomMinW, roomMinH;

    final int lines;
    final boolean[] lineVertical, linePeriodic;
    /** A line's place across (x for a vertical line) and its ends along it, on the plane at home. */
    final int[] lineAt, lineFrom, lineTo, lineGroup;

    final int pieces, pairs;
    final boolean[] pieceVertical;
    final int[] pieceAt, pieceFrom, pieceTo, pieceLine, pieceGroup, pieceFromGroup, pieceToGroup;
    /** The room ids on its low side (left of it, or above it) and high side; its pair of rooms. */
    final int[] pieceRoomA, pieceRoomB, piecePair;
    /** A lower bound of its length at any time. */
    final int[] pieceMinLength;
    /** True for the pieces that may hold a door ({@link TilePlan#doorHosts}). */
    final boolean[] pieceHost;

    final int groups;
    /** Wall length on a group's lines, per tile, at home. 0: moving it would show nothing. */
    final int[] groupLength;
    final int[] ruleHi, ruleLo, ruleGap, ruleMin;
    /** The rules on group g: ruleList[ruleStart[g] .. ruleStart[g + 1]). */
    final int[] ruleStart, ruleList;
    /** Lines group g may line up with: group alignGroup[a] sits alignDelta[a] steps past it, a from alignStart[g]. */
    final int[] alignStart, alignGroup, alignDelta;

    /** Per unit square of the tile, the cell that covers it, and that cell's copy (packed). */
    private final int[] squareCell, squareCopy;

    TileGraph(TileLayout tile, Look look) {
        Look.Plan plan = look.plan;
        width = tile.width;
        height = tile.height;
        shift = tile.shift;
        rooms = tile.rooms;
        cells = tile.cells();
        cellX0 = tile.cellX0;
        cellY0 = tile.cellY0;
        cellX1 = tile.cellX1;
        cellY1 = tile.cellY1;
        cellRoom = tile.cellRoom;
        minCross = steps((double) plan.minCrossH / plan.unitH);
        minStep = plan.minStepUnits;
        minCell = plan.minCellUnits;
        hostMin = steps((double) look.doors.hostBars * look.doors.lengthH / plan.unitH);
        maxUnits = look.motion.maxUnits;
        keepShare = look.motion.keepShare;
        if (minStep < 1 || minCell < 1 || minCross < 1 || maxUnits < 1 || !(keepShare >= 0 && keepShare < 1)) {
            throw new IllegalArgumentException("minimums");
        }

        // the plane at home: every unit square of the tile covered by exactly one cell copy
        int n = width * height;
        squareCell = new int[n];
        squareCopy = new int[n];
        Arrays.fill(squareCell, -1);
        for (int c = 0; c < cells; c++) {
            for (int y = cellY0[c]; y < cellY1[c]; y++) {
                int j = Math.floorDiv(y, height), yb = y - j * height;
                for (int x = cellX0[c]; x < cellX1[c]; x++) {
                    int xr = x - j * shift, i = Math.floorDiv(xr, width), k = xr - i * width + yb * width;
                    if (squareCell[k] >= 0) {
                        throw new IllegalArgumentException("cells " + squareCell[k] + " and " + c + " overlap at " + x + ", " + y);
                    }
                    squareCell[k] = c;
                    squareCopy[k] = pack(-i, -j);
                }
            }
        }
        for (int k = 0; k < n; k++) {
            if (squareCell[k] < 0) throw new IllegalArgumentException("no cell covers " + k % width + ", " + k / width);
        }

        // rooms: 1 to 3 cells that join along edges
        roomCellStart = new int[rooms + 1];
        for (int c = 0; c < cells; c++) roomCellStart[cellRoom[c] + 1]++;
        for (int r = 0; r < rooms; r++) roomCellStart[r + 1] += roomCellStart[r];
        roomCellList = new int[cells];
        int[] fill = Arrays.copyOf(roomCellStart, rooms);
        for (int c = 0; c < cells; c++) roomCellList[fill[cellRoom[c]]++] = c;
        for (int r = 0; r < rooms; r++) checkRoom(r);

        // lines: maximal straight runs of edges between 2 cell copies, followed across the seams
        int[] vLine = new int[n], hLine = new int[n];
        Arrays.fill(vLine, -1);
        Arrays.fill(hLine, -1);
        IntList lv = new IntList(), la = new IntList(), lf = new IntList(), lt = new IntList(), lp = new IntList();
        for (int pass = 0; pass < 2; pass++) {
            boolean vertical = pass == 0;
            int[] of = vertical ? vLine : hLine;
            for (int k = 0; k < n; k++) {
                int x = k % width, y = k / width, at = vertical ? x : y, a0 = vertical ? y : x;
                if (of[k] >= 0 || !edge(vertical, at, a0)) continue;
                int id = lv.size;
                of[k] = id;
                boolean periodic = false;
                int a = a0 + 1;
                for (; edge(vertical, at, a); a++) {
                    int e = edgeIndex(vertical, at, a);
                    if (e == k) {
                        periodic = true;
                        break;
                    }
                    of[e] = id;
                    if (a - a0 > n) throw new IllegalArgumentException("a line runs on for good");
                }
                int to = a, from = a0;
                if (!periodic) {
                    for (a = a0 - 1; edge(vertical, at, a); a--) {
                        of[edgeIndex(vertical, at, a)] = id;
                        if (a0 - a > n) throw new IllegalArgumentException("a line runs on for good");
                    }
                    from = a + 1;
                }
                lv.add(vertical ? 1 : 0);
                la.add(at);
                lf.add(from);
                lt.add(to);
                lp.add(periodic ? 1 : 0);
            }
        }
        lines = lv.size;
        lineVertical = new boolean[lines];
        linePeriodic = new boolean[lines];
        for (int l = 0; l < lines; l++) {
            lineVertical[l] = lv.get(l) == 1;
            linePeriodic[l] = lp.get(l) == 1;
        }
        lineAt = la.array();
        lineFrom = lf.array();
        lineTo = lt.array();

        // the lines of every cell edge
        int[] left = new int[cells], right = new int[cells], top = new int[cells], bottom = new int[cells];
        for (int c = 0; c < cells; c++) {
            left[c] = vLine[index(cellX0[c], cellY0[c])];
            right[c] = vLine[index(cellX1[c], cellY0[c])];
            top[c] = hLine[index(cellX0[c], cellY0[c])];
            bottom[c] = hLine[index(cellX0[c], cellY1[c])];
        }

        // rules between lines (hi, lo, gap, min), then pieces with their T-offset and step rules
        IntList rHi = new IntList(), rLo = new IntList(), rGap = new IntList(), rMin = new IntList();
        for (int c = 0; c < cells; c++) {
            boolean alone = roomCellStart[cellRoom[c] + 1] - roomCellStart[cellRoom[c]] == 1;
            int w = cellX1[c] - cellX0[c], h = cellY1[c] - cellY0[c];
            rule(rHi, rLo, rGap, rMin, right[c], left[c], w, kept(w, alone ? Math.max(minCell, minCross) : minCell),
                    "cell " + c + " width");
            rule(rHi, rLo, rGap, rMin, bottom[c], top[c], h, kept(h, minCross), "cell " + c + " height");
        }
        for (int r = 0; r < rooms; r++) {
            if (roomCellStart[r + 1] - roomCellStart[r] < 2) continue;
            int lo = -1, hi = -1;
            for (int i = roomCellStart[r]; i < roomCellStart[r + 1]; i++) {
                int c = roomCellList[i];
                if (lo < 0 || cellX0[c] < cellX0[lo]) lo = c;
                if (hi < 0 || cellX1[c] > cellX1[hi]) hi = c;
            }
            int w = cellX1[hi] - cellX0[lo];
            rule(rHi, rLo, rGap, rMin, right[hi], left[lo], w, kept(w, minCross), "room " + r + " width");
        }
        IntList pv = new IntList(), pa = new IntList(), pf = new IntList(), pt = new IntList(), pl = new IntList(),
                pfl = new IntList(), ptl = new IntList(), pra = new IntList(), prb = new IntList(), pp = new IntList();
        Map<Long, Integer> pairOf = new HashMap<>();
        IntList evAt = new IntList(), evLine = new IntList(), evSide = new IntList();
        for (int l = 0; l < lines; l++) {
            boolean v = lineVertical[l];
            int at = lineAt[l], from = lineFrom[l], to = lineTo[l], period = to - from;
            boolean periodic = linePeriodic[l];
            int end = periodic ? from + 2 * period : to;
            int[] perp = v ? hLine : vLine;
            // events: lines that end on this one or cross it, by side (1 low, 2 high, 3 both)
            evAt.size = 0;
            evLine.size = 0;
            evSide.size = 0;
            for (int a = from; a <= end; a++) {
                boolean low = edge(!v, a, at - 1), high = edge(!v, a, at);
                if (!low && !high) continue;
                evAt.add(a);
                evLine.add(perp[edgeIndex(!v, a, low ? at - 1 : at)]);
                evSide.add((low ? 1 : 0) | (high ? 2 : 0));
            }
            if (!periodic && (evAt.size == 0 || evAt.get(0) != from || evAt.get(evAt.size - 1) != to)) {
                throw new IllegalArgumentException("line " + l + " has a loose end");
            }
            for (int e = 0; e + 1 < evAt.size && (!periodic || evAt.get(e) < from + period); e++) {
                if ((evSide.get(e) | evSide.get(e + 1)) == 3 && evSide.get(e) != 3 && evSide.get(e + 1) != 3) {
                    rule(rHi, rLo, rGap, rMin, evLine.get(e + 1), evLine.get(e), evAt.get(e + 1) - evAt.get(e), minStep,
                            (v ? "x " : "y ") + at + " near " + evAt.get(e) + ": a step or T-offset");
                }
            }
            // pieces: runs of wall between the same 2 room copies; a periodic line starts where its rooms change
            int a0 = from, a1 = to;
            if (periodic) {
                a0 = -1;
                for (int a = from; a < from + period && a0 < 0; a++) {
                    if (sideRoom(v, at, a, 0) != sideRoom(v, at, a - 1, 0) || sideRoom(v, at, a, 1) != sideRoom(v, at, a - 1, 1)) {
                        a0 = a;
                    }
                }
                a1 = a0 + period;
            }
            long runA = 0, runB = 0;
            int start = Integer.MIN_VALUE;
            for (int a = a0; a <= a1; a++) {
                long ra = a < a1 ? sideRoom(v, at, a, 0) : 0, rb = a < a1 ? sideRoom(v, at, a, 1) : 0;
                boolean wall = a < a1 && ra != rb;
                boolean same = wall && start != Integer.MIN_VALUE && ra == runA && rb == runB;
                if (start != Integer.MIN_VALUE && !same) {
                    pv.add(v ? 1 : 0);
                    pa.add(at);
                    pf.add(start);
                    pt.add(a);
                    pl.add(l);
                    pfl.add(eventLine(evAt, evLine, start));
                    ptl.add(eventLine(evAt, evLine, a));
                    pra.add((int) (runA >>> 32));
                    prb.add((int) (runB >>> 32));
                    long key = pairKey(runA, runB);
                    Integer pair = pairOf.get(key);
                    if (pair == null) {
                        pair = pairOf.size();
                        pairOf.put(key, pair);
                    }
                    pp.add(pair);
                    start = Integer.MIN_VALUE;
                }
                if (wall && start == Integer.MIN_VALUE) {
                    start = a;
                    runA = ra;
                    runB = rb;
                }
            }
        }
        pieces = pv.size;
        pairs = pairOf.size();
        pieceVertical = new boolean[pieces];
        for (int p = 0; p < pieces; p++) pieceVertical[p] = pv.get(p) == 1;
        pieceAt = pa.array();
        pieceFrom = pf.array();
        pieceTo = pt.array();
        pieceLine = pl.array();
        pieceRoomA = pra.array();
        pieceRoomB = prb.array();
        piecePair = pp.array();
        int[] pieceFromLine = pfl.array(), pieceToLine = ptl.array();

        pieceHost = TilePlan.doorHosts(this, look);
        for (int p = 0; p < pieces; p++) {
            if (pieceHost[p]) {
                rule(rHi, rLo, rGap, rMin, pieceToLine[p], pieceFromLine[p], pieceTo[p] - pieceFrom[p], hostMin, "door host " + p);
            }
        }

        // groups: lines glued where a rule has little slack, tightest first
        int rules = rHi.size;
        int[] parent = new int[lines];
        for (int l = 0; l < lines; l++) parent[l] = l;
        Integer[] bySlack = new Integer[rules];
        for (int r = 0; r < rules; r++) bySlack[r] = r;
        Arrays.sort(bySlack, (a, b) -> Integer.compare(rGap.get(a) - rMin.get(a), rGap.get(b) - rMin.get(b)));
        for (int r : bySlack) {
            if (rGap.get(r) - rMin.get(r) >= GLUE_SLACK) break;
            int a = root(parent, rHi.get(r)), b = root(parent, rLo.get(r));
            if (a != b) parent[Math.max(a, b)] = Math.min(a, b);
        }
        lineGroup = new int[lines];
        int[] groupOfRoot = new int[lines];
        Arrays.fill(groupOfRoot, -1);
        int count = 0;
        for (int l = 0; l < lines; l++) {
            int r = root(parent, l);
            if (groupOfRoot[r] < 0) groupOfRoot[r] = count++;
            lineGroup[l] = groupOfRoot[r];
        }
        groups = count;
        cellLeft = groupsOf(left);
        cellRight = groupsOf(right);
        cellTop = groupsOf(top);
        cellBottom = groupsOf(bottom);
        pieceGroup = groupsOf(pieceLine);
        pieceFromGroup = groupsOf(pieceFromLine);
        pieceToGroup = groupsOf(pieceToLine);
        groupLength = new int[groups];
        for (int p = 0; p < pieces; p++) groupLength[pieceGroup[p]] += pieceTo[p] - pieceFrom[p];

        // rules between groups, the tightest of each kind kept once
        Map<Long, Integer> ruleOf = new HashMap<>();
        IntList gHi = new IntList(), gLo = new IntList(), gGap = new IntList(), gMin = new IntList();
        for (int r = 0; r < rules; r++) {
            int hi = lineGroup[rHi.get(r)], lo = lineGroup[rLo.get(r)];
            if (hi == lo) continue;
            long key = ((long) hi << 42) | ((long) lo << 21) | rGap.get(r);
            Integer at = ruleOf.get(key);
            if (at == null) {
                ruleOf.put(key, gHi.size);
                gHi.add(hi);
                gLo.add(lo);
                gGap.add(rGap.get(r));
                gMin.add(rMin.get(r));
            } else if (rMin.get(r) > gMin.get(at)) {
                gMin.set(at, rMin.get(r));
            }
        }
        ruleHi = gHi.array();
        ruleLo = gLo.array();
        ruleGap = gGap.array();
        ruleMin = gMin.array();
        ruleStart = new int[groups + 1];
        for (int r = 0; r < ruleHi.length; r++) {
            ruleStart[ruleHi[r] + 1]++;
            ruleStart[ruleLo[r] + 1]++;
        }
        for (int g = 0; g < groups; g++) ruleStart[g + 1] += ruleStart[g];
        ruleList = new int[ruleStart[groups]];
        fill = Arrays.copyOf(ruleStart, groups);
        for (int r = 0; r < ruleHi.length; r++) {
            ruleList[fill[ruleHi[r]]++] = r;
            ruleList[fill[ruleLo[r]]++] = r;
        }

        // lower bounds over all time, for doors and fixtures
        pieceMinLength = new int[pieces];
        for (int p = 0; p < pieces; p++) {
            int length = pieceTo[p] - pieceFrom[p];
            int least = pieceFromGroup[p] == pieceToGroup[p] ? length : Math.max(length - 2 * maxUnits, Math.min(minStep, minCell));
            pieceMinLength[p] = pieceHost[p] ? Math.max(least, hostMin) : least;
        }
        roomMinW = new int[rooms];
        roomMinH = new int[rooms];
        for (int r = 0; r < rooms; r++) {
            if (roomCellStart[r + 1] - roomCellStart[r] > 1) {
                roomMinW[r] = -1;
                roomMinH[r] = -1;
                continue;
            }
            int c = roomCellList[roomCellStart[r]], w = cellX1[c] - cellX0[c], h = cellY1[c] - cellY0[c];
            roomMinW[r] = cellLeft[c] == cellRight[c] ? w : Math.max(w - 2 * maxUnits, kept(w, Math.max(minCell, minCross)));
            roomMinH[r] = cellTop[c] == cellBottom[c] ? h : Math.max(h - 2 * maxUnits, kept(h, minCross));
        }

        // partners to line up with: parallel lines a little way along, within reach across
        IntList[] partners = new IntList[groups];
        for (int l = 0; l < lines; l++) {
            int g = lineGroup[l];
            if (groupLength[g] == 0 || linePeriodic[l]) continue;
            boolean v = lineVertical[l];
            for (int q = 0; q < lines; q++) {
                if (lineVertical[q] != v || linePeriodic[q] || lineGroup[q] == g) continue;
                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        int across = lineAt[q] + (v ? i * width + j * shift : j * height);
                        int along = v ? j * height : i * width + j * shift;
                        int delta = across - lineAt[l];
                        int apart = Math.max(lineFrom[q] + along - lineTo[l], lineFrom[l] - lineTo[q] - along);
                        if (Math.abs(delta) > 2 * maxUnits || apart <= 0 || apart > ALIGN_REACH) continue;
                        if (partners[g] == null) partners[g] = new IntList();
                        partners[g].add(lineGroup[q]);
                        partners[g].add(delta);
                        partners[g].add(apart);
                    }
                }
            }
        }
        alignStart = new int[groups + 1];
        IntList ag = new IntList(), ad = new IntList();
        for (int g = 0; g < groups; g++) {
            alignStart[g] = ag.size;
            if (partners[g] == null) continue;
            IntList list = partners[g];
            Integer[] order = new Integer[list.size / 3];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> {
                int da = Math.abs(list.get(3 * a + 1)), db = Math.abs(list.get(3 * b + 1));
                if (da != db) return Integer.compare(da, db);
                return Integer.compare(list.get(3 * a + 2), list.get(3 * b + 2));
            });
            for (int i = 0; i < order.length && ag.size - alignStart[g] < ALIGN_PARTNERS; i++) {
                int partner = list.get(3 * order[i]), delta = list.get(3 * order[i] + 1);
                boolean seen = false;
                for (int s = alignStart[g]; s < ag.size; s++) seen |= ag.get(s) == partner && ad.get(s) == delta;
                if (seen) continue;
                ag.add(partner);
                ad.add(delta);
            }
        }
        alignStart[groups] = ag.size;
        alignGroup = ag.array();
        alignDelta = ad.array();
    }

    /** The room copy on one side of a line at unit stretch a: side 0 is left of it (or above), 1 right (or below). */
    long sideRoom(boolean vertical, int at, int a, int side) {
        return vertical ? roomAt(at - 1 + side, a) : roomAt(a, at - 1 + side);
    }

    /** The room copy that covers unit square (x, y) of the plane: the room id in the high half, its copy in the low. */
    long roomAt(int x, int y) {
        long c = cellAt(x, y);
        return ((long) cellRoom[(int) (c >>> 32)] << 32) | (c & 0xFFFFFFFFL);
    }

    /** The cell copy that covers unit square (x, y) of the plane: the cell in the high half, its copy in the low. */
    long cellAt(int x, int y) {
        int j = Math.floorDiv(y, height), xr = x - j * shift, i = Math.floorDiv(xr, width);
        int k = xr - i * width + (y - j * height) * width, p = squareCopy[k];
        return ((long) squareCell[k] << 32) | (pack((p >> 16) + i, (short) p + j) & 0xFFFFFFFFL);
    }

    /** The tile square that unit square (x, y) of the plane is a copy of. */
    private int index(int x, int y) {
        int j = Math.floorDiv(y, height), xr = x - j * shift, i = Math.floorDiv(xr, width);
        return xr - i * width + (y - j * height) * width;
    }

    /**
     * True when the unit edge that starts at {@code a} along a line at {@code at} parts 2 cell copies: the edge from
     * (at, a) to (at, a + 1) for a vertical line, from (a, at) to (a + 1, at) for a horizontal one.
     */
    private boolean edge(boolean vertical, int at, int a) {
        return vertical ? cellAt(at - 1, a) != cellAt(at, a) : cellAt(a, at - 1) != cellAt(a, at);
    }

    private int edgeIndex(boolean vertical, int at, int a) {
        return vertical ? index(at, a) : index(a, at);
    }

    private void checkRoom(int r) {
        int from = roomCellStart[r], to = roomCellStart[r + 1];
        if (to - from < 1 || to - from > 3) throw new IllegalArgumentException("room " + r + " has " + (to - from) + " cells");
        // joined: grow from the first cell through shared edges
        int joined = 1 << 0, size = to - from;
        for (int pass = 0; pass < size; pass++) {
            for (int a = 0; a < size; a++) {
                if ((joined & (1 << a)) == 0) continue;
                for (int b = 0; b < size; b++) {
                    if (touch(roomCellList[from + a], roomCellList[from + b])) joined |= 1 << b;
                }
            }
        }
        if (joined != (1 << size) - 1) throw new IllegalArgumentException("room " + r + " is in pieces");
    }

    /** True when 2 cells share a stretch of edge. */
    private boolean touch(int a, int b) {
        boolean side = (cellX1[a] == cellX0[b] || cellX1[b] == cellX0[a])
                && Math.min(cellY1[a], cellY1[b]) > Math.max(cellY0[a], cellY0[b]);
        boolean end = (cellY1[a] == cellY0[b] || cellY1[b] == cellY0[a])
                && Math.min(cellX1[a], cellX1[b]) > Math.max(cellX0[a], cellX0[b]);
        return side || end;
    }

    /**
     * The least a cell's width or height or a room's width may get, in steps, from its size at home: least, and
     * never under {@link #keepShare} of its size.
     */
    int kept(int size, int least) {
        return Math.max(least, steps((double) keepShare * size));
    }

    private int[] groupsOf(int[] lineIds) {
        int[] out = new int[lineIds.length];
        for (int i = 0; i < out.length; i++) out[i] = lineGroup[lineIds[i]];
        return out;
    }

    private static void rule(IntList hi, IntList lo, IntList gap, IntList min, int h, int l, int g, int m, String what) {
        if (g < m) throw new IllegalArgumentException(what + " is " + g + " steps, below " + m);
        hi.add(h);
        lo.add(l);
        gap.add(g);
        min.add(m);
    }

    /** The line of the event at place a; there is always one where a piece starts or ends. */
    private static int eventLine(IntList at, IntList line, int a) {
        int lo = 0, hi = at.size - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (at.get(mid) < a) lo = mid + 1; else if (at.get(mid) > a) hi = mid - 1; else return line.get(mid);
        }
        throw new IllegalArgumentException("a piece ends where nothing meets its line, at " + a);
    }

    /** The same key for both orders of 2 room copies, and for every copy of the pair. */
    private static long pairKey(long a, long b) {
        int ra = (int) (a >>> 32), rb = (int) (b >>> 32);
        int di = ((int) b >> 16) - ((int) a >> 16), dj = (short) b - (short) a;
        if (ra > rb || (ra == rb && (di < 0 || (di == 0 && dj < 0)))) {
            int r = ra;
            ra = rb;
            rb = r;
            di = -di;
            dj = -dj;
        }
        return ((long) ra << 42) | ((long) rb << 21) | ((di & 0x3FFL) << 10) | (dj & 0x3FFL);
    }

    private static int pack(int i, int j) {
        return (i << 16) | (j & 0xFFFF);
    }

    private static int root(int[] parent, int l) {
        while (parent[l] != l) l = parent[l] = parent[parent[l]];
        return l;
    }

    /** A size in steps, rounded up. */
    private static int steps(double units) {
        return (int) Math.ceil(units - 1e-3);
    }
}
