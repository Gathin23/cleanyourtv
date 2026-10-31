package com.gathin.hindipanchang;

import android.service.dreams.DreamService;

/**
 * Optional system screensaver showing the live Hindi Panchang (Vikram Samvat, Tithi, Nakshatra, and Muhurat).
 */
public final class PanchangDream extends DreamService {
    private PanchangPanel panel;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        panel = new PanchangPanel(this, true);
        setContentView(panel);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (panel != null) panel.start();
    }

    @Override
    public void onDreamingStopped() {
        if (panel != null) panel.stop();
        super.onDreamingStopped();
    }
}

