package io.github.maxhayim.kidsmode;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * While Kids Mode is on, sends any app that isn't on the kids' screen (Settings from the remote's key, an app
 * one of the allowed apps links to) back to the kids' screen. It reads only which app's window came to the
 * front; it doesn't read window content or filter keys.
 */
public class GuardService extends AccessibilityService {
    private static final String TAG = "KidsMode";
    private long lastBounce;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (!Kids.bounce(Store.on(this), pkg, getPackageName(), Store.allowed(this), Apps.keyboards(this))) return;
        // One bounce per half second: some apps change windows several times as they start.
        long now = SystemClock.elapsedRealtime();
        if (now - lastBounce < 500) return;
        lastBounce = now;
        Log.i(TAG, "sent back from " + pkg);
        startActivity(new Intent(this, KidsHomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    @Override
    public void onInterrupt() {
        // Nothing to stop.
    }
}
