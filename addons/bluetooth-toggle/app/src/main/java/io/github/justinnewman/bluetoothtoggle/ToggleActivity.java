package io.github.justinnewman.bluetoothtoggle;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

/**
 * A small panel at the top right that switches Bluetooth. With switch_on_open (options.xml) opening it switches at
 * once and shows the result, so the Home tile is a one-press toggle; the panel then offers the way back and the TV's
 * pairing screen, and closes itself after close_after_seconds without a key press. Nothing runs once it is closed.
 */
public final class ToggleActivity extends Activity {
    private static final int ASK = 1;
    /** Google TV's and Android TV's own "Pair accessory" screen. */
    private static final String CONNECT_INPUT = "com.google.android.intent.action.CONNECT_INPUT";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable close = this::finish;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            show();
        }
    };
    private BluetoothAdapter adapter;
    private TextView state;
    private TextView note;
    private TextView toggle;
    private TextView pair;
    private long closeMs;
    /** Switch once the window is up: on first open, and again when the tile is pressed while the panel shows. */
    private boolean switchPending;
    /** Android's permission prompt is up: the panel must not close itself under it, which would cancel it. */
    private boolean asking;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_toggle);
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager != null ? manager.getAdapter() : null;
        state = findViewById(R.id.state);
        note = findViewById(R.id.note);
        toggle = findViewById(R.id.toggle);
        pair = findViewById(R.id.pair);
        toggle.setOnClickListener(v -> flip());
        pair.setOnClickListener(v -> openPairing());
        closeMs = Math.max(0, getResources().getInteger(R.integer.close_after_seconds)) * 1000L;
        switchPending = saved == null && getResources().getBoolean(R.bool.switch_on_open);
        toggle.requestFocus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (getResources().getBoolean(R.bool.switch_on_open)) switchPending = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(changes, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(changes, filter);
        }
        show();
        if (switchPending) {
            switchPending = false;
            flip();
        }
        armClose();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(changes);
        main.removeCallbacks(close);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // A panel, not a page: once something else is on screen it has done its job.
        if (!isChangingConfigurations() && !asking) finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        armClose();
        return super.dispatchKeyEvent(event);
    }

    private void armClose() {
        main.removeCallbacks(close);
        if (closeMs > 0 && !asking) main.postDelayed(close, closeMs);
    }

    private boolean needsPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private int currentState() {
        if (adapter == null) return States.NONE;
        try {
            return adapter.getState();
        } catch (SecurityException notGranted) {
            return States.NONE;
        }
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    private void flip() {
        if (adapter == null) {
            show();
            return;
        }
        if (needsPermission()) {
            asking = true;
            main.removeCallbacks(close);
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, ASK);
            return;
        }
        int now = currentState();
        if (!States.settled(now)) return;
        boolean on = now == BluetoothAdapter.STATE_ON;
        boolean sent = false;
        if (States.appsMaySwitch(Build.VERSION.SDK_INT)) {
            try {
                sent = on ? adapter.disable() : adapter.enable();
            } catch (SecurityException notGranted) {
                sent = false;
            }
        }
        if (!sent) {
            if (!on && open(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))) return;
            showNote(on && !States.appsMaySwitch(Build.VERSION.SDK_INT) ? R.string.settings_only : R.string.not_switched);
        }
        show();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != ASK) return;
        asking = false;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) flip();
        else showNote(R.string.need_permission);
        armClose();
    }

    private void openPairing() {
        if (open(new Intent(CONNECT_INPUT)) || open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                || open(new Intent(Settings.ACTION_SETTINGS))) {
            finish();
        }
    }

    private boolean open(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    private void show() {
        int now = currentState();
        state.setText(States.words(now));
        state.setCompoundDrawablesRelativeWithIntrinsicBounds(States.icon(now), 0, 0, 0);
        boolean any = now != States.NONE;
        toggle.setVisibility(any ? View.VISIBLE : View.GONE);
        pair.setVisibility(any ? View.VISIBLE : View.GONE);
        toggle.setText(States.action(now));
    }

    private void showNote(int words) {
        note.setText(words);
        note.setVisibility(View.VISIBLE);
    }
}
