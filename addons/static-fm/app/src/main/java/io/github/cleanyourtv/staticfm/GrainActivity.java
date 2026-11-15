package io.github.cleanyourtv.staticfm;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * The tile and the card: full screen until Back. Left and Right change the style, Up and Down the
 * inks, OK how strongly it follows the music. The first open asks once for the permission the sound needs.
 */
public class GrainActivity extends Activity {
    private GrainView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new GrainView(this);
        setContentView(view);
        hideSystemUi();
        view.showLabel();
        int style = getIntent().getIntExtra("style", -1);
        if (style >= 0) view.preview(style);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                && state == null) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) view.reopenSound();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
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
        if (event.getRepeatCount() > 0) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: view.nextStyle(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: view.nextStyle(-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: view.nextPalette(1); return true;
            case KeyEvent.KEYCODE_DPAD_UP: view.nextPalette(-1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A: view.nextReaction(); return true;
            default: return super.onKeyDown(keyCode, event);
        }
    }
}
