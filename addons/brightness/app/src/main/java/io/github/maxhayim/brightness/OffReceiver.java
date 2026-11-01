package io.github.maxhayim.brightness;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Normal screen from outside the app, for when the picture is too dark to find the way:
 * am broadcast -a io.github.maxhayim.brightness.OFF -n io.github.maxhayim.brightness/.OffReceiver
 * It only ever switches dimming off; the running service sees the change and removes the layer.
 */
public class OffReceiver extends BroadcastReceiver {
    static final String ACTION = "io.github.maxhayim.brightness.OFF";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION.equals(intent.getAction())) Prefs.switchOff(context);
    }
}
