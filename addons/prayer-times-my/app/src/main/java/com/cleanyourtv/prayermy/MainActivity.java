package com.cleanyourtv.prayermy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.ColorStateList;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Malaysia prayer times from api.waktusolat.app (free, no key; official JAKIM data).
 * Only the zone code, year and month are sent. No location permission: JAKIM zones are
 * fixed codes (WLY01, SGR01, ...), so the zone is picked on the TV with Left/Right and
 * remembered. Fetches once per open and after midnight/month change; the countdown ticks
 * locally every minute. Offline, the last cached month stays on screen.
 */
public class MainActivity extends Activity {
    private static final String TAG = "SolatMY";
    private static final String BASE = "https://api.waktusolat.app/v2/solat/";
    private static final TimeZone KL = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
    private static final long MINUTE_MS = 60_000L;
    private static final String PREFS = "solat_my";
    private static final String KEY_ZONE = "zone";

    // Displayed names (Malay) with the API keys behind them.
    private static final String[] NAMES = {"Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"};
    private static final String[] KEYS = {"fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha"};
    private static final String[] ARABIC = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final int[] ICONS = {R.drawable.ic_fajr, R.drawable.ic_sunrise, R.drawable.ic_dhuhr,
            R.drawable.ic_asr, R.drawable.ic_maghrib, R.drawable.ic_isha};

