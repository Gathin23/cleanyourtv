package io.github.oussama22.dinorunner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.KeyEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** The game page and the remote keys it takes, shared by the screensaver and the card's screen. */
final class DinoGame {
    private DinoGame() {}

    @SuppressLint("SetJavaScriptEnabled")
    static WebView create(Context context) {
        WebView web = new WebView(context);
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        // Remote keys reach the page through evaluateJavascript, which is not a user gesture, so
        // the game's AudioContext would otherwise stay suspended when the viewer takes over.
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);   // file:///android_asset stays readable
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        web.loadUrl("file:///android_asset/dino/index.html");
        return web;
    }

    /** OK/Up jump, Down ducks, Left/Right hand the game back to the bot; null for any other key. */
    static String gameKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_SPACE:
                return "jump";
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return "duck";
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return "auto";
            default:
                return null;
        }
    }

    /** Sends a game key to the page; true when the event was one. */
    static boolean dispatch(WebView web, KeyEvent e) {
        String key = gameKey(e.getKeyCode());
        if (key == null) return false;
        int action = e.getAction();
        if (web != null && e.getRepeatCount() == 0
                && (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP)) {
            boolean down = action == KeyEvent.ACTION_DOWN;
            web.evaluateJavascript("window.dinoKey&&dinoKey('" + key + "'," + down + ")", null);
        }
        return true;
    }
}
