package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.hardware.input.InputManager;
import android.view.InputDevice;

/**
 * Stops HomeKeyService and InputKeyService filtering keys while a game controller is connected. With key filtering on,
 * Android routes every key through the accessibility layer and re-injects the ones the service passes on, and games
 * (Steam Link with an Xbox controller, on a TCL G07) stop seeing the controller's buttons as the controller's. The
 * remote buttons the services handle do nothing until the controller disconnects; filtering then comes back.
 * Off by default (gamepad_passthrough). Started only with key_filter on too: with it off the services never filter,
 * and this must not turn filtering back on.
 */
final class GamepadPassthrough implements InputManager.InputDeviceListener {
    private final AccessibilityService service;
    private final InputManager input;

    GamepadPassthrough(AccessibilityService service) {
        this.service = service;
        this.input = (InputManager) service.getSystemService(Context.INPUT_SERVICE);
    }

    void start() {
        input.registerInputDeviceListener(this, null);
        update();
    }

    void stop() {
        input.unregisterInputDeviceListener(this);
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

    private void update() {
        AccessibilityServiceInfo info = service.getServiceInfo();
        if (info == null) return;
        boolean filter = !gamepadConnected();
        boolean filtering = (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
        if (filter == filtering) return;
        info.flags ^= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        service.setServiceInfo(info);
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        update();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        update();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        update();
    }
}
