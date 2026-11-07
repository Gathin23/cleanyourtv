package com.gathin.tvhome;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
final class GridNavigation {
    private GridNavigation() { }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }

    /**
     * center_last_row: how far the last row's tiles move towards the middle, in half columns (6 columns with 4
     * apps: 2, one column each side). 0 for a full row, and for a last row that is the dock row.
     */
    static int lastRowHalfShift(int count, int columns, boolean dockRow) {
        if (count <= 0 || columns <= 1) return 0;
        int start = lastRowStart(count, columns);
        if (dockRow && start < columns) return 0;
        return columns - (count - start);
    }

    static int lastRowStart(int count, int columns) {
        return ((count - 1) / columns) * columns;
    }

    /** The last-row tile under column {@code column} of the row above, the nearest end tile past either end. */
    static int intoLastRow(int column, int count, int columns, int halfShift) {
        int start = lastRowStart(count, columns);
        // The inverse of columnAbove, so Up then Down comes back to the same tile.
        int index = Math.floorDiv(2 * column - halfShift + 1, 2);
        return start + Math.max(0, Math.min(count - start - 1, index));
    }

    /** The column a shifted last-row tile sits under in the row above (the left one when it straddles two). */
    static int columnAbove(int position, int count, int columns, int halfShift) {
        return Math.min(columns - 1, (2 * (position - lastRowStart(count, columns)) + halfShift) / 2);
    }
}
