package io.github.justinnewman.bedtimelock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Calendar;

/**
 * The full-screen lock. The right PIN ends it: during the locked hours until the TV goes to standby, on TV Settings
 * for Settings until then. Back does nothing at night (Home only brings the lock straight back); on Settings it goes
 * Home. At the end of the locked hours it closes itself.
 */
public final class LockActivity extends Activity {
    static final String EXTRA_REASON = "reason";
    static final int NIGHT = 0, SETTINGS = 1, TEST = 2;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable recheck = this::recheck;
    private Store store;
    private PinPad pad;
    private TextView title;
    private TextView message;
    private int reason;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_lock);
        store = new Store(this);
        title = findViewById(R.id.title);
        message = findViewById(R.id.message);
        ViewGroup cells = findViewById(R.id.cells);
        pad = new PinPad(cells, this::onPin);
        cells.requestFocus();
        apply(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        apply(intent);
    }

    private void apply(Intent intent) {
        reason = intent.getIntExtra(EXTRA_REASON, NIGHT);
        title.setText(reason == SETTINGS ? getString(R.string.settings_locked)
                : getString(R.string.locked_until, time(this, store.until())));
        message.setText("");
        pad.clear();
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.postDelayed(recheck, 30_000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(recheck);
    }

    /** The locked hours ended while this was on screen. */
    private void recheck() {
        if (reason == NIGHT && !store.nightNow()) {
            finish();
            return;
        }
        main.postDelayed(recheck, 30_000L);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (pad.started()) pad.clear();
                else if (reason != NIGHT) leave();
            }
            return true;
        }
        int wait = store.secondsToWait(System.currentTimeMillis());
        if (wait > 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) message.setText(getString(R.string.wait_seconds, wait));
            return true;
        }
        return pad.onKey(event) || super.dispatchKeyEvent(event);
    }

    private void onPin(String pin) {
        long now = System.currentTimeMillis();
        if (store.secondsToWait(now) > 0) {
            message.setText(getString(R.string.wait_seconds, store.secondsToWait(now)));
            return;
        }
        if (!store.checkPin(pin)) {
            store.failed(now);
            int wait = store.secondsToWait(now);
            message.setText(wait > 0 ? getString(R.string.wait_seconds, wait) : getString(R.string.wrong_pin));
            return;
        }
        store.succeeded();
        if (reason == SETTINGS) store.setSettingsOpen();
        else if (reason == NIGHT) store.setUnlocked();
        finish();
    }

    /** Back from the Settings lock (or the test): Home, not the screen that was covered. */
    private void leave() {
        if (reason == SETTINGS) {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }

    /** A minute of the day in the TV's 12- or 24-hour format. */
    static String time(Context context, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, minute / 60);
        c.set(Calendar.MINUTE, minute % 60);
        return DateFormat.getTimeFormat(context).format(c.getTime());
    }
}
