package io.github.shaharukhs.tvinspector;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.Deque;

/** Shows every key the remote delivers to apps: its name, key code, scan code and the device it came from. */
public final class KeysActivity extends Screen {
    private static final int RECENT = 8;
    private final Deque<String> recent = new ArrayDeque<>();
    private TextView name, detail, hint, history;
    private long lastBack;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        layout();
        hint = text(getString(R.string.keys_hint), 20);
        name = text(getString(R.string.keys_waiting), 34);
        detail = text("", 20);
        text(getString(R.string.keys_recent), 22);
        history = text("", 18);
        text(getString(R.string.keys_missing), 16);
    }

    /** Takes every key before focus handling, so the D-pad is reported instead of moving anything. */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() != KeyEvent.ACTION_UP) return true;
            long now = SystemClock.uptimeMillis();
            if (now - lastBack < 2000) { finish(); return true; }
            lastBack = now;
            show(event);
            hint.setText(getString(R.string.keys_back_again));
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) show(event);
        boolean volume = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE;
        return volume ? super.dispatchKeyEvent(event) : true;
    }

    private void show(KeyEvent event) {
        String key = KeyEvent.keyCodeToString(event.getKeyCode());
        if (event.getRepeatCount() > 0) {
            name.setText(key + ", " + getString(R.string.keys_held, event.getRepeatCount()));
            return;
        }
        InputDevice device = event.getDevice();
        String line = Labels.key(key, event.getKeyCode(), event.getScanCode(),
                device == null || device.isVirtual() ? null : device.getName());
        name.setText(key);
        detail.setText(line);
        recent.addFirst(line);
        while (recent.size() > RECENT) recent.removeLast();
        history.setText(String.join("\n", recent));
        Log.i("TvInspector", "key " + line);
    }
}
