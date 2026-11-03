package io.github.sumyabazar1.weatherline;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** A periodic job is dropped by a power cut on some firmware: put it back at boot. */
public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        WeatherRefreshJob.schedule(context);
    }
}
