package com.oneyuv.tv.nepalicalendar;

import android.service.dreams.DreamService;

/** Optional system screensaver. Android starts it only when the owner selects this dream. */
public final class CalendarDream extends DreamService {
    private CalendarPanel panel;
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        panel = new CalendarPanel(this, true);
        setContentView(panel);
    }
    @Override public void onDreamingStarted() { super.onDreamingStarted(); panel.start(); }
    @Override public void onDreamingStopped() { if (panel != null) panel.stop(); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() { if (panel != null) panel.stop(); super.onDetachedFromWindow(); }
}
