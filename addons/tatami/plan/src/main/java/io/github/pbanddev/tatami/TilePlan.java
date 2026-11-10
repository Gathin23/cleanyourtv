package io.github.pbanddev.tatami;

import java.util.Arrays;

/**
 * Nagaya's {@link Layout}: one tile of rooms ({@link TileBuilder}, checked and worked out by {@link TileGraph})
 * repeated on a staggered lattice, its lines moved by {@link TileMotion}, and cut down to what the view can show.
 *
 * <p>Each frame puts every group of lines at its offset for time t, then, for each copy of the tile that can show,
 * emits the cells and wall pieces the view sees, grown by the blur's strip (in the view), a door's swing and half a
 * wall. Each cell and piece belongs to one copy and is emitted whole, so pieces that run across a seam are never
 * cut. Rooms are boxed from all their cells. Every copy shows the same paints, doors and motion.
 *
 * <p>The frame's arrays are sized from the most the widest views can ask for, with every line anywhere in its range.
 * A frame that wants more leaves the rest out and sets {@link Frame#clipped}; nothing here throws once built, for any
 * time.
 */
final class TilePlan implements Layout {
    /** The widest views, sampled to size the frame: this many centres each way over the tile. */
    private static final int SAMPLES = 7;
    /** A view this size, the smallest the app draws, sees the most plan: the blur's strip is the same px on all. */
    private static final int SAMPLE_W = 1280, SAMPLE_H = 720;
    private static final long HOSTS = 0x6D6F7665L;
    /** A view never needs more copies than this each way, nor a centre farther out than this many steps. */
    private static final int COPIES = 64;
    private static final double LATTICE_REACH = 1e6;

    private final Look look;
    final TileGraph graph;
    final TileMotion motion;
    private final float[] offset;
    private final int[] roomIndex, touched;
    private final double[] reach = new double[4];
    /** The box every cell and piece of copy (0, 0) stays inside at any time, in steps. */
    private final int minX, minY, maxX, maxY;
    private final int cellCapacity, wallCapacity, doorCapacity, fillCapacity;

