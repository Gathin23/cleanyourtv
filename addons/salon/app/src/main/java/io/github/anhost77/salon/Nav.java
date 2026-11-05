package io.github.anhost77.salon;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

/** Shared D-pad rules: the remote follows the screen and never jumps to a far corner. */
final class Nav {
    private Nav() { }

    /**
     * Right on the last button of a row (or Left on the first) has nowhere to go: true swallows the key, so Android's
     * focus finder does not jump to the day tabs or a chip at the other end of the screen.
     */
    static boolean lastInRow(View v, int code) {
        if (code != KeyEvent.KEYCODE_DPAD_RIGHT && code != KeyEvent.KEYCODE_DPAD_LEFT) return false;
        if (!(v.getParent() instanceof ViewGroup)) return false;
        ViewGroup row = (ViewGroup) v.getParent();
        boolean right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        int i = row.indexOfChild(v);
        for (int j = right ? i + 1 : i - 1; j >= 0 && j < row.getChildCount(); j += right ? 1 : -1) {
            View next = row.getChildAt(j);
            if (next.isShown() && next.isFocusable()) return false;
        }
        return true;
    }
}
