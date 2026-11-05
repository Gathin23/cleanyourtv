package io.github.shivamsingh262.photoprints;

import android.service.dreams.DreamService;

/** The screensaver: Prints, until any key wakes the TV back to where it was. */
public class PrintsDream extends DreamService {

    private Prints prints;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.prints);
        prints = new Prints(this, getWindow().getDecorView());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        prints.start();
    }

    @Override
    public void onDreamingStopped() {
        prints.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (prints != null) prints.release();
        super.onDetachedFromWindow();
    }
}
