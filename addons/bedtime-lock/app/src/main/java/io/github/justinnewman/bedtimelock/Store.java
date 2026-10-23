package io.github.justinnewman.bedtimelock;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Everything the lock keeps, in the app's own preferences (backups are off). "unlocked" (the PIN at the lock) and
 * "settings_open" (the PIN in front of TV Settings or this app) both last until the TV goes to standby; "unlocked"
 * also ends with the locked hours.
 */
final class Store {
    private final SharedPreferences p;

    Store(Context context) {
        p = context.getSharedPreferences("lock", Context.MODE_PRIVATE);
    }

    boolean hasPin() {
        return p.contains("pin");
    }

    boolean checkPin(String pin) {
        return Pin.valid(pin) && Pin.hash(p.getString("salt", ""), pin).equals(p.getString("pin", ""));
    }

    void setPin(String pin) {
        String salt = Pin.newSalt();
        p.edit().putString("salt", salt).putString("pin", Pin.hash(salt, pin)).apply();
    }

    boolean enabled() {
        return p.getBoolean("enabled", true);
    }

    void setEnabled(boolean on) {
        p.edit().putBoolean("enabled", on).apply();
    }

    /** Start and end of the locked hours, minutes of the day; 21:00 to 07:00 until changed. */
    int from() {
        return p.getInt("from", 21 * 60);
    }

    int until() {
        return p.getInt("until", 7 * 60);
    }

    void setHours(int from, int until) {
        p.edit().putInt("from", from).putInt("until", until).apply();
    }

    boolean protectSettings() {
        return p.getBoolean("protect_settings", true);
    }

    void setProtectSettings(boolean on) {
        p.edit().putBoolean("protect_settings", on).apply();
    }

    boolean unlocked() {
        return p.getBoolean("unlocked", false);
    }

    void setUnlocked() {
        p.edit().putBoolean("unlocked", true).commit();
    }

    boolean settingsOpen() {
        return unlocked() || p.getBoolean("settings_open", false);
    }

    void setSettingsOpen() {
        p.edit().putBoolean("settings_open", true).commit();
    }

    /** The locked hours are over: a night unlock ends with them, so it cannot carry into the next night. */
    void endNightUnlock() {
        if (unlocked()) p.edit().putBoolean("unlocked", false).commit();
    }

    /** The TV went to standby: both unlocks end. */
    void lockAgain() {
        p.edit().putBoolean("unlocked", false).putBoolean("settings_open", false).commit();
    }

    boolean nightNow() {
        return enabled() && Schedule.inWindow(minuteNow(), from(), until());
    }

    /** Seconds still to wait after too many wrong tries; 0 when a PIN may be tried. */
    int secondsToWait(long now) {
        long left = p.getLong("wait_until", 0) - now;
        return left > 0 ? (int) ((left + 999) / 1000) : 0;
    }

    /** A wrong PIN: the fifth in a row starts the wait. */
    void failed(long now) {
        int tries = p.getInt("tries", 0) + 1;
        SharedPreferences.Editor e = p.edit();
        if (tries >= Pin.MAX_TRIES) e.putLong("wait_until", now + Pin.WAIT_MS).putInt("tries", 0);
        else e.putInt("tries", tries);
        e.apply();
    }

    void succeeded() {
        p.edit().putInt("tries", 0).remove("wait_until").apply();
    }

    static int minuteNow() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }
}
