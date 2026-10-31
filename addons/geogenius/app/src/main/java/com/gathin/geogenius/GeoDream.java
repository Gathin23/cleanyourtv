package com.gathin.geogenius;

import android.service.dreams.DreamService;

public final class GeoDream extends DreamService {

    private GeoPanel panel;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        panel = new GeoPanel(this, true);
        setContentView(panel);
    }

    @Override
    public void onDetachedFromWindow() {
        if (panel != null) {
            panel.releaseResources();
        }
        super.onDetachedFromWindow();
    }
}

