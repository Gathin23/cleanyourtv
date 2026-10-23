package io.github.justinnewman.bedtimelock;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * The owner's screen. The first run asks for a new PIN twice; after that the PIN opens the settings: the locked
 * hours, on or off, whether TV Settings need the PIN at any hour, a new PIN, the helper's state and a test of the lock.
 * Entering the PIN here also opens TV Settings until standby, so the helper can be turned on from the link below.
 */
public final class SettingsActivity extends Activity {
    private static final int VERIFY = 0, CREATE = 1, REPEAT = 2, ROWS = 3;
    private static final int STEP_MINUTES = 15;

    private Store store;
    private int stage;
    private String first;
    private PinPad pad;
    private TextView message;
    /** The time row Up and Down are changing, or null. */
    private TextView editing;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = new Store(this);
        if (!store.hasPin()) showPin(CREATE, 0);
        else if (store.settingsOpen()) showRows();
        else showPin(VERIFY, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stage == ROWS) render();
    }

    private void showPin(int which, int note) {
        stage = which;
        setContentView(R.layout.activity_lock);
        ((TextView) findViewById(R.id.title)).setText(
                which == VERIFY ? R.string.owner_check : which == CREATE ? R.string.create_pin : R.string.repeat_pin);
        message = findViewById(R.id.message);
        if (note != 0) message.setText(note);
        ViewGroup cells = findViewById(R.id.cells);
        pad = new PinPad(cells, this::onPin);
        cells.requestFocus();
    }

    private void onPin(String pin) {
        long now = System.currentTimeMillis();
        if (stage == VERIFY) {
            int wait = store.secondsToWait(now);
            if (wait > 0) {
                message.setText(getString(R.string.wait_seconds, wait));
            } else if (store.checkPin(pin)) {
                store.succeeded();
                store.setSettingsOpen();
                showRows();
            } else {
                store.failed(now);
                wait = store.secondsToWait(now);
                message.setText(wait > 0 ? getString(R.string.wait_seconds, wait) : getString(R.string.wrong_pin));
            }
        } else if (stage == CREATE) {
            first = pin;
            showPin(REPEAT, 0);
        } else if (pin.equals(first)) {
            store.setPin(pin);
            store.setSettingsOpen();
            LockService.reschedule();
            showRows();
        } else {
            showPin(CREATE, R.string.pins_differ);
        }
    }

    private void showRows() {
        stage = ROWS;
        editing = null;
        setContentView(R.layout.activity_settings);
        findViewById(R.id.enabled).setOnClickListener(v -> {
            store.setEnabled(!store.enabled());
            render();
        });
        findViewById(R.id.from).setOnClickListener(v -> edit((TextView) v));
        findViewById(R.id.until).setOnClickListener(v -> edit((TextView) v));
        findViewById(R.id.protect).setOnClickListener(v -> {
            store.setProtectSettings(!store.protectSettings());
            render();
        });
        findViewById(R.id.changePin).setOnClickListener(v -> showPin(CREATE, 0));
        findViewById(R.id.service).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        findViewById(R.id.test).setOnClickListener(v -> startActivity(new Intent(this, LockActivity.class)
                .putExtra(LockActivity.EXTRA_REASON, LockActivity.TEST)));
        render();
        findViewById(R.id.enabled).requestFocus();
    }

    private void render() {
        String on = getString(R.string.on), off = getString(R.string.off);
        ((TextView) findViewById(R.id.enabled)).setText(getString(R.string.row_enabled, store.enabled() ? on : off));
        ((TextView) findViewById(R.id.from)).setText(getString(R.string.row_from, LockActivity.time(this, store.from())));
        ((TextView) findViewById(R.id.until)).setText(getString(R.string.row_until, LockActivity.time(this, store.until())));
        ((TextView) findViewById(R.id.protect)).setText(getString(R.string.row_settings, store.protectSettings() ? on : off));
        ((TextView) findViewById(R.id.service)).setText(serviceOn() ? R.string.row_service_on : R.string.row_service_off);
        ((TextView) findViewById(R.id.note)).setText(editing != null ? R.string.editing_hint : R.string.unlocked_note);
    }

    private boolean serviceOn() {
        String list = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return list != null && list.contains(getPackageName() + "/");
    }

    private void edit(TextView row) {
        editing = row;
        render();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (stage != ROWS) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);
            if (stage == VERIFY && store.secondsToWait(System.currentTimeMillis()) > 0) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    message.setText(getString(R.string.wait_seconds,
                            store.secondsToWait(System.currentTimeMillis())));
                }
                return true;
            }
            return pad.onKey(event) || super.dispatchKeyEvent(event);
        }
        if (editing == null) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (down) {
                int delta = code == KeyEvent.KEYCODE_DPAD_UP ? STEP_MINUTES : -STEP_MINUTES;
                boolean from = editing.getId() == R.id.from;
                int value = Schedule.shift(from ? store.from() : store.until(), delta);
                store.setHours(from ? value : store.from(), from ? store.until() : value);
                LockService.reschedule();
                render();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BACK) {
            if (!down) {
                editing = null;
                render();
            }
            return true;
        }
        return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

}
