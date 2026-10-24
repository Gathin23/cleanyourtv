package io.github.luyzfernando08.sleeptimer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A small bar at the bottom of the screen, for a remote button mapped to it (the launcher's app_keys,
 * "hold:<keycode>:io.github.luyzfernando08.sleeptimer/.QuickActivity"): Left/Right pick the timer (off, then each
 * length) and it applies at once, over whatever is playing; OK or Back, or a few seconds untouched, closes it. The
 * full picker stays on the app's tile and its module card. It is drawn like one of the
 * launcher's glass panels, so it reads as part of Home.
 */
public class QuickActivity extends Activity {
    private static final long CLOSE_AFTER_MS = 4000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable close = this::finish;
    private TextView label;
    /** 0 is off; otherwise the index into Timer.MINUTES plus one. */
    private int choice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padX = Ui.dp(this, 28), padY = Ui.dp(this, 18);
        panel.setPadding(padX, padY, padX, padY);
        panel.setBackground(getDrawable(R.drawable.glass_panel));

        label = Ui.text(this, 20, Color.WHITE);
        panel.addView(label);
        TextView hint = Ui.text(this, 13, 0xB3FFFFFF);
        hint.setText(R.string.quick_hint);
        hint.setPadding(0, Ui.dp(this, 8), 0, 0);
        panel.addView(hint);

        FrameLayout root = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.dp(this, 56);
        root.addView(panel, lp);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        choice = currentChoice();
        show();
        closeLater();
    }

    /** The button held again while the bar is up (singleInstance): keep it up a little longer. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        closeLater();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(close);
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                step(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                step(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                finish();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    /** The choice that matches a running timer: the first length that still covers what is left, so Right extends. */
    private int currentChoice() {
        long end = Timer.end(this);
        if (end == 0) return 0;
        long leftMinutes = (end - System.currentTimeMillis() + 59_999) / 60_000;
        for (int i = 0; i < Timer.MINUTES.length; i++) {
            if (Timer.MINUTES[i] >= leftMinutes) return i + 1;
        }
        return Timer.MINUTES.length;
    }

    private void step(int direction) {
        choice = Math.max(0, Math.min(Timer.MINUTES.length, choice + direction));
        if (choice == 0) Timer.cancel(this);
        else Timer.start(this, Timer.MINUTES[choice - 1]);
        show();
        closeLater();
    }

    private void show() {
        long end = Timer.end(this);
        if (!Timer.canSleep()) label.setText(R.string.no_service);
        else if (end == 0) label.setText(R.string.state_off);
        else label.setText(getString(R.string.in_minutes, (end - System.currentTimeMillis() + 59_999) / 60_000) + " · "
                + getString(R.string.state_on, DateFormat.getTimeFormat(this).format(end)));
    }

    private void closeLater() {
        handler.removeCallbacks(close);
        handler.postDelayed(close, CLOSE_AFTER_MS);
    }
}
