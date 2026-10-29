package io.github.dennisoleary.digitalrain;

import android.service.dreams.DreamService;

/** The screensaver: digital rain until any key wakes the box back to where it was. */
public class RainDream extends DreamService {

    private RainView rain;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        rain = new RainView(this);
        setContentView(rain);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        rain.start();
    }

    @Override
    public void onDreamingStopped() {
        rain.stop();
        super.onDreamingStopped();
    }
}
