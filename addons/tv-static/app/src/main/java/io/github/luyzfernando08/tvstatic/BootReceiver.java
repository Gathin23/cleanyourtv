package io.github.luyzfernando08.tvstatic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a boot or an update: start watching the screen, and after a boot also play the effect once. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent watch = new Intent(context, WatchService.class);
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) watch.putExtra(WatchService.EXTRA_PLAY, true);
        context.startForegroundService(watch);
    }
}
