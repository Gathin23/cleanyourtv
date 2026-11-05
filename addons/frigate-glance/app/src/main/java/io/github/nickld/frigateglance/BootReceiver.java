package io.github.nickld.frigateglance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reconnects after a reboot. TCL firmware also needs the APP_AUTO_START app-op for this to run. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) GlanceService.start(context);
    }
}
