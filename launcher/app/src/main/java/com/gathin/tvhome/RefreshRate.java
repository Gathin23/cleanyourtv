package com.gathin.tvhome;

import android.app.Activity;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

/**
 * Asks Android to run Home at the highest refresh rate the display offers at its current resolution
 * (120 Hz on a panel that has a 120 Hz mode, 60 Hz otherwise). This is a request: Android and the panel decide,
 * and a TV that only has 60 Hz modes stays at 60.
 */
final class RefreshRate {
    private RefreshRate() {}

    /**
     * The id of the fastest mode with the same size as the current one, or the current id when nothing is faster.
     * Pure arrays so it can be tested on the JVM; the four arrays line up by index.
     */
    static int bestModeId(int currentId, int[] ids, int[] widths, int[] heights, float[] rates) {
        int curW = -1;
        int curH = -1;
        float curRate = 0f;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == currentId) {
                curW = widths[i];
                curH = heights[i];
                curRate = rates[i];
            }
        }
        if (curW < 0) return currentId;
        int best = currentId;
        float bestRate = curRate;
        for (int i = 0; i < ids.length; i++) {
            if (widths[i] == curW && heights[i] == curH && rates[i] > bestRate + 0.5f) {
                best = ids[i];
                bestRate = rates[i];
            }
        }
        return best;
    }

    /** Sets the window's preferred display mode. Safe to call when the display has a single mode. */
    static void apply(Activity activity) {
        try {
            Display display = activity.getWindowManager().getDefaultDisplay();
            Display.Mode current = display.getMode();
            Display.Mode[] modes = display.getSupportedModes();
            if (current == null || modes == null || modes.length < 2) return;
            int n = modes.length;
            int[] ids = new int[n];
            int[] widths = new int[n];
            int[] heights = new int[n];
            float[] rates = new float[n];
            for (int i = 0; i < n; i++) {
                ids[i] = modes[i].getModeId();
                widths[i] = modes[i].getPhysicalWidth();
                heights[i] = modes[i].getPhysicalHeight();
                rates[i] = modes[i].getRefreshRate();
            }
            int best = bestModeId(current.getModeId(), ids, widths, heights, rates);
            if (best == current.getModeId()) return;
            Window window = activity.getWindow();
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.preferredDisplayModeId = best;
            window.setAttributes(lp);
        } catch (RuntimeException ignored) {
            // A display that refuses mode queries just keeps the system's choice.
        }
    }
}
