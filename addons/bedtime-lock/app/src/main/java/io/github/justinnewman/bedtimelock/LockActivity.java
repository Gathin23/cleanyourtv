package io.github.justinnewman.bedtimelock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Calendar;

/**
 * The full-screen lock. The right code ends it: during the locked hours until the TV goes to standby, on TV Settings
 * for Settings until then. At night Back with nothing typed sends an unlock request to the parents' phones (when phone
 * unlock is on) and shows the request view, its status and a countdown; Right goes back to the code. On Settings,
 * Back goes Home. At the end of the locked hours it closes itself.
 */
public final class LockActivity extends Activity {
    static final String EXTRA_REASON = "reason";
    static final int NIGHT = 0, SETTINGS = 1, TEST = 2;
    /** The test lock was closed from itself (the right PIN or Back); SettingsActivity reads and clears it. */
    static boolean testClosed;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable recheck = this::recheck;
    private final Runnable countdownTask = this::countdown;
    private Store store;
    private PinPad pad;
    private TextView title;
    private TextView message;
    private int reason;
    /** Phone unlock (ntfy), when the owner turned it on; null otherwise. */
    private PhoneUnlock phone;
    /** The request view is on screen instead of the code boxes. */
    private boolean showingRequest;
    /** What the request view says above the countdown. */
    private int requestStatus = R.string.request_sent;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_lock);
        store = new Store(this);
        title = findViewById(R.id.title);
        message = findViewById(R.id.message);
        ViewGroup cells = findViewById(R.id.cells);
        pad = new PinPad(cells, this::onPin);
        String channel = store.phoneChannel();
        if (channel != null) phone = new PhoneUnlock(channel, new PhoneUnlock.Listener() {
            @Override
            public void onAnswer(boolean unlock) {
                if (unlock) {
                    phone.tell(getString(R.string.told_unlocked));
                    store.succeeded();
                    store.setUnlocked();
                    finish();
                } else {
                    phone.tell(getString(R.string.told_not_now));
                    answered(R.string.not_now);
                }
            }

            @Override
            public void onFailed() {
                answered(R.string.phone_failed);
            }

            @Override
            public void onExpired() {
                answered(R.string.no_answer);
            }
        });
        apply(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (phone != null) phone.shutdown();
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
        findViewById(R.id.ask).setVisibility(phone != null && reason == NIGHT ? View.VISIBLE : View.GONE);
        showCode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.postDelayed(recheck, 30_000L);
        if (phone != null) phone.resume();
        countdown();
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(recheck);
        main.removeCallbacks(countdownTask);
        if (phone != null) phone.stop();
    }

    /** The locked hours ended while this was on screen. */
    private void recheck() {
        if (reason == NIGHT && !store.nightNow()) {
            finish();
            return;
        }
        main.postDelayed(recheck, 30_000L);
    }

    private void showCode() {
        showingRequest = false;
        pad.clear();
        findViewById(R.id.requestView).setVisibility(View.GONE);
        findViewById(R.id.codeView).setVisibility(View.VISIBLE);
        findViewById(R.id.cells).requestFocus();
        countdown();
    }

    private void showRequest() {
        showingRequest = true;
        findViewById(R.id.codeView).setVisibility(View.GONE);
        findViewById(R.id.requestView).setVisibility(View.VISIBLE);
        countdown();
    }

    /** Back at night with nothing typed: send a request when none ran in the last WAIT_MS, and show its view. */
    private void askOrShowRequest() {
        if (PhoneUnlock.cooldownLeft(System.currentTimeMillis()) <= 0) {
            phone.ask(getString(R.string.request_text, time(this, Store.minuteNow())));
            requestStatus = R.string.request_sent;
        }
        showRequest();
    }

    /** An answer, a failed send or no answer: say it in the request view, and switch to it if the code was showing. */
    private void answered(int status) {
        requestStatus = status;
        showRequest();
    }

    /**
     * Once a second while a request waits or the next one may not go out yet: the countdown in the request view and
     * the line under the code boxes. Back sends nothing in that time.
     */
    private void countdown() {
        main.removeCallbacks(countdownTask);
        if (phone == null || reason != NIGHT) return;
        long left = PhoneUnlock.cooldownLeft(System.currentTimeMillis());
        boolean waiting = phone.pending();
        TextView ask = findViewById(R.id.ask);
        TextView time = findViewById(R.id.requestTime);
        ((TextView) findViewById(R.id.requestStatus)).setText(requestStatus);
        if (left <= 0) {
            ask.setText(R.string.ask_hint);
            ask.setAlpha(1f);
            time.setText("");
            ((TextView) findViewById(R.id.requestHint)).setText(R.string.request_hint_ready);
            return;
        }
        String clock = PhoneUnlock.clock(left);
        ask.setText(getString(waiting ? R.string.ask_waiting : R.string.ask_again_in, clock));
        ask.setAlpha(0.5f);
        time.setText(waiting ? clock : getString(R.string.ask_again_in, clock));
        ((TextView) findViewById(R.id.requestHint)).setText(R.string.request_hint_code);
        main.postDelayed(countdownTask, 1_000L);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        if (showingRequest) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (up) showCode();
            } else if (code == KeyEvent.KEYCODE_BACK) {
                if (up) askOrShowRequest();
            } else if ((code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9)
                    || (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9)) {
                // A number key: back to the code with that digit typed.
                showCode();
                return pad.onKey(event);
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (up) {
                if (pad.started()) pad.clear();
                else if (reason == NIGHT && phone != null) askOrShowRequest();
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
        else testClosed = true;
        finish();
    }

    /** Back from the Settings lock (or the test): Home, not the screen that was covered. */
    private void leave() {
        if (reason == SETTINGS) {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (reason == TEST) {
            testClosed = true;
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
