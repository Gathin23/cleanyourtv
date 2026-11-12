package com.gathin.homedashboard;
import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

public class DashboardActivity extends Activity {
    private Scene scene;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
    }
    @Override public void onResume() { super.onResume(); scene = new Scene(this, getIntent().getBooleanExtra("demo", false)); setContentView(scene.root); scene.start(); }
    @Override public void onPause() { scene.stop(); super.onPause(); }
    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) { startActivity(new android.content.Intent(this, SettingsActivity.class)); return true; }
        return super.onKeyDown(code, e);
    }
}
