package io.github.cleanyourtv.staticfm;

import android.service.dreams.DreamService;

/** The screensaver: the chosen style, with no label, until any key wakes the TV. */
public class GrainDream extends DreamService {
    private GrainView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        view = new GrainView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        view.start();
    }

    @Override
    public void onDreamingStopped() {
        view.stop();
        super.onDreamingStopped();
    }
}
