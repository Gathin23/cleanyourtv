package io.github.pbanddev.tatami;

/**
 * One drawn moment of the floor plan, in plan units (screen pixels at zoom 1, before the tilt; y points
 * down). Allocated once and refilled every frame. The contract between the stages of {@link Scene}:
 *
 * <ul>
 *   <li><b>Rooms</b> (filled by the {@link Layout}): a room's index holds for one frame; {@code roomId} holds for
 *       good and picks its paint. {@code roomX, roomY, roomW, roomH} box the room's cells. {@code roomMinW} and
 *       {@code roomMinH} are the smallest the room ever gets, at any time; NaN for a room of more than one cell.
 *       {@code roomColor} is an index into {@link Look.Palette#deck} (set by {@link Scene}). In Yojōhan the
 *       index is the id, and each room is one box.</li>
 *   <li><b>Cells</b> (filled by the {@link Layout}): boxes that fill the plan without gaps or overlaps; each
 *       belongs to the room at index {@code cellRoom}. A room is 1 or more cells. Yojōhan copies each room to one
 *       cell.</li>
 *   <li><b>Walls</b> (filled by the {@link Layout}): the wall pieces between rooms, on the wall's centre line,
 *       with {@code x0 <= x1} and {@code y0 <= y1}. {@code wallId} holds for good; {@code wallRoomA} and
 *       {@code wallRoomB} are the room ids on its 2 sides and {@code wallMinLength} the shortest the piece ever
 *       gets (-1 and NaN in Yojōhan, where the index is the id: there is always one wall fewer than rooms, a cut
 *       adds 1 room and 1 wall and a windmill 4 and 4). The plan's outer edge is not a wall.</li>
 *   <li><b>Doors</b> (filled by the {@link DoorPlacer}): each hinges at {@code doorX, doorY} on the centre line of
 *       the wall at index {@code doorWall}. {@code doorAlong} is +1 when the bar runs from the hinge toward +x
 *       (or +y on a vertical wall), -1 the other way. {@code doorSide} is +1 when the leaf swings toward +y (or +x
 *       on a vertical wall), -1 the other way. {@code doorOpening} is in radians: 0 shut, pi/2 open.</li>
 *   <li><b>Fixtures</b> (filled by {@link Fixtures}): a kind in room {@code fixtureRoom}, turned
 *       {@code fixtureTurn} quarter turns (0 to 3), filling the box {@code x, y, w, h} after the turn.</li>
 * </ul>
 *
 * A layout that has more to show than the arrays hold leaves the rest out and sets {@link #clipped}.
 */
public final class Frame {
    public final float[] roomX, roomY, roomW, roomH, roomMinW, roomMinH;
    public final int[] roomColor, roomId;

    public final float[] cellX, cellY, cellW, cellH;
    public final int[] cellRoom;

    public final float[] wallX0, wallY0, wallX1, wallY1, wallMinLength;
    public final boolean[] wallVertical;
    /** True for a wall piece that may hold a door: the plan keeps it long enough at every moment. Never set in Yojōhan. */
    public final boolean[] wallDoor;
    public final int[] wallId, wallRoomA, wallRoomB;

    public final int[] doorWall, doorAlong, doorSide;
    public final float[] doorX, doorY;
    public final double[] doorOpening;

    public final int[] fixtureKind, fixtureRoom, fixtureTurn;
    public final float[] fixtureX, fixtureY, fixtureW, fixtureH;

    /** The most fill pieces {@link SceneMesh} may cut this frame's cells into, for the wash. */
    public final int fillCapacity;

    public int roomCount, cellCount, wallCount, doorCount, fixtureCount;
    /** True when the last layout had more than these arrays hold. */
    public boolean clipped;

    /** Yojōhan's: one cell per room, one wall fewer than rooms, a door per wall at most, a fixture per room. */
    public Frame(int rooms) {
        this(rooms, rooms, Math.max(0, rooms - 1), Math.max(0, rooms - 1), rooms, rooms);
    }

    public Frame(int rooms, int cells, int walls, int doors, int fixtures, int fills) {
        roomX = new float[rooms];
        roomY = new float[rooms];
        roomW = new float[rooms];
        roomH = new float[rooms];
        roomMinW = new float[rooms];
        roomMinH = new float[rooms];
        roomColor = new int[rooms];
        roomId = new int[rooms];
        cellX = new float[cells];
        cellY = new float[cells];
        cellW = new float[cells];
        cellH = new float[cells];
        cellRoom = new int[cells];
        wallX0 = new float[walls];
        wallY0 = new float[walls];
        wallX1 = new float[walls];
        wallY1 = new float[walls];
        wallMinLength = new float[walls];
        wallVertical = new boolean[walls];
        wallDoor = new boolean[walls];
        wallId = new int[walls];
        wallRoomA = new int[walls];
        wallRoomB = new int[walls];
        doorWall = new int[doors];
        doorAlong = new int[doors];
        doorSide = new int[doors];
        doorX = new float[doors];
        doorY = new float[doors];
        doorOpening = new double[doors];
        fixtureKind = new int[fixtures];
        fixtureRoom = new int[fixtures];
        fixtureTurn = new int[fixtures];
        fixtureX = new float[fixtures];
        fixtureY = new float[fixtures];
        fixtureW = new float[fixtures];
        fixtureH = new float[fixtures];
        fillCapacity = fills;
    }
}
