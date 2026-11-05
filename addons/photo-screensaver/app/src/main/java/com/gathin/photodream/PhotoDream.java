package com.gathin.photodream;

import android.service.dreams.DreamService;

/** The system screensaver: the Scene, until any key wakes the TV back to where it was. */
public class PhotoDream extends DreamService {

    private Scene scene;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        scene = new Scene(this);
        setContentView(scene.root);
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
