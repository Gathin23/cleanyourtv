package com.mauro.tv.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * A restart drops the alarm that ends a USB debugging window but keeps the setting, so debugging would stay on.
 * At boot this closes a window that has run out, or sets the alarm again for the time that is left.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        AdbOffReceiver.rearm(context);
    }
}
