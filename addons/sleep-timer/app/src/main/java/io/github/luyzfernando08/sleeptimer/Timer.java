package io.github.luyzfernando08.sleeptimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * The one timer: when it ends, and the two alarms behind it. The warning alarm fires a minute early and opens
 * WarningActivity; the sleep alarm, a moment after the end, is the backstop that puts the TV to sleep even if
 * the warning never showed.
 */
final class Timer {
    private Timer() { }

    static final int[] MINUTES = {15, 30, 60, 90, 120};
    static final long WARN_MS = 60_000;
    static final String ACTION_WARN = "io.github.luyzfernando08.sleeptimer.WARN";
    static final String ACTION_SLEEP = "io.github.luyzfernando08.sleeptimer.SLEEP";
    static final String ACTION_TEST = "io.github.luyzfernando08.sleeptimer.TEST";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("timer", Context.MODE_PRIVATE);
    }

    /** When the TV goes to sleep, in wall-clock millis, or 0 when no timer is on. */
    static long end(Context ctx) {
        long end = prefs(ctx).getLong("end", 0);
        return end > System.currentTimeMillis() - 5_000 ? end : 0;
    }

    static void start(Context ctx, int minutes) {
        startSeconds(ctx, minutes * 60);
    }

    static void startSeconds(Context ctx, int seconds) {
        long end = System.currentTimeMillis() + seconds * 1000L;
        prefs(ctx).edit().putLong("end", end).apply();
        schedule(ctx, ACTION_WARN, Math.max(System.currentTimeMillis(), end - WARN_MS));
        schedule(ctx, ACTION_SLEEP, end + 2_000);
    }

    static void cancel(Context ctx) {
        prefs(ctx).edit().remove("end").apply();
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        am.cancel(pending(ctx, ACTION_WARN));
        am.cancel(pending(ctx, ACTION_SLEEP));
    }

    /** Ends the timer and puts the TV to sleep. False when the accessibility service is off. */
    static boolean sleepNow(Context ctx) {
        cancel(ctx);
        return SleepService.sleep();
    }

    static boolean canSleep() {
        return SleepService.running();
    }

    private static void schedule(Context ctx, String action, long at) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent pi = pending(ctx, action);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static PendingIntent pending(Context ctx, String action) {
        Intent intent = new Intent(ctx, AlarmReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
