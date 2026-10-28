package io.github.dennisoleary.digitalrain;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

/** The tile and the card: the same rain as the screensaver, at once, until Back. */
public class RainActivity extends Activity {

    private RainView rain;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        rain = new RainView(this);
        setContentView(rain);
    }

    @Override
    protected void onResume() {
        super.onResume();
        rain.start();
    }

    @Override
    protected void onPause() {
        rain.stop();
        super.onPause();
    }
}
