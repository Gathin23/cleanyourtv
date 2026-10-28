package com.gathin.artframe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After a boot or an update, starts the wake watcher if "Wake into art" is on; a TV switched on from cold with the
 * art button also opens art straight away.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        WakeService.sync(context);
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) WakeService.maybeOpen(context);
    }
}
