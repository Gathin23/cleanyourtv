package io.github.nottoobright.koipond;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

/** The tile and the card: the pond full screen until Back; OK starts the breathing guide, arrows drop a pebble. */
public class PondActivity extends Activity {

    private PondView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new PondView(this);
        setContentView(view);
        hideSystemUi();
        view.setShowHint(true);

        // Debug and screenshot extras: am start ... --ei minute 1080 --ez rain true --ez breathe true
        Intent intent = getIntent();
        int minute = intent.getIntExtra("minute", -1);
        if (minute >= 0) view.setFixedMinute(minute);
        if (intent.getBooleanExtra("rain", false)) view.startRain();
        if (intent.getBooleanExtra("breathe", false)) view.toggleBreath();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        // The systemUiVisibility flags still work on API 26..34; WindowInsetsController needs API 30.
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.start();
    }

    @Override
    protected void onPause() {
        view.stop();
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                // A held OK would flip the guide on and off with every repeat.
                if (event.getRepeatCount() == 0) view.toggleBreath();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.getRepeatCount() == 0) view.pebble();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
