package io.github.maxhayim.brightness;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * A small brightness bar at the bottom of the screen, for a remote button mapped to it (the launcher's app_keys,
 * "hold:<keycode>:io.github.maxhayim.brightness/.QuickActivity"). Left/Right step the brightness that is in use
 * now (night brightness while night mode is on) and switch dimming on; OK or Back, or a few seconds untouched,
 * closes it; the settings stay on the app's tile. It is drawn like one of the launcher's glass panels, so it
 * reads as part of Home.
 */
public class QuickActivity extends Activity {
    private static final long CLOSE_AFTER_MS = 4000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable close = this::finish;
    private Dimming.Settings s;
    private TextView label;
    private ProgressBar bar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(28), px(18), px(28), px(18));
        panel.setBackground(getDrawable(R.drawable.glass_panel));

        label = new TextView(this);
        label.setTextSize(20);
        label.setTextColor(0xFFFFFFFF);
        panel.addView(label);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(Dimming.MAX_BRIGHTNESS);
        // White on the glass, like the launcher's own bars, not the theme's orange.
        bar.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(px(420), px(10));
        barLp.topMargin = px(12);
        barLp.bottomMargin = px(10);
        panel.addView(bar, barLp);

        TextView hint = new TextView(this);
        hint.setText(R.string.quick_hint);
        hint.setTextSize(13);
        hint.setTextColor(0xB3FFFFFF);
        panel.addView(hint);

        FrameLayout root = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = px(56);
        root.addView(panel, lp);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!DimService.canDraw(this)) {
            // The settings screen shows the permission row; a bar that moves while nothing dims would mislead.
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        s = Prefs.load(this);
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

    private boolean nightNow() {
        return Dimming.nightNow(s, DimService.minuteNow());
    }

    private void step(int direction) {
        closeLater();
        if (!s.on) {
            // Off reads as 100 %: brighter is nothing to do, and the first dimming step starts both values there,
            // so the picture does not jump to an old stored value when night mode ends.
            if (direction > 0) return;
            s.brightness = Dimming.MAX_BRIGHTNESS;
            s.nightBrightness = Dimming.MAX_BRIGHTNESS;
            s.on = true;
        }
        if (nightNow()) s.nightBrightness = Dimming.stepBrightness(s.nightBrightness, direction);
        else s.brightness = Dimming.stepBrightness(s.brightness, direction);
        Prefs.save(this, s);
        DimService.start(this);
        show();
    }

    private void show() {
        boolean night = nightNow();
        int value = !s.on ? Dimming.MAX_BRIGHTNESS : night ? s.nightBrightness : s.brightness;
        label.setText(getString(night ? R.string.quick_night_brightness : R.string.quick_brightness, value));
        bar.setProgress(value);
    }

    private void closeLater() {
        handler.removeCallbacks(close);
        handler.postDelayed(close, CLOSE_AFTER_MS);
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
