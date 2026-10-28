package io.github.cleanyourtv.flock;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

/** Fullscreen TV entry point. All controls work with a standard D-pad remote. */
public final class FlockActivity extends Activity {
    private FlockView flockView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        flockView = new FlockView(this);
        setContentView(flockView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (flockView != null) flockView.resume();
    }

    @Override
    protected void onPause() {
        if (flockView != null) flockView.pause();
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() == 0) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                flockView.togglePaused();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                flockView.changeCount(12);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                flockView.changeCount(-12);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
