package com.gathin.tvhome;

import android.media.tv.TvInputManager;

/** Which Inputs menu rows inputs_connected_only folds behind "More inputs". */
final class InputFold {
    private InputFold() {}

    /**
     * state is the row's TvInputManager input state, or -1 when it has none to show (the AV jacks, or an HDMI state
     * that cannot be trusted). An AV jack has no hotplug detection, so it always folds. An HDMI port or CEC device
     * stays only while it reports INPUT_STATE_CONNECTED: a port with nothing on it can report
     * CONNECTED_STANDBY (TCL), and so does a device asleep in standby, so both fold. A state of -1 keeps the row.
     */
    static boolean folds(boolean hdmi, int state) {
        if (!hdmi) return true;
        return state != -1 && state != TvInputManager.INPUT_STATE_CONNECTED;
    }
}
