package com.gathin.tvhome;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Android takes an app's accessibility services off the enabled list when the app is force-stopped (Settings, a
 * cleaner app, or am force-stop), and the remote's Home, Input and app buttons then stop working until the helper
 * is turned on again over ADB. With restore_key_services on, a running helper records itself here and drops the
 * record when Android unbinds it (the user turned it off); a force stop kills the process before that, so the
 * record stays. Home starting again puts recorded helpers back on the list, when the installer granted
 * WRITE_SECURE_SETTINGS. With the flag off nothing is recorded and nothing is restored.
 */
final class ServiceRepair {
    private static final String PREFS = "accessibility_helpers";

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
        // Only while this launcher is still the TV's Home. Once the stock desktop is enabled again this process
        // can still be started — by a leftover mapping, by an app, by the switcher itself — and putting the key
        // filter back then would leave it swallowing buttons under the other desktop: a mapping that belongs to
        // this Home would keep firing there. The record is kept, so a real force stop under this Home still
        // repairs itself; under the other Home it just stays dormant until the switch back.
        if (!homeIsOurs(context)) return;
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
            if (!missing) return;
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
            // Ask again before every write, not once at the top: the cycle sleeps a second between steps and Home
            // can change in that gap, and the list comparisons only notice edits to the list itself — switching
            // Home does not touch it — so an answer taken at the top would still put the helper back under the
            // other desktop. Nothing already written is rolled back; the last check before the global enable is
            // the one that keeps a completed cycle from being switched on for somebody else's Home.
            final Context app = context.getApplicationContext();
            new Thread(() -> {
                try {
                    if (!homeIsOurs(app)) return;
                    if (!replaceList(cr, list, with)) return;
                    Thread.sleep(1000);
                    if (!homeIsOurs(app)) return;
                    if (!replaceList(cr, with, without)) return;
                    Thread.sleep(1000);
                    if (!homeIsOurs(app)) return;
                    if (!replaceList(cr, without, with)) return;
                    if (!homeIsOurs(app)) return;
                    Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                } catch (RuntimeException | InterruptedException ignored) {
                    // The setting is the system's; a refusal leaves the helper off, as before.
                }
            }, "ServiceRepair").start();
        } catch (RuntimeException ignored) {
            // The setting is the system's; a refusal leaves the helper off, as before.
        }
    }

    /** Writes the enabled list only while it still reads {@code expected}; an absent list counts as empty. */
    private static boolean replaceList(ContentResolver cr, String expected, String updated) {
        String now = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return Objects.equals(now == null ? "" : now, expected == null ? "" : expected)
                && Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated);
    }

    /**
     * True while this launcher is the TV's Home, i.e. while a key filter of ours belongs on the enabled list.
     * Nothing else answering HOME is the ordinary case and repairs as before. When something else does answer it,
     * which Home Android actually resolves decides: the stock desktop after a switch back, but also Settings'
     * FallbackHome and the factory-test launchers some TVs leave enabled, and those would otherwise keep the
     * repair from ever running. A disabled Home is not returned by the query, so a switch back is seen here.
     * Anything unexpected reads as "not ours": switching a system-wide key filter on inside somebody else's Home
     * is worse than leaving it off until the next switch back.
     */
    private static boolean homeIsOurs(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            String me = context.getPackageName();
            for (ResolveInfo info : pm.queryIntentActivities(home, 0)) {
                if (info.activityInfo == null || me.equals(info.activityInfo.packageName)) continue;
                ResolveInfo resolved = pm.resolveActivity(home, 0);
                return resolved != null && resolved.activityInfo != null
                        && me.equals(resolved.activityInfo.packageName);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
