package com.gathin.addons.youtubetimer;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class TimerPreferences {
    private static final String PREF_NAME = "youtube_timer_prefs";
    private static final String KEY_PIN = "pin_code";
    private static final String KEY_LIMIT_MIN = "limit_minutes";
    private static final String KEY_DATE = "tracked_date";
    private static final String KEY_SECONDS_TODAY = "seconds_today";
    private static final String KEY_RESET_OFFSET = "reset_offset_seconds";
    private static final String KEY_UNLOCKED_DATE = "unlocked_date";
    private static final String KEY_BONUS_SECONDS = "bonus_seconds";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_LOCKOUT_UNTIL = "lockout_until";

    private final Context context;
    private final SharedPreferences prefs;

    public TimerPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // Reset any temporary automated test PIN back to default 1234
        if ("5555".equals(prefs.getString(KEY_PIN, "1234"))) {
            prefs.edit().putString(KEY_PIN, "1234").apply();
        }
    }

    private String todayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public String getPin() {
        return prefs.getString(KEY_PIN, "1234");
    }

    public void setPin(String pin) {
        prefs.edit().putString(KEY_PIN, pin).apply();
    }

    public int getLimitMinutes() {
        return prefs.getInt(KEY_LIMIT_MIN, 60);
    }

    public void setLimitMinutes(int minutes) {
        prefs.edit().putInt(KEY_LIMIT_MIN, minutes).apply();
    }

    public synchronized int getFailedAttempts() {
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0);
    }

    public synchronized void recordFailedAttempt() {
        int attempts = getFailedAttempts() + 1;
        SharedPreferences.Editor editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts);
        if (attempts >= 5) {
            long lockoutUntil = System.currentTimeMillis() + (60 * 1000L); // 1 minute cooldown
            editor.putLong(KEY_LOCKOUT_UNTIL, lockoutUntil);
        }
        editor.apply();
    }

    public synchronized void resetFailedAttempts() {
        prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply();
    }

    public synchronized long getRemainingLockoutSeconds() {
        long lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L);
        long now = System.currentTimeMillis();
        if (lockoutUntil > now) {
            return (lockoutUntil - now + 999L) / 1000L;
        }
        if (prefs.getInt(KEY_FAILED_ATTEMPTS, 0) >= 5) {
            resetFailedAttempts();
        }
        return 0L;
    }

    public int[] getHalfHourUsageSecondsToday() {
        int[] slots = new int[48];
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return slots;

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();
            long now = System.currentTimeMillis();

            // Query events starting from 24h before midnight to capture ongoing sessions correctly
            long queryStart = startOfDay - (24 * 3600 * 1000L);
            UsageEvents events = usm.queryEvents(queryStart, now);
            if (events == null) return slots;

            UsageEvents.Event event = new UsageEvents.Event();
            long sessionStart = -1;
            Set<String> activeActivities = new HashSet<>();

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (!"com.google.android.youtube.tv".equals(event.getPackageName())) {
                    continue;
                }

                int type = event.getEventType();
                long t = event.getTimeStamp();
                String cls = event.getClassName();
                if (cls == null) cls = "default";

                if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (activeActivities.isEmpty()) {
                        sessionStart = t;
                    }
                    activeActivities.add(cls);
                } else if (type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED) {
                    activeActivities.remove(cls);
                    if (activeActivities.isEmpty() && sessionStart != -1) {
                        distributeSessionToSlots(slots, startOfDay, now, sessionStart, t);
                        sessionStart = -1;
                    }
                }
            }

            // If YouTube is currently foreground
            if (!activeActivities.isEmpty() && sessionStart != -1) {
                distributeSessionToSlots(slots, startOfDay, now, sessionStart, now);
            }
        } catch (Exception ignored) {
        }
        return slots;
    }

    private void distributeSessionToSlots(int[] slots, long startOfDay, long now, long sessionStart, long sessionEnd) {
        long clippedStart = Math.max(sessionStart, startOfDay);
        long clippedEnd = Math.min(sessionEnd, now);
        if (clippedEnd <= clippedStart) {
            return;
        }

        long halfHourMs = 30 * 60 * 1000L;
        for (int i = 0; i < 48; i++) {
            long slotStart = startOfDay + (i * halfHourMs);
            long slotEnd = slotStart + halfHourMs;
            long overlapStart = Math.max(clippedStart, slotStart);
            long overlapEnd = Math.min(clippedEnd, slotEnd);
            if (overlapEnd > overlapStart) {
                slots[i] += (int) ((overlapEnd - overlapStart) / 1000L);
                if (slots[i] > 1800) {
                    slots[i] = 1800; // Cap at 30 minutes (1800s) per slot
                }
            }
        }
    }

    public int getRawYouTubeUsageSeconds() {
        int[] slots = getHalfHourUsageSecondsToday();
        int total = 0;
        for (int s : slots) {
            total += s;
        }
        return total;
    }

    public synchronized int getSecondsToday() {
        checkDateReset();
        int rawSeconds = getRawYouTubeUsageSeconds();
        int offset = prefs.getInt(KEY_RESET_OFFSET, 0);
        int usageFromOS = Math.max(0, rawSeconds - offset);
        int tickerSeconds = prefs.getInt(KEY_SECONDS_TODAY, 0);
        return Math.max(usageFromOS, tickerSeconds);
    }

    public synchronized void addSecondsToday(int seconds) {
        checkDateReset();
        int current = getSecondsToday();
        prefs.edit().putInt(KEY_SECONDS_TODAY, current + seconds).apply();
    }

    public synchronized void resetToday() {
        String today = todayDate();
        int rawSeconds = getRawYouTubeUsageSeconds();
        prefs.edit()
                .putString(KEY_DATE, today)
                .putInt(KEY_RESET_OFFSET, rawSeconds)
                .putInt(KEY_SECONDS_TODAY, 0)
                .putInt(KEY_BONUS_SECONDS, 0)
                .remove(KEY_UNLOCKED_DATE)
                .apply();
    }

    public synchronized void addBonusMinutes(int minutes) {
        checkDateReset();
        int current = prefs.getInt(KEY_BONUS_SECONDS, 0);
        prefs.edit().putInt(KEY_BONUS_SECONDS, current + (minutes * 60)).apply();
    }

    public synchronized void unlockForToday() {
        prefs.edit().putString(KEY_UNLOCKED_DATE, todayDate()).apply();
    }

    public synchronized boolean isUnlockedForToday() {
        return todayDate().equals(prefs.getString(KEY_UNLOCKED_DATE, ""));
    }

    public synchronized boolean isLimitReached() {
        if (isUnlockedForToday()) return false;
        checkDateReset();
        int limitSeconds = (getLimitMinutes() * 60) + prefs.getInt(KEY_BONUS_SECONDS, 0);
        return getSecondsToday() >= limitSeconds;
    }

    private void checkDateReset() {
        String today = todayDate();
        String savedDate = prefs.getString(KEY_DATE, "");
        if (!today.equals(savedDate)) {
            prefs.edit()
                    .putString(KEY_DATE, today)
                    .putInt(KEY_RESET_OFFSET, 0)
                    .putInt(KEY_SECONDS_TODAY, 0)
                    .putInt(KEY_BONUS_SECONDS, 0)
                    .remove(KEY_UNLOCKED_DATE)
                    .apply();
        }
    }
}
