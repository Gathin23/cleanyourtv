package io.github.justinnewman.bluetoothtoggle;

import android.bluetooth.BluetoothAdapter;

/** What the panel says and shows for each adapter state, and when an app may switch Bluetooth itself. */
final class States {
    /** No adapter at all (the TV has no Bluetooth). */
    static final int NONE = -1;

    private States() { }

    static int words(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_ON: return R.string.state_on;
            case BluetoothAdapter.STATE_TURNING_ON: return R.string.state_turning_on;
            case BluetoothAdapter.STATE_TURNING_OFF: return R.string.state_turning_off;
            case BluetoothAdapter.STATE_OFF: return R.string.state_off;
            default: return R.string.state_none;
        }
    }

    static int icon(int state) {
        return state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_TURNING_ON
                ? R.drawable.ic_on : R.drawable.ic_off;
    }

    /** The button's action: Turn off while on (or turning on), Turn on otherwise. */
    static int action(int state) {
        return state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_TURNING_ON
                ? R.string.turn_off : R.string.turn_on;
    }

    /** On or off, not on the way: only then is a switch sent. */
    static boolean settled(int state) {
        return state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_OFF;
    }

    /**
     * BluetoothAdapter.enable and disable work for ordinary apps up to Android 12L (API 32). From Android 13 they
     * return false: turning on goes through Android's own prompt then, and turning off is left to Settings.
     */
    static boolean appsMaySwitch(int sdk) {
        return sdk <= 32;
    }
}
