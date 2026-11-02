package io.github.raqs31.nowplaying;

import android.view.KeyEvent;

/**
 * Which remote keys the card catches: media keys only, only while it shows. A key's up goes wherever its down
 * went, so an app never sees half a press.
 */
final class KeyRules {
    static final int PASS = 0, SWALLOW = 1, TOGGLE = 2, PLAY = 3, PAUSE = 4, NEXT = 5, PREV = 6, STOP = 7;

    private int held;

    private static int action(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return TOGGLE;
            case KeyEvent.KEYCODE_MEDIA_PLAY: return PLAY;
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return PAUSE;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return NEXT;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND: return PREV;
            case KeyEvent.KEYCODE_MEDIA_STOP: return STOP;
            default: return PASS;
        }
    }

    int decide(int keyCode, int action, int repeatCount, boolean cardShowing) {
        int a = action(keyCode);
        if (a == PASS) return PASS;
        if (action == KeyEvent.ACTION_DOWN) {
            if (held == keyCode) return SWALLOW;
            if (!cardShowing || repeatCount > 0) return PASS;
            held = keyCode;
            return a;
        }
        if (held != keyCode) return PASS;
        held = 0;
        return SWALLOW;
    }

    /** True while a caught key is down, so filtering must stay on for its up. */
    boolean holding() {
        return held != 0;
    }
}
