package io.github.pbanddev.tatami;

/**
 * One tile of a TILE plan at rest, in whole steps ({@link Look.Plan#unitH}), as a {@link TileBuilder} makes it.
 * This is the contract a builder writes to. {@link TileGraph} checks all of it when the plan is built and throws
 * {@link IllegalArgumentException} on the first rule that fails (the app then shows Yojōhan instead).
 *
 * <ul>
 *   <li><b>Lattice.</b> Copies of the tile sit at {@code i * (width, 0) + j * (shift, height)}, with
 *       {@code shift = width / 2}: each row of tiles is shifted half a tile.</li>
 *   <li><b>Cells</b> are boxes {@code [cellX0, cellX1) x [cellY0, cellY1)} with whole-step corners. Their copies
 *       fill the plane exactly, with no gaps and no overlaps. A cell may reach past the tile's own edges: a row's
 *       last room may end past {@code x = width}, and a cell may hang below {@code y = height}; its copies then
 *       cover the matching part of the neighbouring tiles.</li>
 *   <li><b>Rooms</b> are numbered 0 to {@code rooms - 1}; {@code cellRoom} gives each cell's room. A room is 1 to 3
 *       cells that join along edges, as given (not through a copy): a box, an L, a T, a Z, a U and so on. Cut a
 *       room into cells with vertical cuts at its steps, so each cell is as tall as the room is at that place.</li>
 *   <li><b>Lines.</b> The core finds them: every maximal straight run of cell edges is one line, and it moves as a
 *       whole. Joints of 2 courses that meet exactly at the row line between them form one line (a through-joint)
 *       and stay in line for good. Rooms keep their neighbours for good: nothing a builder makes ever changes
 *       which rooms touch.</li>
 *   <li><b>Rules at rest</b>, in steps: every cell at least {@code minCross} tall and {@code minCell} wide
 *       ({@link Look.Plan#minCrossH} over {@code unitH}, rounded up, and {@link Look.Plan#minCellUnits}); a room of
 *       one cell at least {@code minCross} wide, and a room of more cells at least {@code minCross} from its
 *       leftmost edge to its rightmost. Where 2 lines end on a third from its 2 sides (a step in a row edge, or
 *       joints of 2 courses on the row line between them), they are either one line or at least
 *       {@link Look.Plan#minStepUnits} apart.</li>
 * </ul>
 *
 * The core keeps every rule true at every moment while the lines move, and picks the door hosts itself.
 */
final class TileLayout {
    final int width, height, shift;
    final int rooms;
    final int[] cellX0, cellY0, cellX1, cellY1, cellRoom;

    TileLayout(int width, int height, int rooms, int[] cellX0, int[] cellY0, int[] cellX1, int[] cellY1, int[] cellRoom) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("tile " + width + " x " + height);
        int n = cellRoom.length;
        if (cellX0.length != n || cellY0.length != n || cellX1.length != n || cellY1.length != n) {
            throw new IllegalArgumentException("cell arrays differ in length");
        }
        for (int c = 0; c < n; c++) {
            if (cellX1[c] <= cellX0[c] || cellY1[c] <= cellY0[c]) throw new IllegalArgumentException("empty cell " + c);
            if (cellRoom[c] < 0 || cellRoom[c] >= rooms) throw new IllegalArgumentException("cell " + c + " has no room");
        }
        this.width = width;
        this.height = height;
        this.shift = width / 2;
        this.rooms = rooms;
        this.cellX0 = cellX0;
        this.cellY0 = cellY0;
        this.cellX1 = cellX1;
        this.cellY1 = cellY1;
        this.cellRoom = cellRoom;
    }

    int cells() {
        return cellRoom.length;
    }
}
