package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.Collections;
import java.util.Map;

/**
 * Maps the remote's Input button to the system input switcher, from any app. Remotes such as the Chromecast voice
 * remote send KEYCODE_TV for it, others (KTC and Kalley Realtek Google TV remotes) KEYCODE_TV_INPUT; on Google TV
 * the stock launcher handles both, so with the stock launcher disabled the button does nothing. Buttons listed in
 * input_key_keycodes (config.xml or a theme) open the input switcher the same way, and app_keys start an app (a
 * YouTube button that only the stock launcher answered). Every other key passes through untouched.
 * Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or -InputKeyService on Windows) enables it.
 */
public class InputKeyService extends AccessibilityService {

    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private int[] inputKeycodes = new int[0];
    private Config config;
    private String sourcePanelPackage = "";
    private long lastInputKeyLaunch;
    private boolean sourcePanelOpen;
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();
    private Map<Integer, String> holdAppKeys = Collections.emptyMap();
    private Map<Integer, String> holdAppScancodes = Collections.emptyMap();
    /** app_keys "front:" entries, chosen the way HomeKeyService chooses them (RemoteKeys.tap). */
    private Map<Integer, String> frontAppKeys = Collections.emptyMap();
    private Map<Integer, String> frontAppScancodes = Collections.emptyMap();
    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private boolean repairRecord;
    /** consume_volume_keys: Volume Up/Down go to VolumeKeys instead of on to the firmware. */
    private boolean consumeVolumeKeys;
    /** Stops key filtering while a game controller is connected or after a volume change; null unless gamepad_passthrough or volume_pauses_filter, and key_filter. */
    private GamepadPassthrough gamepads;
    /** The bound helper, for global actions Home's own key path asks for (RemoteKeys). */
    static InputKeyService running;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        // From config.xml or a theme's config.json; theme.sh rebinds this service after a push.
        config = Config.load(this);
        ComponentName sourcePanel = ComponentName.unflattenFromString(config.sourcePanelService);
        sourcePanelPackage = sourcePanel == null ? "" : sourcePanel.getPackageName();
        // Subscribe only for a configured vendor panel. Keep this key-only helper silent otherwise.
        if (!sourcePanelPackage.isEmpty()) {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
                // setServiceInfo() can drop the XML key-filter flag; preserve the configured choice.
                if (config.keyFilter) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
            }
        }
        // restore_key_services: note that this helper runs, so Home can put it back after a force stop.
        repairRecord = config.restoreKeyServices;
        if (repairRecord) ServiceRepair.connected(this, getClass());
        inputKeycodes = config.inputKeycodes;
        consumeVolumeKeys = config.consumeVolumeKeys;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
        RemoteKeys.applyKeyFilter(this, config.keyFilter);
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys);
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys);
        frontAppKeys = RemoteKeys.parseFrontAppKeys(config.appKeys);
        frontAppScancodes = RemoteKeys.parseFrontAppScancodes(config.appKeys);
        // gamepad_passthrough and volume_pauses_filter, off by default.
        if (gamepads == null) gamepads = GamepadPassthrough.start(this, config);
    }

    private boolean isInputKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_TV_INPUT) return true;
        for (int code : inputKeycodes) if (keyCode == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (consumeVolumeKeys && VolumeKeys.handle(this, event)) return true;
        // A scancode entry is more specific than any keycode, so it goes first.
        if (RemoteKeys.handle(this, event, RemoteKeys.tap(appScancodes.get(event.getScanCode()),
                frontAppScancodes.get(event.getScanCode())),
                holdAppScancodes.get(event.getScanCode()))) return true;
        if (RemoteKeys.handle(this, event, RemoteKeys.tap(appKeys.get(event.getKeyCode()),
                frontAppKeys.get(event.getKeyCode())),
                holdAppKeys.get(event.getKeyCode()))) return true;
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            sourcePanelOpen = false;
            lastInputKeyLaunch = 0;
            return false;
        }
        if (!isInputKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            long now = SystemClock.elapsedRealtime();
            if (sourcePanelOpen) {
                sourcePanelOpen = false;
                lastInputKeyLaunch = 0;
                performGlobalAction(GLOBAL_ACTION_BACK);
                return true;
            }
            if (lastInputKeyLaunch != 0 && now - lastInputKeyLaunch < 800) return true;
            lastInputKeyLaunch = now;
            try {
                // Start the vendor picker directly so the key doesn't wait on Home's menu animation.
                // The short guard prevents a quick second press from opening/toggling the panel twice.
                boolean opened = !config.sourcePanelService.isEmpty()
                        && MainActivity.openSourcePanel(this, config.sourcePanelService,
                        config.sourcePanelAction, config.sourcePanelExtra);
                if (opened) sourcePanelOpen = true;
                if (!opened) {
                    startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
        return true;
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    @Override
    public boolean onUnbind(Intent intent) {
        RemoteKeys.release(this);
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        RemoteKeys.release(this);
        if (gamepads != null) gamepads.stop();
        if (consumeVolumeKeys) VolumeKeys.stop();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence packageName = event.getPackageName();
        sourcePanelOpen = !sourcePanelPackage.isEmpty()
                && packageName != null && sourcePanelPackage.contentEquals(packageName);
    }

    @Override
    public void onInterrupt() {
    }
}
