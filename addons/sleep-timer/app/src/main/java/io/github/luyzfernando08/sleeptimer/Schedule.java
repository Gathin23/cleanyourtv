package io.github.luyzfernando08.sleeptimer;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.time.*;
import java.util.*;

final class Schedule {
    static final String ACTION_BEDTIME = "io.github.luyzfernando08.sleeptimer.BEDTIME";
    static final String ACTION_TEST_WAKE = "io.github.luyzfernando08.sleeptimer.TEST_WAKE";
    static final String ACTION_EVENT = "io.github.luyzfernando08.sleeptimer.SCHEDULE";
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("schedule", 0); }
    static boolean on(Context c) { return prefs(c).getBoolean("on", false); }
    static List<ScheduleRules.Rule> rules(Context c) {
        List<ScheduleRules.Rule> out = new ArrayList<>();
        String stored = prefs(c).getString("rules", null);
        if (stored == null) {
            out.add(new ScheduleRules.Rule(31, prefs(c).getInt("weekday", 1410), false));
            out.add(new ScheduleRules.Rule(96, prefs(c).getInt("weekend", 60), false));
            return out;
        }
        try {
            JSONArray a = new JSONArray(stored);
            for (int i=0; i<a.length(); i++) {
                JSONObject r = a.getJSONObject(i);
                int days=r.getInt("days"), minute=r.getInt("minute");
                if (days>0 && days<=127 && minute>=0 && minute<1440)
                    out.add(new ScheduleRules.Rule(days, minute, r.getBoolean("wake")));
            }
        } catch (JSONException e) { android.util.Log.e("Schedule", "Invalid rules", e); }
        return out;
    }
    static List<ScheduleRules.Pause> pauses(Context c) {
        List<ScheduleRules.Pause> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("pauses", "[]"));
            for (int i=0; i<a.length(); i++) {
                JSONObject p=a.getJSONObject(i);
                LocalDate start=LocalDate.parse(p.getString("start")), end=LocalDate.parse(p.getString("end"));
                if (!end.isBefore(start)) out.add(new ScheduleRules.Pause(start,end));
            }
        } catch (JSONException | java.time.format.DateTimeParseException e) { android.util.Log.e("Schedule", "Invalid pauses", e); }
        return out;
    }
    static void save(Context c, boolean enabled, List<ScheduleRules.Rule> rules, List<ScheduleRules.Pause> pauses) {
        JSONArray rr=new JSONArray(), pp=new JSONArray();
        try {
            for (ScheduleRules.Rule r:rules) rr.put(new JSONObject().put("days",r.days).put("minute",r.minute).put("wake",r.wake));
            for (ScheduleRules.Pause p:pauses) pp.put(new JSONObject().put("start",p.start.toString()).put("end",p.end.toString()));
        } catch (JSONException e) { throw new IllegalStateException(e); }
        prefs(c).edit().putBoolean("on",enabled).putString("rules",rr.toString()).putString("pauses",pp.toString()).apply();
        reschedule(c);
    }
    static String describe(Context c) {
        if (!on(c)) return "";
        ScheduleRules.Event e=ScheduleRules.next(System.currentTimeMillis(),ZoneId.systemDefault(),rules(c),pauses(c));
        return e==null ? "" : android.text.format.DateFormat.format("EEE HH:mm", e.at).toString();
    }
    private static PendingIntent pending(Context c) {
        return PendingIntent.getBroadcast(c,3,new Intent(c,AlarmReceiver.class).setAction(ACTION_EVENT),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void reschedule(Context c) {
        AlarmManager am=c.getSystemService(AlarmManager.class);
        // Cancel both alarms left by the earlier weekday/weekend implementation.
        for (int id=1;id<=2;id++) am.cancel(PendingIntent.getBroadcast(c,id,
                new Intent(c,AlarmReceiver.class).setAction(ACTION_BEDTIME),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
        am.cancel(pending(c));
        prefs(c).edit().remove("next_at").apply();
        if (!on(c)) return;
        ScheduleRules.Event e=ScheduleRules.next(System.currentTimeMillis(),ZoneId.systemDefault(),rules(c),pauses(c));
        if (e==null) return;
        prefs(c).edit().putLong("next_at",e.at).putBoolean("next_wake",e.wake).apply();
        if (Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,e.at,pending(c));
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,e.at,pending(c));
    }
    static void wakeScreen(Context c) {wakeScreen(c,()->{});}
    @SuppressWarnings("deprecation")
    static void wakeScreen(Context c,Runnable finished) {
        PowerManager pm=c.getSystemService(PowerManager.class);
        PowerManager.WakeLock lock=pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"sleeptimer:schedule");
        lock.acquire(3000);
        android.util.Log.i("SleepSchedule", "Screen wake requested");
        finished.run();
    }
    // Uses the same screen-wake path as production; accessible only through the shell-protected receiver.
    static void testWake(Context c, int seconds) {
        PendingIntent pi=PendingIntent.getBroadcast(c,4,new Intent(c,AlarmReceiver.class).setAction(ACTION_TEST_WAKE),
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        c.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis()+Math.max(10,Math.min(seconds,300))*1000L,pi);
    }
    static void fire(Context c) {fire(c,()->{});}
    static void fire(Context c,Runnable finished) {
        long at=prefs(c).getLong("next_at",0),now=System.currentTimeMillis();
        boolean wake=prefs(c).getBoolean("next_wake",false);
        prefs(c).edit().remove("next_at").apply();
        Runnable done=()->{reschedule(c);finished.run();};
        if(on(c)&&at>0&&now>=at&&now-at<10*60_000L) {
            if(wake&&!ScheduleRules.paused(LocalDate.now(),pauses(c))){wakeScreen(c,done);return;}
            if(!wake&&c.getSystemService(PowerManager.class).isInteractive()){Timer.sleepNow(c);done.run();return;}
        }
        done.run();
    }
}
