package com.majnioui.tv.prayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

public class PrayerReceiver extends BroadcastReceiver {
    private static final String TAG = "PrayerReceiver";
    private static final String CHANNEL_ID = "prayer_reminder";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!context.getResources().getBoolean(R.bool.reminders)) {
            Log.i(TAG, "Reminders are disabled in config.xml");
            PrayerScheduler.cancel(context);
            return;
        }

        String action = intent != null ? intent.getAction() : null;
        Log.i(TAG, "onReceive: " + action);

        if (PrayerScheduler.ACTION_REFRESH_ALARM.equals(action)) {
            PrayerScheduler.fetchTimingsAndSchedule(context);
            return;
        }

        if (PrayerScheduler.ACTION_PRAYER_ALARM.equals(action)) {
            String name = intent.getStringExtra(PrayerScheduler.EXTRA_PRAYER_NAME);
            String time = intent.getStringExtra(PrayerScheduler.EXTRA_PRAYER_TIME);
            String place = intent.getStringExtra(PrayerScheduler.EXTRA_PLACE);
            if (name == null) name = "Fajr";
            if (time == null) time = "";
            if (place == null) place = context.getString(R.string.place);

            int idx = PrayerScheduler.getPrayerIndex(name);
            String localizedName = idx >= 0 ? context.getString(PrayerScheduler.NAME_RES[idx]) : name;

            // 1. Post High-Priority Notification (handles sound and tray indicator with fixed ID)
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel ch = new NotificationChannel(
                            CHANNEL_ID,
                            context.getString(R.string.app_name),
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    ch.setDescription(context.getString(R.string.app_name));
                    ch.enableLights(true);
                    ch.setLightColor(Color.parseColor("#2BB673"));

                    Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                            .build();
                    ch.setSound(soundUri, audioAttributes);
                    nm.createNotificationChannel(ch);
                }

                Intent mainIntent = new Intent(context, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent contentPi = PendingIntent.getActivity(
                        context,
                        0,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                );

                Notification.Builder nb;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    nb = new Notification.Builder(context, CHANNEL_ID);
                } else {
                    nb = new Notification.Builder(context);
                }

                String title = context.getString(R.string.notification_title, localizedName);
                String body = context.getString(R.string.notification_body, localizedName, time, place);

                nb.setContentTitle(title)
                        .setContentText(body)
                        .setSmallIcon(R.drawable.icon)
                        .setAutoCancel(true)
                        .setContentIntent(contentPi)
                        .setPriority(Notification.PRIORITY_MAX)
                        .setCategory(Notification.CATEGORY_ALARM)
                        .setColor(Color.parseColor("#2BB673"));

                nm.notify(NOTIFICATION_ID, nb.build());
            }

            // 2. Launch floating alert banner on screen (single launch path)
            try {
                Intent popup = new Intent(context, PrayerAlertActivity.class);
                popup.putExtra(PrayerScheduler.EXTRA_PRAYER_NAME, name);
                popup.putExtra(PrayerScheduler.EXTRA_PRAYER_TIME, time);
                popup.putExtra(PrayerScheduler.EXTRA_PLACE, place);
                popup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(popup);
            } catch (Exception e) {
                Log.w(TAG, "Launch alert activity error: " + e);
            }

            // 3. Schedule next prayer
            PrayerScheduler.scheduleNext(context);
        }
    }
}
