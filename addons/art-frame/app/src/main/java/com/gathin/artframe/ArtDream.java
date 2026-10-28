package com.gathin.artframe;

import android.service.dreams.DreamService;

/** The TV's screensaver: the art button's choice, until any key wakes the TV back to where it was. */
public class ArtDream extends DreamService {
    private FrameView frame;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        Prefs prefs = new Prefs(this);
        // Outside art hours, or in the dimming hours, ask the TV for a dim screen as well as darkening the art.
        setScreenBright(prefs.isArtTime() && prefs.dimFactor() >= 1f);
        frame = new FrameView(this, true);
        setContentView(frame);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        Library lib = Library.get(this);
        if (lib.needsStarter()) {
            new Thread(() -> {
                lib.importStarter(getApplicationContext());
                if (frame != null) frame.post(() -> frame.setPlaylist(lib.defaultPlaylist(), ""));
            }, "starter").start();
        }
        frame.setPlaylist(lib.defaultPlaylist(), "");
        frame.start();
    }

    @Override
    public void onDreamingStopped() {
        if (frame != null) frame.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (frame != null) frame.release();
        frame = null;
        super.onDetachedFromWindow();
    }
}