    // Zone code + short place name. Full JAKIM coverage would be ~50 codes; these 30 cover
    // every state and all major towns. The default zone comes from res/values/config.xml.
    private static final String[] ZONES = {
            "WLY01", "WLY02",
            "SGR01", "SGR02", "SGR03",
            "JHR01", "JHR02", "JHR03", "JHR04",
            "KDH01", "KDH02", "KDH05", "KDH06",
            "KTN01", "MLK01",
            "NGS01", "NGS02",
            "PHG02", "PHG04",
            "PLS01", "PNG01",
            "PRK02", "PRK06",
            "TRG01", "TRG02", "TRG04",
            "SBH04", "SBH02",
            "SWK04", "SWK08"};
    private static final String[] PLACES = {
            "Kuala Lumpur / Putrajaya", "Labuan",
            "Selangor: Gombak, Petaling, Sepang", "Selangor: K. Selangor, Sabak Bernam", "Selangor: Klang",
            "Johor: P. Aur, Pemanggil", "Johor Bahru, Kulai, Mersing", "Kluang, Pontian", "Batu Pahat, Muar, Segamat",
            "Alor Setar, Kubang Pasu", "Sg Petani, Yan, Pendang", "Kulim, Bandar Baharu", "Langkawi",
            "Kelantan", "Melaka",
            "Tampin, Jempol", "Seremban, Port Dickson",
            "Kuantan, Pekan", "Bentong, Lipis, Raub",
            "Perlis", "Pulau Pinang",
            "Ipoh, Kampar", "Taiping, Parit Buntar",
            "Kuala Terengganu", "Besut, Setiu", "Dungun, Kemaman",
            "Kota Kinabalu", "Tawau, Lahad Datu",
            "Sibu, Kapit", "Kuching"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private Typeface font;
    private int accent;
    private TextView placeView, dates, next, status;
    private LinearLayout rows;
    private int[] minutes;
    private String[] times;
    private String hijriDay = "";
    private String loadedForDay = "";
    private String loadedZone = "";
    private boolean loading;
    private String zone;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!todayKey().equals(loadedForDay) || !zone.equals(loadedZone)) load();
            else render();
            main.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        font = getResources().getFont(R.font.outfit);
        accent = getColor(R.color.accent);
        zone = prefs().getString(KEY_ZONE, getString(R.string.zone).trim());
        if (zoneIndex(zone) < 0) zone = ZONES[0];

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {(accent & 0x00FFFFFF) | 0x55000000, 0xFF050607, 0xFF050607}));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(80), dp(48), dp(80), dp(40));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        placeView = text(18, 400, 0xB3FFFFFF);
        placeView.setLetterSpacing(0.25f);
        col.addView(placeView);

        next = text(52, 300, 0xFFFFFFFF);
        next.setPadding(0, dp(10), 0, dp(4));
        col.addView(next);
        dates = text(20, 400, 0xB3FFFFFF);
        col.addView(dates);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        rlp.setMargins(0, dp(36), 0, 0);
        col.addView(rows, rlp);

        status = text(16, 400, 0x80FFFFFF);
        status.setPadding(0, dp(18), 0, 0);
        status.setText("Loading…");
        col.addView(status);

        setContentView(root);
        updatePlace();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        main.postDelayed(tick, MINUTE_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
    }

    /** Left/Right changes the JAKIM zone and remembers it. No location permission needed. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int i = zoneIndex(zone);
            int d = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            zone = ZONES[(i + d + ZONES.length) % ZONES.length];
            prefs().edit().putString(KEY_ZONE, zone).apply();
            updatePlace();
            load();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private static int zoneIndex(String z) {
        for (int i = 0; i < ZONES.length; i++) if (ZONES[i].equals(z)) return i;
        return -1;
    }

    private void updatePlace() {
        int i = Math.max(0, zoneIndex(zone));
        placeView.setText((PLACES[i] + "  ·  " + ZONES[i]).toUpperCase(Locale.ROOT));
    }

    private static String todayKey() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-M-d", Locale.ROOT);
        f.setTimeZone(KL);
        return f.format(new Date());
    }

    private void load() {
        if (loading) return;
        loading = true;
        final String z = zone;
        Calendar c = Calendar.getInstance(KL);
        final int year = c.get(Calendar.YEAR);
        final int month = c.get(Calendar.MONTH) + 1;
        final int day = c.get(Calendar.DAY_OF_MONTH);
        final String dayKey = todayKey();
        final String url = BASE + z + "?year=" + year + "&month=" + month;
        new Thread(() -> {
            JSONObject json = null;
            try {
                json = new JSONObject(fetch(url));
            } catch (Exception e) {
                Log.w(TAG, "fetch: " + e);
            }
            if (json == null) {
                // Fallback: the same endpoint without query params serves the current month.
                try {
                    json = new JSONObject(fetch(BASE + z));
                } catch (Exception e) {
                    Log.w(TAG, "fetch fallback: " + e);
                }
            }
            if (json != null) {
                prefs().edit().putString("cache_" + z + "_" + year + "_" + month, json.toString()).apply();
            } else {
                String cached = prefs().getString("cache_" + z + "_" + year + "_" + month, null);
                if (cached != null) {
                    try {
                        json = new JSONObject(cached);
                    } catch (Exception e) {
                        Log.w(TAG, "cache: " + e);
                    }
                }
            }
            final JSONObject result = json;
            main.post(() -> {
                loading = false;
                if (result == null) {
                    status.setText("Can't reach api.waktusolat.app. Showing the last times, if any.");
                    return;
                }
                try {
                    parse(result, day);
                    loadedForDay = dayKey;
                    loadedZone = z;
                    render();
                } catch (Exception e) {
                    Log.w(TAG, "parse: " + e);
                    status.setText("Unexpected answer from api.waktusolat.app.");
                }
            });
        }).start();
    }

    private void parse(JSONObject data, int day) throws Exception {
        JSONArray prayers = data.getJSONArray("prayers");
        JSONObject today = null;
        for (int i = 0; i < prayers.length(); i++) {
            JSONObject p = prayers.getJSONObject(i);
            if (p.getInt("day") == day) { today = p; break; }
        }
        if (today == null) throw new IllegalStateException("no entry for day " + day);
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.ROOT);
        hm.setTimeZone(KL);
        int[] m = new int[NAMES.length];
        String[] s = new String[NAMES.length];
        Calendar cal = Calendar.getInstance(KL);
        for (int i = 0; i < NAMES.length; i++) {
            long epoch = today.getLong(KEYS[i]);
            Date d = new Date(epoch * 1000L);
            s[i] = hm.format(d);
            cal.setTime(d);
            m[i] = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        }
        minutes = m;
        times = s;
        // Hijri comes as "1448-05-16" (year-month-day).
        String hijri = today.optString("hijri", "");
        String[] parts = hijri.split("-");
        hijriDay = parts.length == 3 ? parts[2] + "/" + parts[1] + "/" + parts[0] + "H" : hijri;
        SimpleDateFormat g = new SimpleDateFormat("EEEE d MMMM yyyy", Locale.ENGLISH);
        g.setTimeZone(KL);
        dates.setText(g.format(new Date()) + "  ·  " + hijriDay);
    }

    private void render() {
        if (minutes == null) return;
        Calendar c = Calendar.getInstance(KL);
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int nextIdx = -1;
        for (int i = 0; i < NAMES.length; i++) {
            if (i == 1) continue;   // Syuruk is shown but is not a prayer
            if (minutes[i] > now) { nextIdx = i; break; }
        }
        if (nextIdx >= 0) {
            int left = minutes[nextIdx] - now;
            next.setText(NAMES[nextIdx] + " in " + (left >= 60 ? left / 60 + " h " : "") + left % 60 + " min");
        } else {
            next.setText("Subuh tomorrow");
        }

        rows.removeAllViews();
        for (int i = 0; i < NAMES.length; i++) {
            boolean hl = i == nextIdx;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(6), dp(20), dp(6), dp(20));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(22));
            bg.setColor(hl ? accent : 0x14FFFFFF);
            card.setBackground(bg);
            int dim = i == 1 ? 0x80FFFFFF : 0xFFFFFFFF;
            int fg = hl ? 0xFFFFFFFF : dim;
            ImageView icon = new ImageView(this);
            icon.setImageResource(ICONS[i]);
            icon.setImageTintList(ColorStateList.valueOf(hl ? 0xFFFFFFFF : (i == 1 ? 0x80FFFFFF : accent)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
            ilp.bottomMargin = dp(22);
            card.addView(icon, ilp);
            TextView ar = centered(text(26, 400, fg));
            ar.setText(ARABIC[i]);
            card.addView(ar);
            TextView name = centered(text(20, 500, hl ? 0xE6FFFFFF : (i == 1 ? 0x80FFFFFF : 0xB3FFFFFF)));
            name.setText(NAMES[i]);
            name.setPadding(0, dp(8), 0, dp(20));
            card.addView(name);
            TextView time = centered(text(42, hl ? 500 : 300, fg));
            time.setText(times[i]);
            time.setMaxLines(1);
            time.setAutoSizeTextTypeUniformWithConfiguration(20, 42, 1, TypedValue.COMPLEX_UNIT_SP);
            card.addView(time, new LinearLayout.LayoutParams(-1, dp(52)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.setMargins(i == 0 ? 0 : dp(8), 0, i == NAMES.length - 1 ? 0 : dp(8), 0);
            rows.addView(card, lp);
        }
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.ROOT);
        hm.setTimeZone(KL);
        status.setText("api.waktusolat.app (JAKIM) · updated " + hm.format(new Date())
                + " · Left/Right changes zone");
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private static TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        t.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        return t;
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, weight, false) : font);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
