package com.gathin.newsdream;

import android.service.dreams.DreamService;
import android.view.KeyEvent;

/**
 * The system screensaver: the Scene. Left and Right page through the headlines; any other key wakes the TV back
 * to where it was, as a screensaver that takes no keys would.
 */
public class NewsDream extends DreamService {

    private Scene scene;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Interactive so Left and Right reach dispatchKeyEvent; everything else ends the dream there.
        setInteractive(true);
        setFullscreen(true);
        scene = new Scene(this);
        setContentView(scene.root);
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
        // The waking key is used up here, so it doesn't also act on the app below.
        if (event.getAction() == KeyEvent.ACTION_DOWN) finish();
        return true;
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (scene != null) scene.start();
    }

    @Override
    public void onDreamingStopped() {
        if (scene != null) scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (scene != null) scene.stop();
        super.onDetachedFromWindow();
    }
}
