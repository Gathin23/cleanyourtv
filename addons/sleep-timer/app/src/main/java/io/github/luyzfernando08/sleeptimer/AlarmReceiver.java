package io.github.luyzfernando08.sleeptimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/** The timer's two alarms: a minute early, the warning; just after the end, sleep if nobody answered. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Timer.ACTION_TEST.equals(intent.getAction())) {
            // From adb only (the receiver needs WRITE_SECURE_SETTINGS): a timer of a few seconds, to try it out.
            if (intent.getBooleanExtra("wake", false)) Schedule.testWake(context, intent.getIntExtra("seconds", 60));
            else Timer.startSeconds(context, intent.getIntExtra("seconds", 70));
            return;
        }
        if (Schedule.ACTION_TEST_WAKE.equals(intent.getAction())) {
            Schedule.wakeScreen(context,goAsync()::finish);
            return;
        }
        if (Schedule.ACTION_EVENT.equals(intent.getAction())) {
            Schedule.fire(context,goAsync()::finish);
            return;
        }
        if (Schedule.ACTION_BEDTIME.equals(intent.getAction())) {
            Schedule.reschedule(context);
            return;
        }
        long end = Timer.due(context);
        if (end == 0) return;                                   // cancelled, or pushed back since
        if (System.currentTimeMillis() > end + 5_000) {         // late: a fresh minute of warning, not a sudden sleep
            if (context.getSystemService(PowerManager.class).isInteractive()) Timer.startSeconds(context, 60);
            else Timer.cancel(context);
            return;
        }
        if (Timer.ACTION_WARN.equals(intent.getAction())) {
            context.startActivity(new Intent(context, WarningActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (Timer.ACTION_SLEEP.equals(intent.getAction()) && System.currentTimeMillis() >= end) {
            Timer.sleepNow(context);
        }
    }
}
