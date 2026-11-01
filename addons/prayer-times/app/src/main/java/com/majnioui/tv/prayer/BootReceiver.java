package com.majnioui.tv.prayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "PrayerBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!context.getResources().getBoolean(R.bool.reminders)) {
            Log.i(TAG, "Reminders disabled in config.xml, ignoring boot");
            return;
        }
        String action = intent != null ? intent.getAction() : "";
        Log.i(TAG, "onReceive boot/update action: " + action);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            PrayerScheduler.scheduleNext(context);
        }
    }
}
