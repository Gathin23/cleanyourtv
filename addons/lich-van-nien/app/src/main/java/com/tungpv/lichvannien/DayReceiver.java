package com.tungpv.lichvannien;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Boot, clock changes or the app's own midnight alarm: bring the tile up to today. */
public class DayReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        DayUpdater.update(context);
    }
}
