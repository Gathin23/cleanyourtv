package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The dock is the first row of the grid. dock_apps decides what is in it, or the saved order once the owner
 * changed the dock on the TV: a slot left free stays empty instead of taking the first grid app (#162) when
 * fill_dock is false; by default they fill.
 */
final class DockSlots {
    private DockSlots() { }

    /**
     * Empty slots to put after the {@code docked} dock apps found. None when the dock is full, when no
     * dock app is installed (the first row fills so Home still has a dock) or when nothing follows.
     */
    static int empty(int docked, int dockSize, int tiles, boolean fill) {
        if (fill || docked <= 0 || docked >= dockSize || tiles <= docked) return 0;
        return dockSize - docked;
    }

    /**
     * The order a reorder session saves when fill_dock is false (#499). Reorder mode shows no empty slots, so a
     * grid app slides into a free dock slot without being put there. Only the keys in {@code dock} (in the dock
     * when the session started, or carried into it) that end inside the first {@code dockSize} places stay in the
     * dock: they come first, in their order, and every other key follows in its order. The dock size to save is
     * the number of keys in {@code dock} found there.
     */
    static List<String> dockFirst(List<String> keys, int dockSize, Set<String> dock) {
        List<String> head = new ArrayList<>(), tail = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            (i < dockSize && dock.contains(k) ? head : tail).add(k);
        }
        head.addAll(tail);
        return head;
    }

    /** How many of the first {@code dockSize} keys are in {@code dock}: the dock size dockFirst puts first. */
    static int dockCount(List<String> keys, int dockSize, Set<String> dock) {
        int n = 0;
        for (int i = 0; i < Math.min(dockSize, keys.size()); i++) if (dock.contains(keys.get(i))) n++;
        return n;
    }

    /** Whether a tile sits in the dock row: every tile of a single row, else the first `dockSize` of the grid. */
    static boolean inDockRow(boolean dockRow, boolean singleRow, int position, int dockSize) {
        return dockRow && (singleRow || position < dockSize);
    }

    /** A position focus can land on: an empty slot sends it to the last dock app. */
    static int focusable(int position, int docked, int empty) {
        return empty > 0 && position >= docked && position < docked + empty ? docked - 1 : position;
    }
}
