package com.gathin.haunted;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

/** The tile and the card: the same screen as the screensaver, at once, until Back. */
public class HauntedActivity extends Activity {

    private HauntedView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new HauntedView(this);
        setContentView(view);
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
}
