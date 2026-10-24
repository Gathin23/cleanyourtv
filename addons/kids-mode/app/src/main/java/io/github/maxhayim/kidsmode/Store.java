package io.github.maxhayim.kidsmode;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Kids Mode's state in the app's own storage (backups are off): on or off, the allowed apps, the lock. */
final class Store {
    private Store() {
    }

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences("kids", Context.MODE_PRIVATE);
    }

    static boolean on(Context c) {
        return of(c).getBoolean("on", false);
    }

    static void setOn(Context c, boolean on) {
        of(c).edit().putBoolean("on", on).apply();
    }

    static Set<String> allowed(Context c) {
        return new HashSet<>(of(c).getStringSet("allowed", new HashSet<>()));
    }

    static boolean appsChosen(Context c) {
        return of(c).contains("allowed");
    }

    static void setAllowed(Context c, Set<String> pkgs) {
        of(c).edit().putStringSet("allowed", new HashSet<>(pkgs)).apply();
    }

    /** The home app Home hands over to while Kids Mode is off; null until known. */
    static ComponentName normalHome(Context c) {
        String s = of(c).getString("normal_home", null);
        return s == null ? null : ComponentName.unflattenFromString(s);
    }

    static void setNormalHome(Context c, ComponentName cn) {
        of(c).edit().putString("normal_home", cn.flattenToString()).apply();
    }

    static boolean hasLock(Context c) {
        return !of(c).getString("hash", "").isEmpty();
    }

    /** True for a password, false for a PIN. */
    static boolean usesPassword(Context c) {
        return of(c).getBoolean("password", false);
    }

    static void setLock(Context c, String secret, boolean password) {
        String salt = Lock.newSalt();
        of(c).edit().putString("salt", salt).putString("hash", Lock.hash(secret, salt)).putBoolean("password", password)
                .putInt("wrong", 0).putLong("wait_until", 0).apply();
    }

    /** Checks a try, counting wrong ones; false also while a wait after wrong tries runs. */
    static boolean check(Context c, String attempt) {
        SharedPreferences p = of(c);
        if (System.currentTimeMillis() < p.getLong("wait_until", 0)) return false;
        if (Lock.matches(attempt, p.getString("salt", null), p.getString("hash", null))) {
            p.edit().putInt("wrong", 0).putLong("wait_until", 0).apply();
            return true;
        }
        int wrong = p.getInt("wrong", 0) + 1;
        p.edit().putInt("wrong", wrong).putLong("wait_until", System.currentTimeMillis() + Lock.waitAfter(wrong)).apply();
        return false;
    }

    /** Milliseconds until another try is accepted, 0 when one is. */
    static long waitLeft(Context c) {
        return Math.max(0, of(c).getLong("wait_until", 0) - System.currentTimeMillis());
    }
}
