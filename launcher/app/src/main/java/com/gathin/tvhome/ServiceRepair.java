package com.gathin.tvhome;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Android takes an app's accessibility services off the enabled list when the app is force-stopped (Settings, a
 * cleaner app, or am force-stop), and the remote's Home, Input and app buttons then stop working until the helper
 * is turned on again over ADB. With restore_key_services on, a running helper records itself here and drops the
 * record when Android unbinds it (the user turned it off); a force stop kills the process before that, so the
 * record stays. Home starting again puts recorded helpers back on the list, when the installer granted
 * WRITE_SECURE_SETTINGS. A helper that is still on the list but not bound (a process kill that left it crashed,
 * or accessibility_enabled reset to 0) gets the same cycle. With the flag off nothing is recorded and nothing is
 * restored.
 */
final class ServiceRepair {
    private static final String PREFS = "accessibility_helpers";
    /** How long a listed helper may still be binding (Home starts early in boot) before it counts as stuck. */
    private static final long SETTLE_MS = 3000;
    /** Cycles tried before giving up; each one doubles its pauses (1 s, 2 s, 4 s). */
    private static final int CYCLE_ATTEMPTS = 3;
    /** How long a cycle's helper gets to bind before the next, slower cycle. */
    private static final long BIND_WAIT_MS = 5000;

    private ServiceRepair() {
    }

    static void connected(Context context, Class<?> service) {
        prefs(context).edit().putBoolean(service.getName(), true).apply();
    }

    static void disconnected(Context context, Class<?> service) {
        prefs(context).edit().remove(service.getName()).apply();
    }

    static void restore(Context context) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            ContentResolver cr = context.getContentResolver();
            String list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            List<ComponentName> enabled = new ArrayList<>();
            if (list != null) {
                for (String entry : list.split(":")) {
                    ComponentName name = ComponentName.unflattenFromString(entry);
                    if (name != null) enabled.add(name);
                }
            }
            List<ComponentName> recorded = new ArrayList<>();
            for (String service : prefs(context).getAll().keySet()) {
                recorded.add(new ComponentName(context.getPackageName(), service));
            }
            if (recorded.isEmpty()) return;
            // The list without our recorded helpers, then with all of them.
            StringBuilder others = new StringBuilder();
            for (ComponentName name : enabled) {
                if (recorded.contains(name)) continue;
                if (others.length() > 0) others.append(':');
                others.append(name.flattenToShortString());
            }
            StringBuilder updated = new StringBuilder(others);
            boolean missing = false;
            for (ComponentName name : recorded) {
                if (!enabled.contains(name)) missing = true;
                if (updated.length() > 0) updated.append(':');
                updated.append(name.flattenToShortString());
            }
            // A helper can also be listed and not bound: Hisense songshan's standby kills the process with
            // another app in front, which is not a force stop, so the helper stays listed and lands on
            // "Crashed services"; a TCL G08 reboot left the list complete with accessibility_enabled at 0
            // (#1047). The same cycle and flag write repair both, after SETTLE_MS in case it is still binding.
            final boolean listedOnly = !missing;
            final Context app = context.getApplicationContext();
            if (listedOnly && !listedButUnbound(context, enabled, recorded)) return;
            // A killed helper also lands on Android's "Crashed services" list (dumpsys accessibility), and a
            // crashed service is never bound again, so putting it back on the list alone binds nothing and the
            // switch falls back to 0 (Hisense songshan, Android 11, whose standby kills third-party apps). Android
            // drops the crashed mark when it sees the helper leave the enabled list, so add, remove, add: after a
            // force stop the list is already empty, and a removal that changes nothing is not seen. Off the main
            // thread, so Home does not wait for the pauses. Each write first checks the list still reads what the
            // previous step left, and the cycle stops otherwise: a service turned on or off during a pause stays
            // that way instead of being overwritten by a snapshot.
            final String without = others.toString();
            final String with = updated.toString();
            new Thread(() -> {
                try {
                    if (listedOnly) {
                        Thread.sleep(SETTLE_MS);
                        if (!listedButUnbound(app, enabled, recorded)) return;
                    }
                    if (!replaceList(cr, list, with)) return;
                    // Android only drops the crashed mark if it reads the list while the helper is off it. Just
                    // after a wake (Hisense songshan) it can read the list late, find it unchanged and keep the
                    // mark, so a cycle whose helper is still not bound is repeated with longer pauses.
                    for (int attempt = 0; attempt < CYCLE_ATTEMPTS; attempt++) {
                        long pause = 1000L << attempt;
                        Thread.sleep(pause);
                        if (!replaceList(cr, with, without)) return;
                        Thread.sleep(pause);
                        if (!replaceList(cr, without, with)) return;
                        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                        if (boundWithin(app, recorded, BIND_WAIT_MS)) return;
                    }
                } catch (RuntimeException | InterruptedException ignored) {
                    // The setting is the system's; a refusal leaves the helper off, as before.
                }
            }, "ServiceRepair").start();
        } catch (RuntimeException ignored) {
            // The setting is the system's; a refusal leaves the helper off, as before.
        }
    }

    /** Whether a recorded helper of ours is on the enabled list but not among the services Android has bound. */
    private static boolean listedButUnbound(Context context, List<ComponentName> enabled, List<ComponentName> recorded) {
        AccessibilityManager am = context.getSystemService(AccessibilityManager.class);
        if (am == null) return false;
        List<String> bound = new ArrayList<>();
        for (AccessibilityServiceInfo info : am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
            if (context.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)) {
                bound.add(info.getResolveInfo().serviceInfo.name);
            }
        }
        String pkg = context.getPackageName();
        return listedButUnbound(classNames(enabled, pkg), classNames(recorded, pkg), bound);
    }

    /** Waits up to {@code ms} for every recorded helper, now back on the list, to be bound. */
    private static boolean boundWithin(Context context, List<ComponentName> recorded, long ms)
            throws InterruptedException {
        for (long waited = 0; waited < ms; waited += 500) {
            Thread.sleep(500);
            if (!listedButUnbound(context, recorded, recorded)) return true;
        }
        return false;
    }

    /** Pure part of the check, by class name: true when some recorded helper is listed and not bound. */
    static boolean listedButUnbound(Collection<String> listed, Collection<String> recorded, Collection<String> bound) {
        for (String name : recorded) {
            if (listed.contains(name) && !bound.contains(name)) return true;
        }
        return false;
    }

    private static List<String> classNames(List<ComponentName> names, String pkg) {
        List<String> out = new ArrayList<>();
        for (ComponentName name : names) {
            if (pkg.equals(name.getPackageName())) out.add(name.getClassName());
        }
        return out;
    }

    /** Writes the enabled list only while it still reads {@code expected}; an absent list counts as empty. */
    private static boolean replaceList(ContentResolver cr, String expected, String updated) {
        String now = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return Objects.equals(now == null ? "" : now, expected == null ? "" : expected)
                && Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
