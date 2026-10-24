package com.gathin.tvhome;

/**
 * Where the first-tile focus from a Home press has to wait. When Home is stopped, or its window
 * has no focus yet, a focus request posted from onNewIntent (or from onResume's refocus) can run
 * before the window regains focus, and the view that was focused before, such as the Inputs pill
 * after Inputs opened an external source picker (#1204), then wins focus back. So in those cases
 * the first-tile focus waits until the window actually gains focus, instead of being posted early.
 */
final class HomeFocus {

    private boolean pending;

    /**
     * A Home press arrived. Returns true when the first tile can be focused right away, which is
     * only when Home is on screen and its window already has focus. Otherwise the focus is pending
     * until the window gains focus.
     */
    boolean onHomeIntent(boolean stopped, boolean hasWindowFocus) {
        if (stopped || !hasWindowFocus) {
            pending = true;
            return false;
        }
        return true;
    }

    /** Returns true once, when the window gains focus with a Home focus pending. */
    boolean onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus || !pending) return false;
        pending = false;
        return true;
    }

    /** A lost request must not pop the focus to the first tile on some later window focus. */
    void onPause() {
        pending = false;
    }
}
