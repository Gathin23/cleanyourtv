package io.github.pbanddev.tatami;

import android.service.dreams.DreamService;

/** The TV screensaver: the floor plan, full screen. Any key wakes the TV back to where it was. */
public final class FloorPlanDream extends DreamService {
    private FloorPlanView plan;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        plan = new FloorPlanView(this);
        setContentView(plan);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        plan.setEffects(Effects.load(this));
        plan.onResume();
    }

    @Override
    public void onDreamingStopped() {
        plan.onPause();
        super.onDreamingStopped();
    }
}
