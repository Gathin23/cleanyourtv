package com.gathin.tvhome;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;

/**
 * Keeps the TextClocks under a view ticking on their own. Before Android 12 a TextClock without seconds only
 * advances on ACTION_TIME_TICK; some firmware stops sending it for good after a sleep (a TCL Android 9 TV, once
 * the power button has been used), which freezes every clock until a reboot. From Android 12 a TextClock times
 * itself, so there this does nothing. Otherwise it redraws them at each minute change of the wall clock, and as
 * soon as the display comes back on: the wait to the next minute runs on uptime, which stands still while the set
 * sleeps. A display listener rather than ACTION_SCREEN_ON, as that same TCL firmware leaves the app that was on
 * screen out of that broadcast.
 */
final class ClockRefresh implements Runnable, DisplayManager.DisplayListener {
    /** Past the minute change, so the redraw cannot land just before it and repaint the old minute. */
    private static final long MARGIN_MS = 250L;

    private final View root;
    private final DisplayManager displays;
    /** The display's last state seen by onDisplayChanged; it is on when start() runs. */
    private boolean on = true;

    /** root holds the TextClocks. */
    ClockRefresh(View root) {
        this.root = root;
        displays = (DisplayManager) root.getContext().getSystemService(Context.DISPLAY_SERVICE);
    }

    /**
     * Redraw at the next minute change, then every minute and when the display comes on; calling it again
     * re-aligns after the wall clock was set (registering the same listener twice is a no-op).
     */
    void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return;
        displays.registerDisplayListener(this, null);
        schedule();
    }

    /** Safe to call when never started. */
    void stop() {
        root.removeCallbacks(this);
        displays.unregisterDisplayListener(this);
    }

    @Override
    public void run() {
        redraw(root);
        schedule();
    }

    /** Redraws when our display goes from off to on, not on its other changes. */
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

    private void schedule() {
        root.removeCallbacks(this);
        root.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + MARGIN_MS);
    }

    /**
     * setFormat12Hour chooses the format again and calls the clock's onTimeChanged (checked in AOSP 8, 9 and 11),
     * so setting the pattern it already has changes nothing but the text.
     */
    private static void redraw(View v) {
        if (v instanceof TextClock) {
            TextClock c = (TextClock) v;
            c.setFormat12Hour(c.getFormat12Hour());
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) redraw(g.getChildAt(i));
        }
    }
}
