package com.norma.tvlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Collections;
import java.util.Map;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes). Android TV 9 routes that key only to the system-image launcher, so once
 * the stock launcher is disabled the button does nothing. Every other key passes through untouched.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.norma.tvlauncher/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private int[] inputKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same map InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        inputKeycodes = config.inputKeycodes;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
    }

    private boolean isSettingsKey(KeyEvent event) {
        int kc = event.getKeyCode();
        if (kc == KeyEvent.KEYCODE_SETTINGS || kc == 4077 || kc == 4020 || kc == 4056 || kc == 83 || kc == KeyEvent.KEYCODE_MENU) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    private boolean isInputKey(KeyEvent event) {
        int kc = event.getKeyCode();
        if (kc == KeyEvent.KEYCODE_TV || kc == KeyEvent.KEYCODE_TV_INPUT || kc == 178 || kc == 170) return true;
        for (int code : inputKeycodes) if (kc == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // A scancode entry is more specific than any keycode, so it goes first.
        String scanApp = appScancodes.get(event.getScanCode());
        if (scanApp != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.launch(this, scanApp);
            return true;
        }
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent toggle = new Intent(MainActivity.ACTION_TOGGLE_SETTINGS).setPackage(getPackageName());
                sendOrderedBroadcast(toggle, null, new android.content.BroadcastReceiver() {
                    @Override
                    public void onReceive(android.content.Context context, Intent intent) {
                        if (getResultCode() != android.app.Activity.RESULT_OK) {
                            Intent open = settingsPanel ? new Intent(HomeKeyService.this, QuickSettingsActivity.class)
                                    : new Intent(Settings.ACTION_SETTINGS);
                            startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        }
                    }
                }, null, android.app.Activity.RESULT_CANCELED, null, null);
            }
            return true;
        }
        if (isInputKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent toggle = new Intent(MainActivity.ACTION_TOGGLE_INPUTS).setPackage(getPackageName());
                sendOrderedBroadcast(toggle, null, new android.content.BroadcastReceiver() {
                    @Override
                    public void onReceive(android.content.Context context, Intent intent) {
                        if (getResultCode() != android.app.Activity.RESULT_OK) {
                            startActivity(new Intent(HomeKeyService.this, MainActivity.class)
                                    .setAction(MainActivity.ACTION_TOGGLE_INPUTS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        }
                    }
                }, null, android.app.Activity.RESULT_CANCELED, null, null);
            }
            return true;
        }
        String app = appKeys.get(event.getKeyCode());
        if (app != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) RemoteKeys.launch(this, app);
            return true;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
