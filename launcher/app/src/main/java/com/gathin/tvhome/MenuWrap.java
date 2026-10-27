package com.gathin.tvhome;

import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.LinearLayout;

/** menu_wrap: Up on a menu's first row goes to its last, and Down on the last back to the first. */
final class MenuWrap {
    private MenuWrap() {
    }

    /** Joins the first and last rows shown; call again when rows are shown or hidden (More inputs). */
    static void apply(LinearLayout rows) {
        View first = null, last = null;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            // Clear an earlier join: a row that was the first or last may now have rows past it.
            row.setNextFocusUpId(View.NO_ID);
            row.setNextFocusDownId(View.NO_ID);
            if (!row.isFocusable() || row.getVisibility() != View.VISIBLE) continue;
            if (first == null) first = row;
            last = row;
        }
        if (first == null || first == last) return;
        if (first.getId() == View.NO_ID) first.setId(View.generateViewId());
        if (last.getId() == View.NO_ID) last.setId(View.generateViewId());
        first.setNextFocusUpId(last.getId());
        last.setNextFocusDownId(first.getId());
    }

    /** The click a D-pad move makes, for a move made in code rather than by the focus search. */
    static void click(View view, int keyCode) {
        view.playSoundEffect(keyCode == KeyEvent.KEYCODE_DPAD_UP ? SoundEffectConstants.NAVIGATION_UP
                : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? SoundEffectConstants.NAVIGATION_DOWN
                : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? SoundEffectConstants.NAVIGATION_LEFT
                : SoundEffectConstants.NAVIGATION_RIGHT);
    }
}
