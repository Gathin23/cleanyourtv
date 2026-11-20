package io.github.shiruiliu.floatingclock;

import android.service.dreams.DreamService;

/** The Android TV system screensaver: floating clock until any key wakes the TV. */
public class FloatingClockDream extends DreamService {

    private FloatingClockView clock;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        clock = new FloatingClockView(this);
        setContentView(clock);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        clock.start();
    }

    @Override
    public void onDreamingStopped() {
        clock.stop();
        super.onDreamingStopped();
    }
}
