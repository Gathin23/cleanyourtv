package com.gathin.tvhome;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.view.View;

/**
 * Keeps the TextClocks under a view ticking on their own. Before Android 12 a TextClock without seconds only
 * advances on ACTION_TIME_TICK; some firmware stops sending it for good after a sleep (a TCL Android 9 TV, once
 * the power button has been used), which freezes every clock until a reboot. From Android 12 a TextClock times
 * itself, so there this does nothing. Otherwise it redraws them at each minute change of the wall clock, and as
 * soon as the display comes back on.
 */
final class ClockRefresh implements Runnable {
    static final boolean NEEDED = Build.VERSION.SDK_INT < Build.VERSION_CODES.S;
    /** Past the minute change, so the redraw cannot land just before it and repaint the old minute. */
    private static final long MARGIN_MS = 250L;

    private final View root;
    private final DisplayManager displays;
    /**
     * The wait to the next minute runs on uptime, which stands still while the set sleeps, so after the power
     * button the clock would show the time it went to sleep at for up to a minute. The display turning back on
     * redraws it at once. A display listener rather than ACTION_SCREEN_ON: that same TCL firmware leaves the
     * app that was on screen out of that broadcast.
     */
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        private boolean on = true;

        @Override
        public void onDisplayChanged(int displayId) {
            Display d = root.getDisplay();
            if (d == null || d.getDisplayId() != displayId) return;
            boolean nowOn = d.getState() == Display.STATE_ON;
            if (nowOn && !on) run();
            on = nowOn;
        }

        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }
    };
    private boolean listening;

    /** root holds the TextClocks. */
    ClockRefresh(View root) {
        this.root = root;
        displays = (DisplayManager) root.getContext().getSystemService(Context.DISPLAY_SERVICE);
    }

    /** Redraw at the next minute change, then every minute and when the display comes on; also re-aligns after the wall clock was set. */
    void start() {
        if (!NEEDED) return;
        if (!listening) {
            displays.registerDisplayListener(displayListener, null);
            listening = true;
        }
        schedule();
    }

    void stop() {
        root.removeCallbacks(this);
        if (listening) {
            displays.unregisterDisplayListener(displayListener);
            listening = false;
        }
    }

    @Override
    public void run() {
        ClockFormat.refresh(root);
        schedule();
    }

    private void schedule() {
        root.removeCallbacks(this);
        root.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + MARGIN_MS);
    }
}
