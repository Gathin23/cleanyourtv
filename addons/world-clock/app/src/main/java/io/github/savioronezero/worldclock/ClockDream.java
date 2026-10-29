package io.github.savioronezero.worldclock;

import android.service.dreams.DreamService;

/** Android owns activation/timeout/input dismissal; no boot receiver or always-running worker. */
public final class ClockDream extends DreamService {
    private ClockScene scene;
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        scene = new ClockScene(this);
        setContentView(scene);
    }
    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (scene != null) scene.start();
    }
    @Override public void onDreamingStopped() {
        if (scene != null) scene.stop();
        super.onDreamingStopped();
    }
    @Override public void onDetachedFromWindow() {
        if (scene != null) scene.stop();
        scene = null;
        super.onDetachedFromWindow();
    }
    @Override public void onDestroy() {
        if (scene != null) scene.stop();
        super.onDestroy();
    }
}
