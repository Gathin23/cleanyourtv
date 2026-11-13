package io.github.yourvoldemort.panchang;

import android.content.SharedPreferences;
import android.service.dreams.DreamService;

/** Optional system screensaver. Android starts it only when the owner selects this dream. */
public final class PanchangDream extends DreamService {
    private PanchangPanel panel;
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        panel = new PanchangPanel(this, true);
        SharedPreferences prefs = getSharedPreferences(PanchangActivity.PREFS, MODE_PRIVATE);
        panel.setCity(City.cityNamed(prefs.getString(PanchangActivity.KEY_CITY, City.forTimeZone().name)));
        panel.setRegion(Region.named(prefs.getString(PanchangActivity.KEY_REGION, Region.forLocale().name())));
        setContentView(panel);
    }
    @Override public void onDreamingStarted() { super.onDreamingStarted(); panel.start(); }
    @Override public void onDreamingStopped() { if (panel != null) panel.stop(); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() { if (panel != null) panel.stop(); super.onDetachedFromWindow(); }
}
