package io.github.oussama22.dinorunner;

import android.service.dreams.DreamService;
import android.view.KeyEvent;
import android.webkit.WebView;

/**
 * The screensaver. A bot plays silently; OK/Up and Down take over with the game's sounds,
 * Left/Right hand it back, volume keys work, and any other key ends the screensaver.
 */
public class DinoDream extends DreamService {
    private WebView web;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);   // remote keys reach the game instead of ending the dream
        setFullscreen(true);
        setScreenBright(false); // dim the screen where the TV supports it
        web = DinoGame.create(this);
        setContentView(web);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (DinoGame.dispatch(web, e)) return true;
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(e);
        }
        // Finish on key down so the matching key up lands on nothing that acts on it.
        if (e.getAction() == KeyEvent.ACTION_DOWN) finish();
        return true;
    }

    @Override
    public void onDetachedFromWindow() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDetachedFromWindow();
    }
}
