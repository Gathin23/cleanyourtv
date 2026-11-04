package com.gathin.tvhome;

/**
 * Whether Home rebuilds its tiles when it comes back to the front. A rebuild asks PackageManager for every app again
 * and reloads every banner, icon and label (about 30 ms on a TCL C755, on every Home press), so it happens only when
 * an app was installed, removed, updated, enabled or disabled in the meantime. Otherwise Home only puts the focus
 * back, as before.
 */
final class TileRefresh {

    enum Action { NOTHING, REFOCUS, REBUILD }

    private boolean started;
    /** The tiles may not match the installed apps: true until the first build. */
    private boolean stale = true;
    private boolean refocus;

    void onStart() {
        started = true;
    }

    /** Another app took the screen: its tiles stay, and focus goes back to them on the way in. */
    void onStop() {
        started = false;
        refocus = true;
    }

    /** Home pressed while Home is in front: back to the first tile. */
    void onHomeAgain() {
        refocus = true;
    }

    /** The tiles were dropped (onTrimMemory): build them again on the way back. */
    void invalidate() {
        stale = true;
    }

    /**
     * A package broadcast. True when Home is on screen and should rebuild now; while it is in the background the
     * rebuild waits for {@link #onResume}, so a run of app updates costs one rebuild instead of one each.
     */
    boolean onPackagesChanged() {
        if (started) return true;
        stale = true;
        return false;
    }

    /** @param changedMeanwhile PackageManager reports changed packages since the last build (a missed broadcast). */
    Action onResume(boolean changedMeanwhile) {
        Action action = stale || changedMeanwhile ? Action.REBUILD : refocus ? Action.REFOCUS : Action.NOTHING;
        stale = false;
        refocus = false;
        return action;
    }
}
