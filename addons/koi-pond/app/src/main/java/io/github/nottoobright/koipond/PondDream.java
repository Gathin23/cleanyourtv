package io.github.nottoobright.koipond;

import android.service.dreams.DreamService;

/** The screensaver: the pond without hint or breathing guide, until any key wakes the TV. */
public class PondDream extends DreamService {

    private PondView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        view = new PondView(this);
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
