package io.github.tylerbuza.wintercabin;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import com.gathin.winter.WinterCabinView;

/** The TV tile and module card both open the scene at once; Back closes it. */
public final class CabinActivity extends Activity {
    private WinterCabinView scene;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        scene = new WinterCabinView(this);
        setContentView(scene);
    }

    @Override protected void onResume() {
        super.onResume();
        scene.start();
    }

    @Override protected void onPause() {
        scene.stop();
        super.onPause();
    }
}
