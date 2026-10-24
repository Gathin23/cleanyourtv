package io.github.callmejm.stayawake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After boot or an update, bring the watcher back only if the owner left it on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Prefs.enabled(context)) StayService.start(context);
    }
}
