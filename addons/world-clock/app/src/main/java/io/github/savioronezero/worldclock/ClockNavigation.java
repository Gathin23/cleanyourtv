package io.github.savioronezero.worldclock;

/** Three toolbar buttons (0..2) and up to six clocks (3..). Every D-pad edge has an explicit target. */
final class ClockNavigation {
    static final int TOOLBAR = 3;
    static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;
    static int next(int node, int count, int direction) { return next(node, count, direction, false); }
    static int next(int node, int count, int direction, boolean rtl) {
        if (rtl && direction == LEFT) direction = RIGHT;
        else if (rtl && direction == RIGHT) direction = LEFT;
        if (count < 1 || count > ClockModel.LIMIT || node < 0 || node >= count + TOOLBAR)
            throw new IllegalArgumentException("invalid clock focus state");
        int columns = Math.min(3, count);
        if (node < TOOLBAR) {
            if (direction == LEFT) return Math.max(0, node - 1);
            if (direction == RIGHT) return Math.min(TOOLBAR - 1, node + 1);
            if (direction == DOWN) return TOOLBAR + Math.round(node * (columns - 1f) / (TOOLBAR - 1));
            return node;
        }
        int index = node - TOOLBAR, column = index % columns;
        if (direction == LEFT) return column == 0 ? node : node - 1;
        if (direction == RIGHT) return column == columns - 1 || index == count - 1 ? node : node + 1;
        if (direction == UP) return index < columns ? (columns == 1 ? 0 : Math.round(column * (TOOLBAR - 1f) / (columns - 1))) : node - columns;
        if (direction == DOWN && index + columns < count) return node + columns;
        if (direction == DOWN && index / columns == 0 && count > columns) return count + TOOLBAR - 1;
        return node;
    }
}
