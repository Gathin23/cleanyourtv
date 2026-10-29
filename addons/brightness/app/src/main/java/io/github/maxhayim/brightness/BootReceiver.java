package io.github.maxhayim.brightness;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Puts the layer and the schedule back after a reboot or an update of this app, if dimming was on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            DimService.start(context);
        }
    }
}
