package com.gathin.addons.youtubetimer;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public class TimerAccessibilityService extends AccessibilityService {

    private static final String YOUTUBE_PKG = "com.google.android.youtube.tv";
    private static final long TICK_MS = 5000L;

    private TimerPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isYouTubeForeground = false;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!isYouTubeForeground) return;

            // Increment today's watch count
            prefs.addSecondsToday((int) (TICK_MS / 1000));

            if (prefs.isLimitReached()) {
                // Limit reached! Trigger lock screen
                Intent lock = new Intent(TimerAccessibilityService.this, LockActivity.class);
                lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(lock);
            } else {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new TimerPreferences(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;

        String currentPkg = pkg.toString();

        if (YOUTUBE_PKG.equals(currentPkg)) {
            if (!isYouTubeForeground) {
                isYouTubeForeground = true;
                handler.removeCallbacks(ticker);

                // If limit is already reached upon opening, lock immediately
                if (prefs.isLimitReached()) {
                    Intent lock = new Intent(this, LockActivity.class);
                    lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(lock);
                } else {
                    handler.postDelayed(ticker, TICK_MS);
                }
            }
        } else if (!isSystemOverlay(currentPkg)) {
            // Moved away from YouTube to another real app (Home, Netflix, Prime, etc.)
            if (isYouTubeForeground) {
                isYouTubeForeground = false;
                handler.removeCallbacks(ticker);
            }
        }
    }

    private boolean isSystemOverlay(String pkg) {
        if (pkg == null) return false;
        return pkg.equals("android")
                || pkg.equals(getPackageName())
                || pkg.startsWith("com.android.systemui")
                || pkg.startsWith("org.droidtv.")
                || pkg.startsWith("com.mediatek.")
                || pkg.contains("inputmethod");
    }

    @Override
    public void onInterrupt() {
        isYouTubeForeground = false;
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isYouTubeForeground = false;
        handler.removeCallbacks(ticker);
    }
}
