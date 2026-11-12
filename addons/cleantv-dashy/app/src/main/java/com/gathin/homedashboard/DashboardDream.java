package com.gathin.homedashboard;
import android.service.dreams.DreamService;

public class DashboardDream extends DreamService {
    private Scene scene;
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow(); setInteractive(false); setFullscreen(true); setScreenBright(false);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        scene = new Scene(this, false); setContentView(scene.root);
    }
    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (android.os.Build.VERSION.SDK_INT >= 31) getWindow().getDecorView().post(() -> {
            getWindow().setHideOverlayWindows(true);
            // DreamService's attribute callback is a no-op, so push the window update explicitly.
            getWindow().getWindowManager().updateViewLayout(getWindow().getDecorView(), getWindow().getAttributes());
        });
        if (scene != null) scene.start();
    }
    @Override public void onDreamingStopped() { if (scene != null) scene.stop(); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() { if (scene != null) scene.stop(); super.onDetachedFromWindow(); }
}
