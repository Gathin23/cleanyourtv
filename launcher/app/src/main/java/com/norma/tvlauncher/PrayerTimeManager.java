package com.norma.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Manages daily Islamic prayer times (Jadwal Sholat) for Bekasi, Indonesia.
 * Uses official Indonesian Kemenag schedule via MyQuran API with AlAdhan API fallback.
 * Caches timings locally in SharedPreferences for instantaneous offline availability.
 */
public final class PrayerTimeManager {

    private static final String TAG = "PrayerTimeManager";
    private static final String PREF_NAME = "prayer_times_cache";

    // Default timings for Kota Bekasi if offline & no cache
    private static final String DEF_SUBUH = "04:11";
    private static final String DEF_DZUHUR = "11:40";
    private static final String DEF_ASHAR = "14:50";
    private static final String DEF_MAGHRIB = "17:48";
    private static final String DEF_ISYA = "18:58";
    private static final String DEF_LOCATION = "Bekasi, Jawa Barat";

    private static PrayerTimeManager instance;

    private String subuh = DEF_SUBUH;
    private String dzuhur = DEF_DZUHUR;
    private String ashar = DEF_ASHAR;
    private String maghrib = DEF_MAGHRIB;
    private String isya = DEF_ISYA;
    private String location = DEF_LOCATION;
    private String cachedDate = "";

    public interface OnLoadedListener {
        void onLoaded();
    }

    public static synchronized PrayerTimeManager getInstance(Context context) {
        if (instance == null) {
            instance = new PrayerTimeManager(context.getApplicationContext());
        }
        return instance;
    }

    private PrayerTimeManager(Context context) {
        loadFromCache(context);
        fetchPrayerTimesAsync(context, null);
    }

    private void loadFromCache(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        subuh = sp.getString("subuh", DEF_SUBUH);
        dzuhur = sp.getString("dzuhur", DEF_DZUHUR);
        ashar = sp.getString("ashar", DEF_ASHAR);
        maghrib = sp.getString("maghrib", DEF_MAGHRIB);
        isya = sp.getString("isya", DEF_ISYA);
        location = sp.getString("location", DEF_LOCATION);
        cachedDate = sp.getString("date", "");
    }

