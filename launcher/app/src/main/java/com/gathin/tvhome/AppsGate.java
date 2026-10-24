package com.gathin.tvhome;

import android.view.KeyEvent;

/**
 * hide_apps_until_down: Home shows no apps (a photo frame) until Down or OK slides the dock and the grid up. This is
 * the key logic only, so it can be tested without a TV; MainActivity does the views.
 *
 * - Hidden, focus on the invisible gate: Down or OK reveals the apps, and the release of that key is swallowed so it
 *   does not open the dock app it lands on. Up goes to the top bar, Left and Right do nothing, Back does nothing.
 * - Hidden, focus in the top bar: Down reveals the apps, Back hides the bar's focus again, the rest is the bar's.
 * - Shown: Back hides the apps, and so does every return to Home from an app (MainActivity calls hide()).
 * - Home pressed on Home toggles: hidden apps come up, shown ones go away (onHomeAgain).
 */
final class AppsGate {

    enum Action { PASS, CONSUME, REVEAL, FOCUS_TOP, HIDE }

    private boolean hidden;
    /** Keycode whose release (and repeats) is swallowed after it revealed the apps; 0 for none. */
    private int swallow;

    AppsGate(boolean hidden) {
        this.hidden = hidden;
    }

    boolean hidden() {
        return hidden;
    }

    void hide() {
        hidden = true;
        swallow = 0;
    }

    void show() {
        hidden = false;
    }

    /**
     * Home pressed while Home is already in front: it toggles, so the key never seems dead on a photo frame whose apps
     * are already away. (Coming back to Home from an app hides the apps instead; MainActivity calls hide() there.)
     */
    Action onHomeAgain() {
        if (hidden) {
            hidden = false;
            swallow = 0;
            return Action.REVEAL;
        }
        hide();
        return Action.HIDE;
    }

    /** What a key does; {@code onGate} is true while the invisible gate view holds the focus. */
    Action onKey(int keyCode, int action, boolean onGate) {
        boolean down = action == KeyEvent.ACTION_DOWN;
        if (swallow != 0 && keyCode == swallow) {
            if (!down) swallow = 0;
            return Action.CONSUME;
        }
        boolean back = keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE;
        if (!hidden) {
            if (!back) return Action.PASS;
            return down ? Action.CONSUME : Action.HIDE;
        }
        boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || ok && onGate) {
            if (!down) return Action.CONSUME;
            hidden = false;
            swallow = keyCode;
            return Action.REVEAL;
        }
        if (back) return down || onGate ? Action.CONSUME : Action.HIDE;
        if (!onGate) return Action.PASS;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return down ? Action.FOCUS_TOP : Action.CONSUME;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return Action.CONSUME;
        return Action.PASS;
    }
}
