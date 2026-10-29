package com.majnioui.tv.prayer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class PrayerScheduler {
    private static final String TAG = "PrayerScheduler";
    public static final String ACTION_PRAYER_ALARM = "com.majnioui.tv.prayer.ACTION_PRAYER_ALARM";
    public static final String ACTION_REFRESH_ALARM = "com.majnioui.tv.prayer.ACTION_REFRESH_ALARM";

    public static final String EXTRA_PRAYER_NAME = "prayer_name";
    public static final String EXTRA_PRAYER_TIME = "prayer_time";
    public static final String EXTRA_PLACE = "place";

    private static final String PREFS_NAME = "prayer_prefs";
    private static final String KEY_DATE = "cached_date";
    private static final String KEY_PLACE = "place";

    public static final String[] NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    public static final int[] NAME_RES = {
            R.string.prayer_fajr,
            R.string.prayer_sunrise,
            R.string.prayer_dhuhr,
            R.string.prayer_asr,
            R.string.prayer_maghrib,
            R.string.prayer_isha
    };
    public static final String[] ARABIC = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    public static final int[] ICONS = {
            R.drawable.ic_fajr,
            R.drawable.ic_sunrise,
            R.drawable.ic_dhuhr,
            R.drawable.ic_asr,
            R.drawable.ic_maghrib,
            R.drawable.ic_isha
    };

    public static int getPrayerIndex(String name) {
        if (name == null) return -1;
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    public static void saveTimingsAndSchedule(Context ctx, String day, String[] names, String[] times, int[] minutes, String place) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_DATE, day);
        editor.putString(KEY_PLACE, place);
        for (int i = 0; i < names.length; i++) {
            editor.putString("time_" + names[i], times[i]);
            editor.putInt("min_" + names[i], minutes[i]);
        }
        editor.apply();

        if (ctx.getResources().getBoolean(R.bool.reminders)) {
            scheduleNext(ctx);
        } else {
            cancel(ctx);
        }
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent pIntent = new Intent(ctx, PrayerReceiver.class);
        pIntent.setAction(ACTION_PRAYER_ALARM);
        PendingIntent pPi = PendingIntent.getBroadcast(
                ctx,
                1001,
                pIntent,
                PendingIntent.FLAG_NO_CREATE | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );
        if (pPi != null) {
            am.cancel(pPi);
            pPi.cancel();
        }

        Intent rIntent = new Intent(ctx, PrayerReceiver.class);
        rIntent.setAction(ACTION_REFRESH_ALARM);
        PendingIntent rPi = PendingIntent.getBroadcast(
                ctx,
                1002,
                rIntent,
                PendingIntent.FLAG_NO_CREATE | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );
        if (rPi != null) {
            am.cancel(rPi);
            rPi.cancel();
        }
        Log.i(TAG, "All prayer reminder alarms cancelled");
    }

    public static void scheduleNext(Context ctx) {
        if (!ctx.getResources().getBoolean(R.bool.reminders)) {
            cancel(ctx);
            return;
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedDate = prefs.getString(KEY_DATE, "");
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());

        if (!today.equals(savedDate)) {
            // Data for today is not available yet, fetch in background
            fetchTimingsAndSchedule(ctx);
            return;
        }

        String place = prefs.getString(KEY_PLACE, ctx.getString(R.string.place));
        int[] minutes = new int[NAMES.length];
        String[] times = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            minutes[i] = prefs.getInt("min_" + NAMES[i], -1);
            times[i] = prefs.getString("time_" + NAMES[i], "");
        }

        Calendar now = Calendar.getInstance();
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int nextIdx = -1;

        // Skip index 1 (Sunrise), only check prayer times
        for (int i = 0; i < NAMES.length; i++) {
            if (i == 1) continue;
            if (minutes[i] > nowMinute) {
                nextIdx = i;
                break;
            }
        }

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        if (nextIdx >= 0) {
            // Next prayer is today
            Calendar target = (Calendar) now.clone();
            target.set(Calendar.HOUR_OF_DAY, minutes[nextIdx] / 60);
            target.set(Calendar.MINUTE, minutes[nextIdx] % 60);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);

            long triggerAtMillis = target.getTimeInMillis();
            Log.i(TAG, "Scheduling alarm for " + NAMES[nextIdx] + " (" + times[nextIdx] + ") at " + target.getTime());

            Intent intent = new Intent(ctx, PrayerReceiver.class);
            intent.setAction(ACTION_PRAYER_ALARM);
            intent.putExtra(EXTRA_PRAYER_NAME, NAMES[nextIdx]);
            intent.putExtra(EXTRA_PRAYER_TIME, times[nextIdx]);
            intent.putExtra(EXTRA_PLACE, place);

            PendingIntent pi = PendingIntent.getBroadcast(
                    ctx,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            setAlarm(am, triggerAtMillis, pi);
        } else {
            // All prayers for today have passed. Schedule refresh at 00:05 tomorrow morning
            Calendar target = (Calendar) now.clone();
            target.add(Calendar.DAY_OF_YEAR, 1);
            target.set(Calendar.HOUR_OF_DAY, 0);
            target.set(Calendar.MINUTE, 5);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);

            Log.i(TAG, "All prayers passed for today. Scheduling daily refresh at " + target.getTime());

            Intent intent = new Intent(ctx, PrayerReceiver.class);
            intent.setAction(ACTION_REFRESH_ALARM);

            PendingIntent pi = PendingIntent.getBroadcast(
                    ctx,
                    1002,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            setAlarm(am, target.getTimeInMillis(), pi);
        }
    }

    private static void setAlarm(AlarmManager am, long triggerAtMillis, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
                    return;
                } catch (SecurityException e) {
                    Log.w(TAG, "Exact alarm disallowed at runtime, falling back: " + e);
                }
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    public static void fetchTimingsAndSchedule(Context ctx) {
        if (!ctx.getResources().getBoolean(R.bool.reminders)) {
            cancel(ctx);
            return;
        }

        new Thread(() -> {
            try {
                Context appContext = ctx.getApplicationContext();
                String day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
                String dayParam = new SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(new Date());
                String url = "https://api.aladhan.com/v1/timings/" + dayParam
                        + "?latitude=" + appContext.getString(R.string.latitude)
                        + "&longitude=" + appContext.getString(R.string.longitude)
                        + "&method=" + appContext.getString(R.string.method);

                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[4096];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                } finally {
                    c.disconnect();
                }

                JSONObject json = new JSONObject(out.toString("UTF-8"));
                JSONObject data = json.getJSONObject("data");
                JSONObject t = data.getJSONObject("timings");

                int[] minutes = new int[NAMES.length];
                String[] times = new String[NAMES.length];
                for (int i = 0; i < NAMES.length; i++) {
                    String v = t.getString(NAMES[i]).trim();
                    int sp = v.indexOf(' ');
                    if (sp > 0) v = v.substring(0, sp);
                    times[i] = v;
                    minutes[i] = Integer.parseInt(v.substring(0, 2)) * 60 + Integer.parseInt(v.substring(3, 5));
                }

                saveTimingsAndSchedule(appContext, day, NAMES, times, minutes, appContext.getString(R.string.place));
                Log.i(TAG, "Background fetch and schedule successful for " + day);
            } catch (Exception e) {
                Log.w(TAG, "Background fetch error: " + e);
            }
        }).start();
    }
}