    private void saveToCache(Context context, String date) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString("subuh", subuh)
                .putString("dzuhur", dzuhur)
                .putString("ashar", ashar)
                .putString("maghrib", maghrib)
                .putString("isya", isya)
                .putString("location", location)
                .putString("date", date)
                .apply();
    }

    public void fetchPrayerTimesAsync(Context context, OnLoadedListener listener) {
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            boolean success = false;
            Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
            int year = now.get(Calendar.YEAR);
            int month = now.get(Calendar.MONTH) + 1;
            int day = now.get(Calendar.DAY_OF_MONTH);
            String todayStr = String.format(Locale.US, "%04d-%02d-%02d", year, month, day);

            // Attempt 1: MyQuran API (Kemenag Kota Bekasi ID 1221)
            try {
                String myQuranUrl = String.format(Locale.US,
                        "https://api.myquran.com/v2/sholat/jadwal/1221/%04d/%02d/%02d",
                        year, month, day);
                HttpURLConnection conn = (HttpURLConnection) new URL(myQuranUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "CleanYourTV/1.0");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject root = new JSONObject(sb.toString());
                    if (root.optBoolean("status", false)) {
                        JSONObject data = root.optJSONObject("data");
                        if (data != null) {
                            JSONObject jadwal = data.optJSONObject("jadwal");
                            if (jadwal != null) {
                                subuh = jadwal.optString("subuh", subuh);
                                dzuhur = jadwal.optString("dzuhur", dzuhur);
                                ashar = jadwal.optString("ashar", ashar);
                                maghrib = jadwal.optString("maghrib", maghrib);
                                isya = jadwal.optString("isya", isya);
                                location = "Kota Bekasi, Jawa Barat";
                                cachedDate = todayStr;
                                saveToCache(appContext, todayStr);
                                success = true;
                                Log.d(TAG, "Loaded prayer times from MyQuran: Subuh " + subuh + ", Zuhur " + dzuhur);
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Throwable t) {
                Log.w(TAG, "MyQuran fetch failed: " + t.getMessage());
            }

            // Attempt 2: AlAdhan API fallback (Kemenag method 20)
            if (!success) {
                try {
                    String aladhanUrl = "https://api.aladhan.com/v1/timingsByCity?city=Bekasi&country=Indonesia&method=20";
                    HttpURLConnection conn = (HttpURLConnection) new URL(aladhanUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("User-Agent", "CleanYourTV/1.0");

                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();

                        JSONObject root = new JSONObject(sb.toString());
                        JSONObject data = root.optJSONObject("data");
                        if (data != null) {
                            JSONObject timings = data.optJSONObject("timings");
                            if (timings != null) {
                                subuh = timings.optString("Fajr", subuh);
                                dzuhur = timings.optString("Dhuhr", dzuhur);
                                ashar = timings.optString("Asr", ashar);
                                maghrib = timings.optString("Maghrib", maghrib);
                                isya = timings.optString("Isha", isya);
                                location = "Bekasi, Indonesia";
                                cachedDate = todayStr;
                                saveToCache(appContext, todayStr);
                                success = true;
                                Log.d(TAG, "Loaded prayer times from AlAdhan: Subuh " + subuh + ", Zuhur " + dzuhur);
                            }
                        }
                    }
                    conn.disconnect();
                } catch (Throwable t) {
                    Log.w(TAG, "AlAdhan fetch failed: " + t.getMessage());
                }
            }

            if (listener != null) {
                new Handler(Looper.getMainLooper()).post(listener::onLoaded);
            }
        }).start();
    }

    public static class PrayerInterval {
        public String previousPrayerName;
        public String nextPrayerName;
        public String nextPrayerTimeStr;
        public long totalDurationMs;
        public long elapsedMs;
        public long remainingMs;
        public float progress; // 0.0f to 1.0f
        public String countdownStr; // "hh:mm:ss"

        // Full daily times for schedule bar
        public String subuh;
        public String dzuhur;
        public String ashar;
        public String maghrib;
        public String isya;
        public String location;
    }

    public PrayerInterval getPrayerInterval(Calendar now) {
        PrayerInterval pi = new PrayerInterval();
        pi.subuh = this.subuh;
        pi.dzuhur = this.dzuhur;
        pi.ashar = this.ashar;
        pi.maghrib = this.maghrib;
        pi.isya = this.isya;
        pi.location = this.location;

        long nowMs = now.getTimeInMillis();

        long subuhMs = parseTimeToMs(now, subuh);
        long dzuhurMs = parseTimeToMs(now, dzuhur);
        long asharMs = parseTimeToMs(now, ashar);
        long maghribMs = parseTimeToMs(now, maghrib);
        long isyaMs = parseTimeToMs(now, isya);

        long prevMs;
        long nextMs;

        if (nowMs < subuhMs) {
            // From yesterday's Isya to today's Subuh
            prevMs = isyaMs - (24L * 60 * 60 * 1000);
            nextMs = subuhMs;
            pi.previousPrayerName = "Isya";
            pi.nextPrayerName = "Subuh";
            pi.nextPrayerTimeStr = subuh;
        } else if (nowMs < dzuhurMs) {
            // From Subuh to Zuhur
            prevMs = subuhMs;
            nextMs = dzuhurMs;
            pi.previousPrayerName = "Subuh";
            pi.nextPrayerName = "Zuhur";
            pi.nextPrayerTimeStr = dzuhur;
        } else if (nowMs < asharMs) {
            // From Zuhur to Ashar
            prevMs = dzuhurMs;
            nextMs = asharMs;
            pi.previousPrayerName = "Zuhur";
            pi.nextPrayerName = "Ashar";
            pi.nextPrayerTimeStr = ashar;
        } else if (nowMs < maghribMs) {
            // From Ashar to Maghrib
            prevMs = asharMs;
            nextMs = maghribMs;
            pi.previousPrayerName = "Ashar";
            pi.nextPrayerName = "Maghrib";
            pi.nextPrayerTimeStr = maghrib;
        } else if (nowMs < isyaMs) {
            // From Maghrib to Isya
            prevMs = maghribMs;
            nextMs = isyaMs;
            pi.previousPrayerName = "Maghrib";
            pi.nextPrayerName = "Isya";
            pi.nextPrayerTimeStr = isya;
        } else {
            // From Isya to tomorrow's Subuh
            prevMs = isyaMs;
            nextMs = subuhMs + (24L * 60 * 60 * 1000);
            pi.previousPrayerName = "Isya";
            pi.nextPrayerName = "Subuh";
            pi.nextPrayerTimeStr = subuh;
        }

        pi.totalDurationMs = nextMs - prevMs;
        pi.elapsedMs = Math.max(0, nowMs - prevMs);
        pi.remainingMs = Math.max(0, nextMs - nowMs);

        if (pi.totalDurationMs > 0) {
            pi.progress = (float) pi.elapsedMs / (float) pi.totalDurationMs;
        } else {
            pi.progress = 0f;
        }
        pi.progress = Math.max(0f, Math.min(1f, pi.progress));

        long totalSecs = pi.remainingMs / 1000;
        long hours = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long seconds = totalSecs % 60;
        pi.countdownStr = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);

        return pi;
    }

    private static long parseTimeToMs(Calendar base, String timeStr) {
        Calendar cal = (Calendar) base.clone();
        int hour = 0;
        int minute = 0;
        try {
            String[] parts = timeStr.trim().split(":");
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (Throwable ignored) {}
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
