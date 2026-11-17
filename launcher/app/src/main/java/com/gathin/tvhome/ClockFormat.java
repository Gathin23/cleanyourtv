package com.gathin.tvhome;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;

/** clock_format: "12" or "24" makes the launcher's clocks use that format whatever the TV is set to; else the TV's. */
final class ClockFormat {
    private ClockFormat() {
    }

    /** Every TextClock under root: its 12- and 24-hour patterns become the chosen one's (the AM/PM clock follows). */
    static void apply(View root, String mode) {
        if (!"12".equals(mode) && !"24".equals(mode)) return;
        if (root instanceof TextClock) {
            TextClock c = (TextClock) root;
            if ("12".equals(mode)) c.setFormat24Hour(c.getFormat12Hour());
            else c.setFormat12Hour(c.getFormat24Hour());
        } else if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i), mode);
        }
    }

    static boolean is24(Context ctx, String mode) {
        return "24".equals(mode) || !"12".equals(mode) && DateFormat.is24HourFormat(ctx);
    }
}
