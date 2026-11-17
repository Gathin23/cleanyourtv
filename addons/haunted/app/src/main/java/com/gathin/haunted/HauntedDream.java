package com.gathin.haunted;

import android.service.dreams.DreamService;

/** The screensaver: night photos, bats, a ghost and a clock until any key wakes the box. */
public class HauntedDream extends DreamService {

    private HauntedView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        view = new HauntedView(this);
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
