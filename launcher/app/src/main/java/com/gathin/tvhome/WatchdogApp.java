package com.gathin.tvhome;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Writes the time of each crash to a small preference, then lets the previous handler carry on, so a crash
 * looks the same as before. {@link Config#load} reads it back to choose safe mode (docs/WATCHDOG.md).
 */
public class WatchdogApp extends Application {
    private static final String PREFS = "crash_watchdog";
    private static final String KEY = "crashes";

    @Override
    public void onCreate() {
        super.onCreate();
        if (!getResources().getBoolean(R.bool.crash_watchdog)) return;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
                // commit(), not apply(): the process is about to die and apply() would be lost.
                p.edit().putString(KEY, CrashWatchdog.format(
                        CrashWatchdog.record(CrashWatchdog.parse(p.getString(KEY, null)), System.currentTimeMillis()))).commit();
            } catch (Throwable ignored) {
                // Never hide the original crash.
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    static boolean safeMode(Context ctx) {
        if (!ctx.getResources().getBoolean(R.bool.crash_watchdog)) return false;
        SharedPreferences p = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
        return CrashWatchdog.safeMode(CrashWatchdog.parse(p.getString(KEY, null)), System.currentTimeMillis());
    }

    /** A theme push or reset forgets the crashes, so a fixed config gets a fresh start. */
    static void clear(Context ctx) {
        if (!ctx.getResources().getBoolean(R.bool.crash_watchdog)) return;
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY).apply();
    }
}
