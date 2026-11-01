package io.github.luyzfernando08.sleeptimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The timer's two alarms: a minute early, the warning; just after the end, sleep if nobody answered. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Timer.ACTION_TEST.equals(intent.getAction())) {
            // From adb only (the receiver needs WRITE_SECURE_SETTINGS): a timer of a few seconds, to try it out.
            Timer.startSeconds(context, intent.getIntExtra("seconds", 70));
            return;
        }
        long end = Timer.end(context);
        if (end == 0) return;                                   // cancelled, or pushed back since
        if (Timer.ACTION_WARN.equals(intent.getAction())) {
            context.startActivity(new Intent(context, WarningActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (Timer.ACTION_SLEEP.equals(intent.getAction()) && System.currentTimeMillis() >= end) {
            Timer.sleepNow(context);
        }
    }
}
