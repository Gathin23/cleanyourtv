package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateUtils;

import java.util.Locale;

/**
 * When an app was last opened from Home, for the cinematic shelf's "Last opened" line: the launcher notes the
 * time whenever it opens an app. No permission and nothing read from other apps, so an app started some other
 * way (a remote's own Netflix button) shows the last time Home opened it.
 */
final class LastOpened {
    private static final String PREFS = "last_opened";

    private LastOpened() {
    }

    /** Notes that Home just opened this app. */
    static void record(Context ctx, String pkg) {
        prefs(ctx).edit().putLong(pkg, System.currentTimeMillis()).apply();
    }

    /** Every app Home opened, package to milliseconds since the epoch (dock_order "recent"). */
    static java.util.Map<String, Long> all(Context ctx) {
        java.util.Map<String, Long> out = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, ?> e : prefs(ctx).getAll().entrySet()) {
            if (e.getValue() instanceof Long) out.put(e.getKey(), (Long) e.getValue());
        }
        return out;
    }

    /** Milliseconds since the epoch, or 0 when Home never opened the app. */
    static long get(Context ctx, String pkg) {
        return pkg == null ? 0L : prefs(ctx).getLong(pkg, 0L);
    }

    /**
     * "5 minutes ago" within the hour, then the day and the time: "today, 11:40 am", "yesterday, 9:40 pm",
     * "3 days ago, 9:40 pm", "Oct 12, 9:40 pm". The parts are Android's own wording in the TV's language.
     */
    static String describe(Context ctx, long when) {
        long now = System.currentTimeMillis();
        // Under a minute Android says "0 minutes ago"; one minute reads better.
        when = Math.min(when, now - DateUtils.MINUTE_IN_MILLIS);
        if (now - when < DateUtils.HOUR_IN_MILLIS) {
            return DateUtils.getRelativeTimeSpanString(when, now, DateUtils.MINUTE_IN_MILLIS).toString();
        }
        String day = DateUtils.getRelativeTimeSpanString(when, now, DateUtils.DAY_IN_MILLIS).toString();
        // It follows "Last opened" mid-sentence: "yesterday", not "Yesterday". Month names keep their capital.
        if (Locale.getDefault().getLanguage().equals("en") && (day.startsWith("Yesterday") || day.startsWith("Today"))) {
            day = Character.toLowerCase(day.charAt(0)) + day.substring(1);
        }
        return day + ", " + DateUtils.formatDateTime(ctx, when, DateUtils.FORMAT_SHOW_TIME);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
