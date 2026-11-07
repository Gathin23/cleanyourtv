package io.github.smartgenius2.neondream;

import android.service.dreams.DreamService;

/** The screensaver. Scene, colours and clock come from {@link Prefs}, set in this app's own screen. */
public class NeonDream extends DreamService {

    private NeonView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        Prefs prefs = new Prefs(this);
        view = new NeonView(this, prefs.scene(), prefs.palette(), prefs.clock());
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (view != null) view.start();
    }

    @Override
    public void onDreamingStopped() {
        if (view != null) view.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (view != null) view.stop();
        super.onDetachedFromWindow();
    }
}
