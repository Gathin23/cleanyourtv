package com.tungpv.lichvannien;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Keeps the one launcher alias whose banner matches today enabled, and arms an alarm for just after the next
 * midnight in Vietnam. Safe to call any number of times.
 */
final class DayUpdater {

    static final TimeZone VN = TimeZone.getTimeZone("GMT+07:00");
    static final int VARIANTS = 12;

    private DayUpdater() {
    }

    static Calendar now() {
        return Calendar.getInstance(VN);
    }

    static VnCalendar.Day today(int offsetDays) {
        Calendar c = now();
        c.add(Calendar.DAY_OF_MONTH, offsetDays);
        return VnCalendar.of(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }

    static void update(Context ctx) {
        update(ctx, 0);
    }

    /** offsetDays other than 0 only previews another day's tile (adb test); the next run puts today's back. */
    static void update(Context ctx, int offsetDays) {
        int want = VnCalendar.variant(today(offsetDays));
        PackageManager pm = ctx.getPackageManager();
        // The new alias first, so the package never has zero launcher entries.
        set(pm, alias(ctx, want), PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        for (int v = 0; v < VARIANTS; v++) {
            if (v != want) set(pm, alias(ctx, v), PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        }
        set(pm, new ComponentName(ctx, ctx.getPackageName() + ".DayStart"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        armMidnight(ctx);
    }

    static ComponentName alias(Context ctx, int variant) {
        return new ComponentName(ctx, ctx.getPackageName() + ".Day" + variant);
    }

    private static void set(PackageManager pm, ComponentName c, int state) {
        // DEFAULT means the manifest's enabled="true", so only DISABLED counts as off. Unchanged states are not
        // written again: every write sends PACKAGE_CHANGED and makes Home rebuild its tiles.
        boolean on = pm.getComponentEnabledSetting(c) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (on == (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)) return;
        pm.setComponentEnabledSetting(c, state, PackageManager.DONT_KILL_APP);
    }

    /** An alarm within 15 minutes after the next Vietnamese midnight; it does not wake the TV. */
    private static void armMidnight(Context ctx) {
        Calendar next = now();
        next.add(Calendar.DAY_OF_MONTH, 1);
        next.set(Calendar.HOUR_OF_DAY, 0);
        next.set(Calendar.MINUTE, 1);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, DayReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.setWindow(AlarmManager.RTC, next.getTimeInMillis(), 15 * 60_000L, pi);
    }
}
