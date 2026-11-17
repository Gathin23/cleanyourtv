package io.github.smartgenius2.neondream;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

/** Shows the screensaver with the saved options until Back. */
public class PreviewActivity extends Activity {

    private NeonView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Prefs prefs = new Prefs(this);
        view = new NeonView(this, prefs.scene(), prefs.palette(), prefs.clock());
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
