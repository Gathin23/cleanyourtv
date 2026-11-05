package io.github.shivamsingh262.photoprints;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

/**
 * The app's tile and its module card on cleanyourtv's Home: the same scene as the screensaver, right away, until
 * Back. The screen stays on while it shows.
 */
public class SlideshowActivity extends Activity {

    private Prints prints;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.prints);
        prints = new Prints(this, getWindow().getDecorView());
    }

    @Override
    protected void onStart() {
        super.onStart();
        prints.start();
    }

    @Override
    protected void onStop() {
        prints.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        prints.release();
        super.onDestroy();
    }
}
