package com.gathin.btconnect;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;

/**
 * Connecting and disconnecting a paired speaker. Android's public API has neither, so this calls the audio
 * profile's own connect and disconnect, which a plain app may use with the Nearby devices permission.
 */
final class Link {
    private Link() { }

    /** A speaker or headphones: anything the TV can send sound to. */
    static boolean isAudio(BluetoothClass c) {
        if (c == null) return true;   // unknown class: show it rather than hide a speaker
        return c.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO
                || c.hasService(BluetoothClass.Service.AUDIO);
    }

    /** True when Android accepted the request; the connection itself changes a moment later. */
    static boolean set(BluetoothA2dp a2dp, BluetoothDevice device, boolean connect) {
        try {
            return (Boolean) BluetoothA2dp.class.getMethod(connect ? "connect" : "disconnect", BluetoothDevice.class)
                    .invoke(a2dp, device);
        } catch (ReflectiveOperationException | SecurityException refused) {
            return false;
        }
    }
}
