package com.gathin.geogenius;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

public final class GeoActivity extends Activity {

    private GeoPanel panel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        panel = new GeoPanel(this, false);
        setContentView(panel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (panel != null) {
            panel.refreshState();
        }
    }

    @Override
    protected void onDestroy() {
        if (panel != null) {
            panel.releaseResources();
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && panel != null) {
            if (panel.handleBackPress()) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}