    TilePlan(Look look, TileBuilder builder) {
        this.look = look;
        graph = new TileGraph(builder.build(look.plan), look);
        motion = new TileMotion(look, graph);
        offset = new float[graph.groups];
        roomIndex = new int[graph.rooms];
        Arrays.fill(roomIndex, -1);
        touched = new int[graph.rooms];
        TileGraph g = graph;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int c = 0; c < g.cells; c++) {
            x0 = Math.min(x0, g.cellX0[c]);
            y0 = Math.min(y0, g.cellY0[c]);
            x1 = Math.max(x1, g.cellX1[c]);
            y1 = Math.max(y1, g.cellY1[c]);
        }
        for (int p = 0; p < g.pieces; p++) {
            boolean v = g.pieceVertical[p];
            x0 = Math.min(x0, v ? g.pieceAt[p] : g.pieceFrom[p]);
            y0 = Math.min(y0, v ? g.pieceFrom[p] : g.pieceAt[p]);
            x1 = Math.max(x1, v ? g.pieceAt[p] : g.pieceTo[p]);
            y1 = Math.max(y1, v ? g.pieceTo[p] : g.pieceAt[p]);
        }
        int r = g.maxUnits;
        minX = x0 - r;
        minY = y0 - r;
        maxX = x1 + r;
        maxY = y1 + r;
        int[] most = widest();
        cellCapacity = most[0] + most[0] / 5 + 16;
        wallCapacity = most[1] + most[1] / 5 + 16;
        fillCapacity = most[2] + most[2] / 5 + 16;
        doorCapacity = most[3] + most[3] / 5 + 16;
    }

    /**
     * Picks the wall pieces that may hold a door, once per plan: at most one per pair of rooms that touch, on that
     * pair's longest piece that is at least {@link Look.Doors#hostBars} bar lengths at home and has the cells on the
     * side its door swings to ({@link NagayaDoors#side}) deep enough at every moment for the swing. It picks
     * {@link Look.Doors#chance} of all touching pairs (or every pair that can have one, if fewer), long pieces more
     * often than short ones: each pair's key is a hash over its piece's length squared, and the smallest keys go
     * first. A first pass in that order takes only pairs that give a room its first door, so almost every room gets
     * one; a second takes the rest. The motion keeps every host that long at every moment. {@link Frame#wallDoor}
     * carries the choice to the door placer.
     */
    static boolean[] doorHosts(TileGraph g, Look look) {
        boolean[] host = new boolean[g.pieces];
        int[] best = new int[g.pairs];
        Arrays.fill(best, -1);
        // a door swings across the cell beside it: the swing, its stroke and both walls, in steps
        Look.Doors d = look.doors;
        int swing = (int) Math.ceil((double) (d.radiusH + d.lineH + look.walls.widthH) / look.plan.unitH - 1e-3);
        for (int p = 0; p < g.pieces; p++) {
            int length = g.pieceTo[p] - g.pieceFrom[p], b = best[g.piecePair[p]];
            if (length < g.hostMin || (b >= 0 && length <= g.pieceTo[b] - g.pieceFrom[b])) continue;
            // every cell beside it on the side its door swings to stays that deep at every moment: a cell never gets
            // below its kept share of its width or height, nor below its rule's minimum (TileGraph.kept)
            boolean vertical = g.pieceVertical[p], roomy = true;
            int s = NagayaDoors.side(look.plan.seed, d.swingBias, p, vertical) > 0 ? g.pieceAt[p] : g.pieceAt[p] - 1;
            for (int a = g.pieceFrom[p]; a < g.pieceTo[p] && roomy; a++) {
                int c = (int) ((vertical ? g.cellAt(s, a) : g.cellAt(a, s)) >>> 32), r = g.cellRoom[c];
                boolean alone = g.roomCellStart[r + 1] - g.roomCellStart[r] == 1;
                int across = vertical ? g.cellX1[c] - g.cellX0[c] : g.cellY1[c] - g.cellY0[c];
                int least = vertical && !alone ? g.minCell : g.minCross;
                roomy = g.kept(across, least) >= swing;
            }
            if (roomy) best[g.piecePair[p]] = p;
        }
        int can = 0;
        Integer[] order = new Integer[g.pairs];
        double[] key = new double[g.pairs];
        for (int pair = 0; pair < g.pairs; pair++) {
            if (best[pair] < 0) continue;
            double length = g.pieceTo[best[pair]] - g.pieceFrom[best[pair]];
            key[pair] = MoveClock.unit(MoveClock.hash(look.plan.seed ^ HOSTS, pair, 0, 0)) / (length * length);
            order[can++] = pair;
        }
        Arrays.sort(order, 0, can, (a, b) -> Double.compare(key[a], key[b]));
        long wanted = Math.min(can, Math.round((double) look.doors.chance * g.pairs)), count = 0;
        int[] doors = new int[g.rooms];
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < can && count < wanted; i++) {
                int p = best[order[i]], a = g.pieceRoomA[p], b = g.pieceRoomB[p];
                if (host[p] || (pass == 0 && doors[a] > 0 && doors[b] > 0)) continue;
                host[p] = true;
                doors[a]++;
                doors[b]++;
                count++;
            }
        }
        return host;
    }

    @Override
    public int rooms() {
        return graph.rooms;
    }

    @Override
    public Frame newFrame() {
        // doors go only on host pieces
        return new Frame(cellCapacity, cellCapacity, wallCapacity, doorCapacity, 0, fillCapacity);
    }

    @Override
    public void layout(double t, View v, Frame f) {
        f.roomCount = 0;
        f.cellCount = 0;
        f.wallCount = 0;
        f.clipped = false;
        motion.offsets(t, offset);
        float unit = v.h * look.plan.unitH;
        float grow = v.h * (look.doors.radiusH + look.walls.widthH / 2);
        TileGraph g = graph;
        if (!(unit > 0 && v.zoom > 0 && v.w > 0)) return;
        reach(v, unit, grow);
        int jFrom = (int) Math.floor((reach[1] - reach[3] - maxY) / g.height);
        int jTo = (int) Math.ceil((reach[1] + reach[3] - minY) / g.height);
        if (!(Math.abs(reach[0]) < LATTICE_REACH && Math.abs(reach[1]) < LATTICE_REACH && jTo - jFrom < COPIES)) {
            f.clipped = true;
            return;
        }
        for (int j = jFrom; j <= jTo; j++) {
            int oy = j * g.height, sx = j * g.shift;
            int iFrom = (int) Math.floor((reach[0] - reach[2] - maxX - sx) / g.width);
            int iTo = (int) Math.ceil((reach[0] + reach[2] - minX - sx) / g.width);
            for (int i = iFrom; i <= iTo && i - iFrom < COPIES; i++) {
                int ox = i * g.width + sx;
                if (!v.sees((minX + ox) * unit, (minY + oy) * unit, (maxX + ox) * unit, (maxY + oy) * unit, grow)) continue;
                cells(f, v, ox, oy, unit, grow);
                walls(f, v, ox, oy, unit, grow);
            }
        }
    }

    /** The view's centre and how far it reaches along plan x and y, in steps, into {@link #reach}. */
    private void reach(View v, float unit, float grow) {
        double c = Math.abs(Math.cos(Math.toRadians(v.rollDeg))), s = Math.abs(Math.sin(Math.toRadians(v.rollDeg)));
        double ex = (v.w / 2.0 + v.margin) / v.zoom, ey = (v.h / 2.0 + v.margin) / v.zoom;
        reach[0] = v.centreX / unit;
        reach[1] = v.centreY / unit;
        reach[2] = (ex * c + ey * s + grow) / unit;
        reach[3] = (ex * s + ey * c + grow) / unit;
    }

    private void cells(Frame f, View v, int ox, int oy, float unit, float grow) {
        TileGraph g = graph;
        float[] o = offset;
        int count = 0;
        for (int k = 0; k < g.cells; k++) {
            float x0 = (g.cellX0[k] + ox + o[g.cellLeft[k]]) * unit, x1 = (g.cellX1[k] + ox + o[g.cellRight[k]]) * unit;
            float y0 = (g.cellY0[k] + oy + o[g.cellTop[k]]) * unit, y1 = (g.cellY1[k] + oy + o[g.cellBottom[k]]) * unit;
            if (!v.sees(x0, y0, x1, y1, grow)) continue;
            if (f.cellCount == f.cellX.length) {
                f.clipped = true;
                continue;
            }
            int id = g.cellRoom[k], r = roomIndex[id];
            if (r < 0) {
                if (f.roomCount == f.roomX.length) {
                    f.clipped = true;
                    continue;
                }
                r = f.roomCount++;
                roomIndex[id] = r;
                touched[count++] = id;
                room(f, r, id, ox, oy, unit);
            }
            int c = f.cellCount++;
            f.cellX[c] = x0;
            f.cellY[c] = y0;
            f.cellW[c] = x1 - x0;
            f.cellH[c] = y1 - y0;
            f.cellRoom[c] = r;
        }
        for (int i = 0; i < count; i++) roomIndex[touched[i]] = -1;
    }

    /** Room r of the frame: room id's box over all its cells in this copy, and its least size. */
    private void room(Frame f, int r, int id, int ox, int oy, float unit) {
        TileGraph g = graph;
        float[] o = offset;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int i = g.roomCellStart[id]; i < g.roomCellStart[id + 1]; i++) {
            int k = g.roomCellList[i];
            x0 = Math.min(x0, (g.cellX0[k] + ox + o[g.cellLeft[k]]) * unit);
            x1 = Math.max(x1, (g.cellX1[k] + ox + o[g.cellRight[k]]) * unit);
            y0 = Math.min(y0, (g.cellY0[k] + oy + o[g.cellTop[k]]) * unit);
            y1 = Math.max(y1, (g.cellY1[k] + oy + o[g.cellBottom[k]]) * unit);
        }
        f.roomId[r] = id;
        f.roomX[r] = x0;
        f.roomY[r] = y0;
        f.roomW[r] = x1 - x0;
        f.roomH[r] = y1 - y0;
        f.roomMinW[r] = g.roomMinW[id] < 0 ? Float.NaN : g.roomMinW[id] * unit;
        f.roomMinH[r] = g.roomMinH[id] < 0 ? Float.NaN : g.roomMinH[id] * unit;
    }

    private void walls(Frame f, View v, int ox, int oy, float unit, float grow) {
        TileGraph g = graph;
        float[] o = offset;
        for (int p = 0; p < g.pieces; p++) {
            boolean vertical = g.pieceVertical[p];
            int across = vertical ? ox : oy, along = vertical ? oy : ox;
            float at = (g.pieceAt[p] + across + o[g.pieceGroup[p]]) * unit;
            float a0 = (g.pieceFrom[p] + along + o[g.pieceFromGroup[p]]) * unit;
            float a1 = (g.pieceTo[p] + along + o[g.pieceToGroup[p]]) * unit;
            float x0 = vertical ? at : a0, y0 = vertical ? a0 : at, x1 = vertical ? at : a1, y1 = vertical ? a1 : at;
            if (!v.sees(x0, y0, x1, y1, grow)) continue;
            if (f.wallCount == f.wallX0.length) {
                f.clipped = true;
                continue;
            }
            int w = f.wallCount++;
            f.wallId[w] = p;
            f.wallRoomA[w] = g.pieceRoomA[p];
            f.wallRoomB[w] = g.pieceRoomB[p];
            f.wallX0[w] = x0;
            f.wallY0[w] = y0;
            f.wallX1[w] = x1;
            f.wallY1[w] = y1;
            f.wallVertical[w] = vertical;
            f.wallMinLength[w] = g.pieceMinLength[p] * unit;
            f.wallDoor[w] = g.pieceHost[p];
        }
    }

    /** The most cells, wall pieces, fill pieces and door hosts any of the widest views asks for, with every line anywhere. */
    private int[] widest() {
        Look.Camera cam = look.camera;
        float zoom = Math.min(cam.restZoom, Math.min(cam.zoomFrom, cam.zoomTo));
        float[] rolls = {cam.angleDeg, cam.rollFromDeg, cam.rollToDeg};
        float margin = Scene.margin(look), unit = SAMPLE_H * look.plan.unitH;
        TileGraph g = graph;
        View v = new View();
        int[] most = new int[4];
        for (float roll : rolls) {
            for (int i = 0; i < SAMPLES; i++) {
                for (int j = 0; j < SAMPLES; j++) {
                    double x = (g.width * i + g.shift * j) * unit / SAMPLES, y = g.height * j * unit / (double) SAMPLES;
                    v.set(SAMPLE_W, SAMPLE_H, roll, zoom, x, y, margin);
                    count(v, unit, most);
                }
            }
        }
        return most;
    }

    /** What view v can ask for with every line anywhere in its range, into most where it is more. */
    private void count(View v, float unit, int[] most) {
        TileGraph g = graph;
        float grow = v.h * (look.doors.radiusH + look.walls.widthH / 2), side = v.h * look.post.washPieceH;
        int r = g.maxUnits, cells = 0, walls = 0, fills = 0, hosts = 0;
        reach(v, unit, grow);
        int jFrom = (int) Math.floor((reach[1] - reach[3] - maxY) / g.height);
        int jTo = (int) Math.ceil((reach[1] + reach[3] - minY) / g.height);
        for (int j = jFrom; j <= jTo; j++) {
            int oy = j * g.height, sx = j * g.shift;
            int iFrom = (int) Math.floor((reach[0] - reach[2] - maxX - sx) / g.width);
            int iTo = (int) Math.ceil((reach[0] + reach[2] - minX - sx) / g.width);
            for (int i = iFrom; i <= iTo; i++) {
                int ox = i * g.width + sx;
                if (!v.sees((minX + ox) * unit, (minY + oy) * unit, (maxX + ox) * unit, (maxY + oy) * unit, grow)) continue;
                for (int k = 0; k < g.cells; k++) {
                    float x0 = (g.cellX0[k] - r + ox) * unit, x1 = (g.cellX1[k] + r + ox) * unit;
                    float y0 = (g.cellY0[k] - r + oy) * unit, y1 = (g.cellY1[k] + r + oy) * unit;
                    if (!v.sees(x0, y0, x1, y1, grow)) continue;
                    cells++;
                    fills += SceneMesh.pieces(x1 - x0, y1 - y0, side);
                }
                for (int p = 0; p < g.pieces; p++) {
                    boolean vertical = g.pieceVertical[p];
                    int across = g.pieceAt[p] + (vertical ? ox : oy), along = vertical ? oy : ox;
                    float a = (across - r) * unit, b = (across + r) * unit;
                    float c = (g.pieceFrom[p] + along - r) * unit, d = (g.pieceTo[p] + along + r) * unit;
                    if (!(vertical ? v.sees(a, c, b, d, grow) : v.sees(c, a, d, b, grow))) continue;
                    walls++;
                    if (g.pieceHost[p]) hosts++;
                }
            }
        }
        most[0] = Math.max(most[0], cells);
        most[1] = Math.max(most[1], walls);
        most[2] = Math.max(most[2], fills);
        most[3] = Math.max(most[3], hosts);
    }
}
