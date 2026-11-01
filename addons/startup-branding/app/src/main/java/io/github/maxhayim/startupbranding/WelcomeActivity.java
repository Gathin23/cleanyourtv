package io.github.maxhayim.startupbranding;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * The welcome screen, full screen with nothing else on it. Back or OK closes it, or it closes by itself after the
 * chosen time. Opened by Preview, and by others through the exported {@code .Welcome} alias (a remote button mapped
 * in the launcher, or a launcher hook after boot): then it shows only while the welcome screen is on.
 */
public class WelcomeActivity extends Activity {
    static final String EXTRA_PREVIEW = "preview";

    private final Handler main = new Handler(Looper.getMainLooper());
    private WelcomeView view;
    private volatile boolean gone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Look look = Prefs.load(this);
        // Only this app's own Preview may show it while it's off; through the .Welcome alias, the setting decides.
        boolean outside = getIntent().getComponent() != null && getIntent().getComponent().getClassName().endsWith(".Welcome");
        boolean preview = !outside && getIntent().getBooleanExtra(EXTRA_PREVIEW, false);
        if (look.mode == Look.Mode.OFF && !preview) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new WelcomeView(this);
        view.show(look, null);
        setContentView(view);
        hideBars();
        if (look.mode == Look.Mode.CUSTOM && !look.image.isEmpty()) {
            DisplayMetrics m = getResources().getDisplayMetrics();
            int w = m.widthPixels, h = m.heightPixels;
            // Decoded off the main thread; the colour and text show at once, the picture when it's ready.
            new Thread(() -> {
                Bitmap b = Images.load(this, look.image, w, h);
                main.post(() -> {
                    if (!gone && b != null) view.show(look, b);
                });
            }, "welcome-image").start();
        }
        if (look.seconds > 0) main.postDelayed(this::finish, look.seconds * 1000L);
    }

    private void hideBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                finish();
                return true;
            default:
                // Back closes it the usual way; other keys are ignored so a stray press doesn't skip it.
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onDestroy() {
        gone = true;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
