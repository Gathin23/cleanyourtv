package io.github.dpetrasgr.livetvmode;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Told which app's window comes to the front, and nothing else. While the home screen is locked, its coming to the
 * front (Home, Back from an app, the TV starting) opens Live TV at once, so to a viewer Home seems to do nothing;
 * quick returns to it count toward the burst that unlocks it until the TV next sleeps. Screen off and on lock it
 * again, and waking up opens Live TV.
 *
 * Android binds an enabled accessibility service at boot and keeps it, so there is no boot receiver and no
 * foreground service, and a service the system has bound may start activities from the background.
 */
public class LiveTvService extends AccessibilityService {
    /** Connected this soon after the TV started, the helper takes it the home screen is up and opens Live TV: Android
     *  can show Home before it binds the helper, and that window change would be missed. Later (the helper switched
     *  on again, or rebound) it leaves whatever is on screen alone. */
    static final long BOOT_WINDOW_MS = 180_000;
    private final Mode mode = new Mode();
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** The package of the last app window that came to the front (system UI and this app left out). */
    private String front = "";
    private boolean homeAtSleep;
    private boolean liveTvAtSleep;

    /** The press that unlocked the home screen came right after one that started Live TV, which can reach the
     *  screen after it: Home comes back over it. */
    private final Runnable homeBack = () -> {
        if (!Prefs.isHomeScreen(this, front)) {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {
                // no home screen to go back to; Live TV stays
            }
        }
    };

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                String liveTv = Prefs.liveTv(LiveTvService.this);
                homeAtSleep = Prefs.isHomeScreen(LiveTvService.this, front);
                liveTvAtSleep = !liveTv.isEmpty() && liveTv.equals(front);
                mode.lock();
                return;
            }
            // Both lock: waking up must find the home screen locked even if the screen-off broadcast came late.
            mode.lock();
            if (Prefs.enabled(LiveTvService.this)
                    && Mode.opensOnWake(homeAtSleep, liveTvAtSleep, Prefs.wakeFromApps(LiveTvService.this))) {
                openLiveTv(SystemClock.elapsedRealtime());
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screen, f);
        }
        long now = SystemClock.elapsedRealtime();
        if (Prefs.enabled(this) && now < BOOT_WINDOW_MS) openLiveTv(now);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;
        front = pkg;
        if (!Prefs.enabled(this) || !Prefs.isHomeScreen(this, pkg)) return;
        long now = SystemClock.elapsedRealtime();
        switch (mode.home(now, Prefs.presses(this))) {
            case OPEN_LIVE_TV:
                openLiveTv(now);
                break;
            case UNLOCKED:
                if (mode.openedWithin(now, Mode.PRESS_GAP_MS)) {
                    handler.postDelayed(homeBack, 400);
                    handler.postDelayed(homeBack, 1000);   // a slow Live TV app can arrive after the first look
                }
                break;
            default:
                break;
        }
    }

    private void openLiveTv(long now) {
        String pkg = Prefs.liveTv(this);
        Intent i = pkg.isEmpty() ? null : Prefs.launchIntent(this, pkg);
        if (i == null || mode.looping(now)) return;
        mode.opened(now);
        try {
            startActivity(i);
        } catch (RuntimeException ignored) {
            // the app went away between the lookup and the start; Home stays
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(screen);
        } catch (IllegalArgumentException ignored) {
            // never registered: the service was not connected
        }
        super.onDestroy();
    }
}
