package com.gathin.tvhome;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cuaca di samping jam layar Home, tanpa mengubah MainActivity.
 *
 * Cara kerja: Application ini memantau MainActivity. Saat Home tampil, ia mencari TextView
 * yang isinya berupa jam (mis. 19:42), memasang satu TextView cuaca di samping kanannya
 * (kiri untuk layout RTL), lalu mengikuti posisi dan transparansi jam itu. Data dari
 * Open-Meteo (tanpa API key), diambil paling sering tiap 30 menit dan hanya saat Home tampil.
 *
 * Opsional (cw_island): kapsul hitam di belakang tombol atas, ala Dynamic Island.
 *
 * Aman gagal: jika jam tidak ditemukan atau terjadi error, cuaca tidak tampil dan launcher
 * berjalan seperti biasa. Log: adb logcat -s CleanWeather
 */
public class WeatherApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new Hook(this));
    }

    static final class Hook implements Application.ActivityLifecycleCallbacks, ViewTreeObserver.OnPreDrawListener {

        private static final String TAG = "CleanWeather";
        private static final Pattern TIME = Pattern.compile("^\\d{1,2}[:.]\\d{2}(\\s?[AaPp]\\.?[Mm]\\.?)?$");
        private static final long MAX_AGE_MS = 30 * 60 * 1000L;
        private static final long STALE_MS = 3 * 60 * 60 * 1000L;
        private static final String[] ID = {"Cerah", "Berawan", "Berkabut", "Gerimis", "Hujan", "Salju", "Badai petir"};
        private static final String[] EN = {"Clear", "Cloudy", "Fog", "Drizzle", "Rain", "Snow", "Thunderstorm"};

        private final Context app;
        private final Handler ui = new Handler(Looper.getMainLooper());
        private boolean enabled, fahrenheit, island = true, broken, fetching, warned;
        private double lat, lon;
        private SharedPreferences prefs; // dibuat saat dibutuhkan: Application juga hidup saat direct boot
        private Activity home;
        private FrameLayout content;
        private TextView clock, tv;
        private Activity islandHost;
        private long lastFind;
        private float lastX = Float.NaN, lastY = Float.NaN;

        private final Runnable refreshTask = new Runnable() {
            @Override public void run() {
                refresh();
                ui.postDelayed(this, MAX_AGE_MS);
            }
        };

        Hook(Context app) {
            this.app = app;
            try {
                Resources r = app.getResources();
                enabled = r.getBoolean(R.bool.cw_enabled);
                fahrenheit = r.getBoolean(R.bool.cw_fahrenheit);
                lat = Double.parseDouble(r.getString(R.string.cw_lat).trim());
                lon = Double.parseDouble(r.getString(R.string.cw_lon).trim());
                int islandId = r.getIdentifier("cw_island", "bool", app.getPackageName());
                island = islandId == 0 || r.getBoolean(islandId); // weather.xml lama tanpa cw_island: nyala
            } catch (Exception e) {
                enabled = false;
                Log.w(TAG, "konfigurasi cuaca tidak valid, fitur dimatikan", e);
            }
        }

        // ---- siklus hidup ----

        @Override public void onActivityResumed(Activity a) {
            if (broken || !"MainActivity".equals(a.getClass().getSimpleName())) return;
            try {
                if (island) applyIsland(a);
                if (!enabled) return;
                View c = a.findViewById(android.R.id.content);
                if (!(c instanceof FrameLayout)) return;
                home = a;
                content = (FrameLayout) c;
                if (tv == null || tv.getParent() != content) {
                    tv = new TextView(a);
                    tv.setSingleLine(true);
                    tv.setVisibility(View.INVISIBLE);
                    tv.setFocusable(false);
                    tv.setClickable(false);
                    content.addView(tv, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP | Gravity.LEFT));
                    clock = null;
                    lastX = lastY = Float.NaN;
                }
                String cached = cachedText();
                if (cached != null) tv.setText(cached);
                ViewTreeObserver o = content.getViewTreeObserver();
                o.removeOnPreDrawListener(this);
                o.addOnPreDrawListener(this);
                ui.removeCallbacks(refreshTask);
                ui.post(refreshTask);
            } catch (Exception e) {
                fail(e);
            }
        }

        @Override public void onActivityPaused(Activity a) {
            if (a != home) return;
            ui.removeCallbacks(refreshTask);
            if (content != null) {
                ViewTreeObserver o = content.getViewTreeObserver();
                if (o.isAlive()) o.removeOnPreDrawListener(this);
            }
        }

        @Override public void onActivityDestroyed(Activity a) {
            if (a != home) return;
            home = null;
            content = null;
            clock = null;
            tv = null;
        }

        @Override public void onActivityCreated(Activity a, Bundle b) { }
        @Override public void onActivityStarted(Activity a) { }
        @Override public void onActivityStopped(Activity a) { }
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }

        private void fail(Exception e) {
            broken = true;
            Log.w(TAG, "cuaca dimatikan karena error", e);
            if (tv != null) tv.setVisibility(View.INVISIBLE);
        }

        // ---- posisi di samping jam (dipanggil sebelum tiap frame digambar) ----

        @Override public boolean onPreDraw() {
            if (broken) return true;
            try {
                place();
            } catch (Exception e) {
                fail(e);
            }
            return true;
        }

        private void place() {
            if (tv == null || content == null) return;
            if (clock == null || !clock.isAttachedToWindow()) {
                long now = SystemClock.uptimeMillis();
                if (now - lastFind < 1000) return;
                lastFind = now;
                View byId = home == null ? null : home.findViewById(R.id.clock);
                clock = byId instanceof TextView ? (TextView) byId : findClock(content);
                if (clock == null) {
                    tv.setVisibility(View.INVISIBLE);
                    if (!warned) {
                        warned = true;
                        Log.i(TAG, "teks jam tidak ditemukan di layar Home; cuaca tidak ditampilkan");
                    }
                    return;
                }
                Log.i(TAG, "jam ditemukan: " + clock.getClass().getSimpleName());
                tv.setTypeface(clock.getTypeface());
                tv.setTextColor(clock.getCurrentTextColor());
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, clock.getTextSize() * 0.38f);
            }

            float alpha = clock.getAlpha();
            boolean visible = clock.getVisibility() == View.VISIBLE;
            for (ViewParent p = clock.getParent(); p instanceof View && p != content; p = p.getParent()) {
                View v = (View) p;
                alpha *= v.getAlpha();
                if (v.getVisibility() != View.VISIBLE) visible = false;
            }
            Layout l = clock.getLayout();
            if (!visible || alpha <= 0.01f || l == null || tv.getText().length() == 0
                    || tv.getWidth() == 0 || tv.getBaseline() < 0) {
                tv.setVisibility(View.INVISIBLE);
                return;
            }

            int[] a = new int[2];
            int[] b = new int[2];
            clock.getLocationInWindow(a);
            content.getLocationInWindow(b);
            float gap = clock.getTextSize() * 0.35f;
            float base = a[0] - b[0] + clock.getCompoundPaddingLeft() - clock.getScrollX();
            float x = clock.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                    ? base + l.getLineLeft(0) - gap - tv.getWidth()
                    : base + l.getLineRight(0) + gap;
            float y = a[1] - b[1] + clock.getCompoundPaddingTop() + l.getLineBaseline(0) - tv.getBaseline();

            if (Float.isNaN(lastX) || Math.abs(x - lastX) > 0.5f || Math.abs(y - lastY) > 0.5f) {
                tv.setTranslationX(x);
                tv.setTranslationY(y);
                lastX = x;
                lastY = y;
            }
            tv.setAlpha(alpha * 0.8f);
            tv.setVisibility(View.VISIBLE);
        }

        /** Kapsul hitam di belakang tombol atas (Input, Wi-Fi, Pengaturan), sekali per Activity. */
        private void applyIsland(Activity a) {
            if (islandHost == a) return;
            View bar = a.findViewById(R.id.topButtons);
            if (bar == null) return;
            float d = a.getResources().getDisplayMetrics().density;
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(0xFF000000);
            pill.setCornerRadius(40 * d); // dibatasi otomatis ke setengah tinggi: bentuk kapsul
            bar.setBackground(pill);
            int pad = Math.round(6 * d);
            bar.setPadding(bar.getPaddingLeft() + pad, bar.getPaddingTop() + pad,
                    bar.getPaddingRight() + pad, bar.getPaddingBottom() + pad);
            islandHost = a;
        }

        /** TextView terbesar yang isinya berupa jam. */
        private static TextView findClock(View v) {
            TextView best = null;
            if (v instanceof TextView && v.getVisibility() != View.GONE) {
                CharSequence t = ((TextView) v).getText();
                if (t != null && TIME.matcher(t.toString().trim()).matches()) best = (TextView) v;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    TextView c = findClock(g.getChildAt(i));
                    if (c != null && (best == null || c.getTextSize() > best.getTextSize())) best = c;
                }
            }
            return best;
        }

        // ---- data cuaca ----

        private SharedPreferences prefs() {
            if (prefs == null) prefs = app.getSharedPreferences("clean_weather", Context.MODE_PRIVATE);
            return prefs;
        }

        private String key() {
            return lat + "," + lon + "," + fahrenheit;
        }

        private String cachedText() {
            SharedPreferences p = prefs();
            if (!key().equals(p.getString("key", ""))) return null;
            if (System.currentTimeMillis() - p.getLong("at", 0) > STALE_MS) return null;
            return p.getString("text", null);
        }

        private void refresh() {
            if (fetching) return;
            SharedPreferences p = prefs();
            if (key().equals(p.getString("key", ""))
                    && System.currentTimeMillis() - p.getLong("at", 0) < MAX_AGE_MS) return;
            fetching = true;
            final String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,weather_code&timezone=auto%s",
                    lat, lon, fahrenheit ? "&temperature_unit=fahrenheit" : "");
            new Thread(new Runnable() {
                @Override public void run() {
                    String text = null;
                    HttpURLConnection c = null;
                    try {
                        c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(5000);
                        c.setReadTimeout(5000);
                        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String s;
                        while ((s = r.readLine()) != null) sb.append(s);
                        r.close();
                        JSONObject cur = new JSONObject(sb.toString()).getJSONObject("current");
                        text = describe(Math.round(cur.getDouble("temperature_2m")), cur.getInt("weather_code"));
                    } catch (Exception e) {
                        Log.w(TAG, "gagal mengambil cuaca: " + e);
                    } finally {
                        if (c != null) c.disconnect();
                    }
                    final String t = text;
                    ui.post(new Runnable() {
                        @Override public void run() {
                            fetching = false;
                            if (t == null) return;
                            prefs().edit().putString("key", key()).putString("text", t)
                                    .putLong("at", System.currentTimeMillis()).apply();
                            if (tv != null) tv.setText(t);
                        }
                    });
                }
            }, "clean-weather").start();
        }

        private String describe(long temp, int code) {
            String lang = Locale.getDefault().getLanguage();
            String[] names = ("id".equals(lang) || "in".equals(lang)) ? ID : EN;
            int k = kind(code);
            return temp + "\u00B0" + (fahrenheit ? "F" : "C") + (k < 0 ? "" : "  " + names[k]);
        }

        /** Kode cuaca WMO dari Open-Meteo menjadi indeks ke ID/EN. */
        private static int kind(int c) {
            if (c == 0) return 0;
            if (c <= 3) return 1;
            if (c == 45 || c == 48) return 2;
            if (c <= 57) return 3;
            if (c <= 67 || (c >= 80 && c <= 82)) return 4;
            if (c <= 77 || c == 85 || c == 86) return 5;
            if (c >= 95) return 6;
            return -1;
        }
    }
}
