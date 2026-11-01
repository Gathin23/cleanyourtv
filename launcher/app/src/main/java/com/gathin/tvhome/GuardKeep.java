package com.gathin.tvhome;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.os.UserManager;
import android.provider.Settings;

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

    /** Puts HomeKeyService back on the enabled list when standby exit is opted in. Other services stay. */
    static void ensureHomeKey(Context context) {
        // The helper may already be bound. Arming only inside onServiceConnected misses an opt-in
        // that arrives after that, so the first screen-off does nothing (Philips 65PUS8102).
        HomeKeyService.applyStandbyOptIn();
        if (!offInStandby(context)) return;
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return;
        PowerManager power = context.getSystemService(PowerManager.class);
        // Still off: binding the helper again would sit on the power key. Wait for the screen.
        if (power != null && !power.isInteractive()) {
            watchScreenOn(context.getApplicationContext());
            return;
        }
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
