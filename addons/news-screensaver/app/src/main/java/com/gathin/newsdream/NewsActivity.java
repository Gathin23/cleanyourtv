package com.gathin.newsdream;

import android.app.Activity;
import android.view.KeyEvent;

/**
 * The module card on cleanyourtv's Home, and the settings' preview: the same scene as the screensaver, right
 * away. Left and Right page through the headlines; any other key goes back, to Home (or to the settings it was
 * opened from), as the screensaver wakes on any other key.
 */
public class NewsActivity extends Activity {

    private Scene scene;

    @Override
    protected void onStart() {
        super.onStart();
        // Built again on every start, so feeds changed in the settings show at once.
        scene = new Scene(this);
        setContentView(scene.root);
        scene.start();
    }

    @Override
    protected void onStop() {
        if (scene != null) scene.stop();
        scene = null;
        super.onStop();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && scene != null) {
                scene.step(key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) finish();
        return true;
    }
}
