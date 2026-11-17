package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;

/**
 * Stops HomeKeyService and InputKeyService filtering keys while a game controller is connected. With key filtering on,
 * Android routes every key through the accessibility layer and re-injects the ones the service passes on, and games
 * (Steam Link with an Xbox controller, on a TCL G07) stop seeing the controller's buttons as the controller's. The
 * remote buttons the services handle do nothing until the controller disconnects; filtering then comes back.
 * Off by default (gamepad_passthrough).
 *
 * With volume_pauses_filter it also stops filtering for PAUSE_MS after each volume change. On a Xiaomi MOEU0 (Google TV 14) the
 * window policy changes volume once per press while any service filters keys, and a held volume key never repeats;
 * the services never see the volume keys, so consume_volume_keys cannot repeat them either. The first press steps
 * once, a press or hold within PAUSE_MS repeats as usual, and the remote buttons work again once the pause ends. Off by
 * default (volume_pauses_filter).
 *
 * Started only with key_filter on too: with it off the services never filter, and this must not turn filtering back on.
 */
final class GamepadPassthrough extends BroadcastReceiver implements InputManager.InputDeviceListener, Runnable {
    static final long PAUSE_MS = 4000;

    private final AccessibilityService service;
    private final InputManager input;
    private final Config config;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private GamepadPassthrough(AccessibilityService service, Config config) {
        this.service = service;
        this.input = (InputManager) service.getSystemService(Context.INPUT_SERVICE);
        this.config = config;
    }

    /** Null unless a switch that needs it is on, and key_filter: with that off the services never filter keys. */
    static GamepadPassthrough start(AccessibilityService service, Config config) {
        if (!config.keyFilter || !(config.gamepadPassthrough || config.volumePausesFilter)) return null;
        GamepadPassthrough g = new GamepadPassthrough(service, config);
        // Only for gamepads: a device event ends a volume pause early.
        if (config.gamepadPassthrough) g.input.registerInputDeviceListener(g, null);
        if (config.volumePausesFilter) {
            // A protected system broadcast, so Android 14 needs no exported flag.
            service.registerReceiver(g, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        }
        g.run();
        return g;
    }

    void stop() {
        input.unregisterInputDeviceListener(this);
        if (config.volumePausesFilter) service.unregisterReceiver(this);
        handler.removeCallbacks(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        handler.removeCallbacks(this);
        handler.postDelayed(this, PAUSE_MS);
        set(false);
    }

    /**
     * A gamepad has all the SOURCE_GAMEPAD bits and the SOURCE_JOYSTICK ones, as dumpsys input reports them on a TCL
     * G07. Either alone is not enough: the Bluetooth voice remote reports a joystick (BT_RC833_U1: 0x01000311), and
     * the firmware's virtual-search keyboard, which isVirtual() misses, reports a gamepad (0x00000701). An Xbox
     * controller reports both (0x01000511).
     */
    static boolean isGamepad(int sources) {
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                && (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private boolean gamepadConnected() {
        for (int id : input.getInputDeviceIds()) {
            InputDevice device = input.getInputDevice(id);
            if (device != null && !device.isVirtual() && isGamepad(device.getSources())) return true;
        }
        return false;
    }

    /** Filtering as the gamepads want it; also a pause's end. */
    @Override
    public void run() {
        set(!(config.gamepadPassthrough && gamepadConnected()));
    }

    private void set(boolean filter) {
        AccessibilityServiceInfo info = service.getServiceInfo();
        if (info == null) return;
        boolean filtering = (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
        if (filter == filtering) return;
        // Without the flag no more keys come, the UP of a button held right now included.
        if (!filter) {
            RemoteKeys.release(service);
            // consume_volume_keys: the held key's UP will not come either, so end its repeat too.
            VolumeKeys.stop();
        }
        info.flags ^= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        service.setServiceInfo(info);
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        run();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        run();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        run();
    }
}
