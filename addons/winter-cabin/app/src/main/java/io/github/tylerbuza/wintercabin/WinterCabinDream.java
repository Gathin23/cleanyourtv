package io.github.tylerbuza.wintercabin;

import android.service.dreams.DreamService;
import com.gathin.winter.WinterCabinView;

/** Optional system screensaver; Android owns its activation and dismissal. */
public final class WinterCabinDream extends DreamService {
    private WinterCabinView scene;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        scene = new WinterCabinView(this);
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
        if (scene != null) { scene.stop(); scene = null; }
        super.onDetachedFromWindow();
    }
}
