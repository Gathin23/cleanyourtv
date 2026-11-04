package com.mauro.tv.monitor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * Fires when the USB debugging window opened from the add-on runs out, and turns USB debugging off again.
 * {@link #set} is the one place that turns it on (for MINUTES, with this alarm as the backstop) or off.
 */
public final class AdbOffReceiver extends BroadcastReceiver {
    static final int MINUTES = 30;
    /** When the window we opened ends, in wall-clock ms. The alarm does not survive a restart; this does. */
    static final String UNTIL = "adb_until";

    @Override public void onReceive(Context context, Intent intent) {
        set(context, false);
    }

    /** Needs WRITE_SECURE_SETTINGS, granted once over ADB; without it nothing changes. */
    static void set(Context context, boolean on) {
        SharedPreferences prefs = context.getSharedPreferences("journal", Context.MODE_PRIVATE);
        try {
            Settings.Global.putInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, on ? 1 : 0);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(context, 1, new Intent(context, AdbOffReceiver.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            long until = System.currentTimeMillis() + MINUTES * 60000L;
            if (on) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pi);
            else am.cancel(pi);
            if (on) prefs.edit().putLong(UNTIL, until).apply();
            else prefs.edit().remove(UNTIL).apply();
        } catch (RuntimeException ignored) { }
    }

    /**
     * After a restart: closes a window that ran out, or sets the alarm again for the time that is left. Does nothing
     * if no window of ours is open.
     */
    static void rearm(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("journal", Context.MODE_PRIVATE);
        long until = prefs.getLong(UNTIL, 0);
        if (until == 0) return;
        if (System.currentTimeMillis() >= until) {
            closeIfOverdue(context);
            return;
        }
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pi = PendingIntent.getBroadcast(context, 1, new Intent(context, AdbOffReceiver.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pi);
        } catch (RuntimeException ignored) { }
    }

    /**
     * Turns debugging off if a window we opened has run out while the alarm could not fire (the TV restarted in
     * between: a restart drops alarms but keeps the setting). Called whenever the add-on is on screen.
     */
    static void closeIfOverdue(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("journal", Context.MODE_PRIVATE);
        long until = prefs.getLong(UNTIL, 0);
        if (until == 0 || System.currentTimeMillis() < until) return;
        try {
            if (Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1) set(context, false);
            else prefs.edit().remove(UNTIL).apply();
        } catch (RuntimeException ignored) { }
    }
}
