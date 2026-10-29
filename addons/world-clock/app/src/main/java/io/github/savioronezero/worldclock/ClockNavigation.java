package io.github.savioronezero.worldclock;

/** Two toolbar buttons (0/1) and up to six clocks (2..). Every D-pad edge has an explicit target. */
final class ClockNavigation {
    static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;
    static int next(int node, int count, int direction) { return next(node, count, direction, false); }
    static int next(int node, int count, int direction, boolean rtl) {
        if (rtl && direction == LEFT) direction = RIGHT;
        else if (rtl && direction == RIGHT) direction = LEFT;
        if (count < 1 || count > ClockModel.LIMIT || node < 0 || node >= count + 2)
            throw new IllegalArgumentException("invalid clock focus state");
        int columns = Math.min(3, count);
        if (node < 2) {
            if (direction == LEFT) return 0;
            if (direction == RIGHT) return 1;
            if (direction == DOWN) return 2 + (node == 0 ? 0 : columns - 1);
            return node;
        }
        int index = node - 2, column = index % columns;
        if (direction == LEFT) return column == 0 ? node : node - 1;
        if (direction == RIGHT) return column == columns - 1 || index == count - 1 ? node : node + 1;
        if (direction == UP) return index < columns ? (column == 0 ? 0 : 1) : node - columns;
        if (direction == DOWN && index + columns < count) return node + columns;
        if (direction == DOWN && index / columns == 0 && count > columns) return count + 1;
        return node;
    }
}
