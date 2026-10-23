package io.github.justinnewman.bedtimelock;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

/**
 * Watches which app comes to the front (window changes only; no keys, no window content). During the locked hours,
 * or on TV Settings when that is protected, it puts LockActivity over it. A timer brings the lock up when the hours
 * start over something already on screen, and the TV going to standby ends any unlock.
 */
public final class LockService extends AccessibilityService {
    /** The running service, so a change of hours in SettingsActivity can move the timer. */
    private static LockService running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::onTick;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) store.lockAgain();
            else onTick();
        }
    };
    private Store store;
    /** The package last seen at the front, other than this app and system overlays. */
    private String front;

    static void reschedule() {
        if (running != null) running.schedule();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        store = new Store(this);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screen, filter);
        }
        running = this;
        onTick();
    }

    @Override
    public void onDestroy() {
        running = null;
        main.removeCallbacks(tick);
        if (store != null) unregisterReceiver(screen);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (store == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence name = event.getPackageName();
        if (name == null) return;
        String pkg = name.toString();
        if (passesThrough(pkg)) return;
        front = pkg;
        lockIfNeeded(pkg);
    }

    @Override
    public void onInterrupt() {
    }

    /** This app, the system UI (volume, notifications) and the on-screen keyboard never trigger the lock. */
    private boolean passesThrough(String pkg) {
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return true;
        String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return ime != null && ime.startsWith(pkg + "/");
    }

    private void lockIfNeeded(String pkg) {
        int reason = reason(pkg);
        if (reason < 0) return;
        startActivity(new Intent(this, LockActivity.class)
                .putExtra(LockActivity.EXTRA_REASON, reason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    /** Why `pkg` must be covered now (LockActivity.NIGHT or SETTINGS), or -1. */
    private int reason(String pkg) {
        if (!store.hasPin() || store.unlocked()) return -1;
        if (store.nightNow()) return LockActivity.NIGHT;
        boolean settings = pkg.equals("com.android.tv.settings") || pkg.equals("com.android.settings");
        if (settings && store.protectSettings() && !store.settingsOpen()) return LockActivity.SETTINGS;
        return -1;
    }

    private void onTick() {
        if (!store.nightNow()) store.endNightUnlock();
        if (front != null) {
            lockIfNeeded(front);
        } else if (store.hasPin() && !store.unlocked() && store.nightNow()) {
            lockIfNeeded(getPackageName() + ".none");
        }
        schedule();
    }

    /** Wake at the next start or end of the locked hours (a second after the minute turns). */
    private void schedule() {
        main.removeCallbacks(tick);
        long now = System.currentTimeMillis();
        long intoMinute = now % 60_000L;
        int minutes = Schedule.minutesToNextChange(Store.minuteNow(), store.from(), store.until());
        main.postDelayed(tick, minutes * 60_000L - intoMinute + 1_000L);
    }
}
