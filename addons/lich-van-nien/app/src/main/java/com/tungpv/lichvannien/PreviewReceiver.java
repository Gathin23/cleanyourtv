package com.tungpv.lichvannien;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Shows another day's tile until the next update, for testing over adb:
 * am broadcast -n com.tungpv.lichvannien/.PreviewReceiver --ei preview_days 1
 * The manifest guards it with DUMP, which apps cannot hold.
 */
public class PreviewReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        DayUpdater.update(context, intent.getIntExtra("preview_days", 0));
    }
}
