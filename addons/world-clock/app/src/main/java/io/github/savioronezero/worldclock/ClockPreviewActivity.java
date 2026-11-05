package io.github.savioronezero.worldclock;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Preview shares the dream renderer but never changes the selected system screensaver. */
public final class ClockPreviewActivity extends Activity {
    private ClockScene scene;
    private final PreviewDismissal dismissal = new PreviewDismissal();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        scene = new ClockScene(this);
        setContentView(scene);
    }
    @Override protected void onResume() { super.onResume(); scene.start(); }
    @Override protected void onPause() { scene.stop(); super.onPause(); finish(); }
    @Override protected void onDestroy() { scene.stop(); super.onDestroy(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // Consume the whole press, so the release cannot also activate a control behind the preview.
        if (dismissal.key(event.getKeyCode(), event.getAction(), event.isCanceled())) finish();
        return true;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) finish();
        return true;
    }
}
