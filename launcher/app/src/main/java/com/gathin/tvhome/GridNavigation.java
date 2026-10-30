package com.gathin.tvhome;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
final class GridNavigation {
    /** Directions for {@link #repeatTarget}, kept free of Android key codes so the helper stays unit-testable. */
    static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;

    private GridNavigation() { }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }

    /**
     * Position focus should move to when a direction key is held: one tile along the row for
     * Left/Right and one row for Up/Down. Returns the same position when there is no further
     * tile in that direction (row start/end, the dock row for Up, the last row for Down), so
     * the caller stops moving but keeps swallowing the held key's repeats.
     *
     * @param dockCount dock apps shown: the dock row ends after them, since empty slots take no focus
     */
    static int repeatTarget(int position, int direction, int count, int columns, int dockCount, boolean rtl) {
        if (count <= 0 || position < 0 || position >= count || columns <= 0) return position;
        switch (direction) {
            case LEFT:
            case RIGHT: {
                boolean forward = (direction == RIGHT) != rtl;
                boolean inDock = position < columns;
                if (forward) {
                    int rowEnd = inDock
                            ? Math.min(columns, Math.min(count, dockCount)) - 1
                            : Math.min((position / columns) * columns + columns - 1, count - 1);
                    return position >= rowEnd ? position : position + 1;
                }
                int rowStart = inDock ? 0 : (position / columns) * columns;
                return position <= rowStart ? position : position - 1;
            }
            case UP:
                // The dock row is the top: Up from it leaves for the top bar.
                return position < columns ? position : position - columns;
            case DOWN: {
                int lastRowStart = ((count - 1) / columns) * columns;
                if (position >= lastRowStart) return position;
                return Math.min(position + columns, count - 1);
            }
            default:
                return position;
        }
    }
}
