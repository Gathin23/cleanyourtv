package com.gathin.tvhome;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.os.UserManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps {@link HomeKeyService} on the accessibility list, the way a China-market TV needs it.
 *
 * TCL and Xiaomi start their own launcher at boot and force-stop a third-party one, which also
 * drops its accessibility service. {@link GuardRevival} is bound again by the system afterwards
 * and calls {@link #ensureHomeKey}. With {@code guard_off_in_standby} the service removes itself
 * while the screen is off: on a TCL tc8000 the power key never woke the set while an accessibility
 * service of this app stayed bound.
 */
final class GuardKeep {
    static final String ACTION = "com.gathin.tvhome.GUARD";
    private static final String PREFS = "guard";
    private static final String KEY_OFF = "off_in_standby";
    private static final String KEY_SWITCHED_OFF = "home_key_switched_off";

    private static BroadcastReceiver screenOn;

    private GuardKeep() {
    }

    static boolean offInStandby(Context context) {
        UserManager users = context.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OFF, false);
    }

    static void setOffInStandby(Context context, boolean off) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_OFF, off).commit();
    }

    /** HomeKeyService was unbound by the user, not by a standby step-out: the guard leaves it off until it connects. */
    static void setSwitchedOff(Context context, boolean off) {
        UserManager users = context.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SWITCHED_OFF, off).apply();
    }

    /** True when Home resolves to another app: not this launcher, Android's chooser or the stock_home_redirect one. */
    static boolean otherHome(String resolved, String mine, String stockRedirect) {
        return resolved != null && !resolved.equals(mine) && !resolved.equals("android") && !resolved.equals(stockRedirect);
    }

    /** No repair and no re-add while another app is Home; stock_home_redirect keeps the stock launcher from counting. */
    static boolean mayRepair(String resolved, String mine, String stockRedirect) {
        return !otherHome(resolved, mine, stockRedirect);
    }

    /** The HomeKeyService re-add: standby opt-in on, not switched off by the user, and Home is not another app. */
    static boolean mayAdd(boolean off, boolean switchedOff, String resolved, String mine, String stockRedirect) {
        return off && !switchedOff && mayRepair(resolved, mine, stockRedirect);
    }

    /**
     * Puts HomeKeyService back on the enabled list when standby exit is opted in, unless another app is Home or the
     * user switched it off, and with restore_key_services repairs the recorded helpers as Home's start does: on TCL
     * and Xiaomi the revival may be the only start after a reboot or a kill.
     */
    static void ensureHomeKey(Context context) {
        // The helper may already be bound. Arming only inside onServiceConnected misses an opt-in
        // that arrives after that, so the first screen-off does nothing (Philips 65PUS8102).
        HomeKeyService.applyStandbyOptIn();
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return;
        boolean off = offInStandby(context);
        PowerManager power = context.getSystemService(PowerManager.class);
        // Still off: binding the helper again would sit on the power key. Wait for the screen.
        if (off && power != null && !power.isInteractive()) {
            watchScreenOn(context.getApplicationContext());
            return;
        }
        UserManager users = context.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) return;
        Config config = Config.load(context);
        ResolveInfo home = context.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        String resolved = home == null ? null : home.activityInfo.packageName;
        if (mayAdd(off, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SWITCHED_OFF, false),
                resolved, context.getPackageName(), config.stockHomeRedirect)) addHomeKey(context);
        if (config.restoreKeyServices && mayRepair(resolved, context.getPackageName(), config.stockHomeRedirect)) {
            repairRecorded(context);
        }
    }

    /** restore_key_services: recorded helpers off the list come back (ServiceRepair); a listed one Android has not
     *  bound gets the accessibility switch back on, which a TCL reboot can leave at 0 (#3367). */
    private static void repairRecorded(Context context) {
        ServiceRepair.restore(context);
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        String list = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (manager == null || list == null) return;
        List<String> bound = new ArrayList<>();
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            bound.add(info.getId());
        }
        for (String entry : list.split(":")) {
            ComponentName name = ComponentName.unflattenFromString(entry);
            if (name == null || !name.getPackageName().equals(context.getPackageName())
                    || bound.contains(name.flattenToShortString())) continue;
            try {
                Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            } catch (RuntimeException ignored) {
                // The settings are the system's; a refusal leaves the helper as it was.
            }
            return;
        }
    }

    private static void addHomeKey(Context context) {
        try {
            ComponentName mine = new ComponentName(context, HomeKeyService.class);
            String list = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            List<String> entries = new ArrayList<>();
            boolean found = false;
            if (list != null && !list.equals("null")) {
                for (String entry : list.split(":")) {
                    if (entry.isEmpty()) continue;
                    ComponentName name = ComponentName.unflattenFromString(entry);
                    if (mine.equals(name)) found = true;
                    entries.add(entry);
                }
            }
            if (!found) {
                entries.add(mine.flattenToShortString());
                Settings.Secure.putString(context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, String.join(":", entries));
            }
            Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (RuntimeException ignored) {
            // The settings are the system's; a refusal leaves the helper as it was.
        }
    }

    /** Drops the service from the list and puts it back on the next SCREEN_ON. The process has to stay up. */
    static void stepOut(AccessibilityService service) {
        watchScreenOn(service.getApplicationContext());
        service.disableSelf();
    }

    private static void watchScreenOn(Context app) {
        if (screenOn != null) return;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (screenOn != this) return;
                screenOn = null;
                try {
                    app.unregisterReceiver(this);
                } catch (RuntimeException ignored) {
                }
                ensureHomeKey(app);
            }
        };
        screenOn = receiver;
        app.registerReceiver(receiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }
}
