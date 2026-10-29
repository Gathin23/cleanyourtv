package io.github.justinnewman.gameday;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;

/** Seconds per turn on Home: Left and Right change it by 5, OK saves, Back leaves it as it was. */
public final class SecondsActivity extends Activity {
    private Repository repo;
    private TextView value;
    private int seconds;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_seconds);
        repo = new Repository(this);
        value = findViewById(R.id.value);
        seconds = repo.turnSeconds();
        show();
        value.requestFocus();
    }

    private void show() {
        value.setText("‹  " + seconds + "  ›");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                seconds = Math.max(Repository.TURN_MIN, seconds - 5);
                show();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                seconds = Math.min(Repository.TURN_MAX, seconds + 5);
                show();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                repo.setTurnSeconds(seconds);
                GamesProvider.changed(getApplicationContext());
                finish();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
