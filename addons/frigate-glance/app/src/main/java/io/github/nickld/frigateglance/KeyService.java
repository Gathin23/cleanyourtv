package io.github.nickld.frigateglance;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Dismiss and Snooze from the remote while a card shows. Key filtering is switched on only while a card is
 * up (and until a caught key is released): the rest of the time this service sees no keys at all, so it
 * cannot slow or change anything (some TCL firmware drops key repeat while any service filters keys).
 */
public class KeyService extends AccessibilityService {
    private static volatile KeyService instance;
    private static volatile boolean showing;

    private final Handler main = new Handler(Looper.getMainLooper());
    private KeyRules rules = new KeyRules(0, 0);

    /** True while Android has the service bound, so the remote buttons can work. */
    static boolean isRunning() {
        return instance != null;
    }

    /** Called when a card appears or leaves. */
    static void setFiltering(boolean cardShowing) {
        showing = cardShowing;
        KeyService s = instance;
        if (s != null) s.main.post(s::applyFilter);
    }

    /** Re-reads the dismiss and snooze keys after a settings change. */
    static void reloadKeys(Context context) {
        KeyService s = instance;
        if (s != null) s.main.post(s::loadKeys);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        loadKeys();
        applyFilter();
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int pendingBefore = rules.pendingHold();
        int d = rules.decide(event.getKeyCode(), event.getAction(), event.getEventTime(), showing);
        int pendingNow = rules.pendingHold();
        if (pendingNow != 0 && pendingBefore == 0) GlanceService.holdStarted(pendingNow);
        else if (pendingBefore != 0 && pendingNow == 0) GlanceService.holdEnded();
        if (d == KeyRules.DISMISS) GlanceService.dismissCard();
        else if (d == KeyRules.SNOOZE) GlanceService.snoozeCard();
        else if (d == KeyRules.FULLSCREEN) GlanceService.openFullscreen();
        if (!rules.holding() && !showing) applyFilter();
        return d != KeyRules.PASS;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Keys only.
    }

    @Override
    public void onInterrupt() {
        // Nothing to stop.
    }

    private void loadKeys() {
        GlanceConfig c = new ConfigStore(this).load();
        if (c == null) {
            rules = new KeyRules(0, 0);
            return;
        }
        int dismiss = KeyEvent.keyCodeFromString(c.dismissKey);
        int snooze = KeyEvent.keyCodeFromString(c.snoozeKey);
        if (dismiss == KeyEvent.KEYCODE_UNKNOWN) Log.w(ConfigStore.TAG, "unknown dismissKey " + c.dismissKey);
        if (snooze == KeyEvent.KEYCODE_UNKNOWN) Log.w(ConfigStore.TAG, "unknown snoozeKey " + c.snoozeKey);
        int fullscreen = c.fullscreen ? KeyEvent.keyCodeFromString(c.fullscreenKey) : 0;
        rules = new KeyRules(dismiss, snooze, fullscreen, c.dismissHold, c.snoozeHold, c.fullscreenHold);
    }

    private void applyFilter() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        boolean want = showing || rules.holding();
        boolean has = (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
        if (want == has) return;
        if (want) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
    }
}
