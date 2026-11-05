package io.github.anhost77.salon;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One channel's week from the guide, by kind: Nouveautés (premieres, "inédit", or made this year or last), Films,
 * Séries (episodes of one series grouped with their airings), Documentaires, Tout. The chosen programme shows its
 * genres, year, cast and director, summary and next airings, Regarder / Bande-annonce, and "Vous aimerez aussi":
 * TMDB's recommendations, each opening the search page that says where to watch it. Extra "channel": its index.
 */
public final class ChannelActivity extends Activity {
    private static final String[] TABS = {"Nouveautés", "Films", "Séries", "Documentaires", "Tout"};

    private static final class Prog {
        long s, e;
        int g, n;
        String t, st, d, k, kk, i, a, r, y;
    }

    /** A programme and every airing of it this week (a series: its episodes). */
    private static final class Entry {
        final String title;
        final List<Prog> airings = new ArrayList<>();

        Entry(String title) {
            this.title = title;
        }
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<>(60);
    private final List<Prog> week = new ArrayList<>();
    private final List<View> tabViews = new ArrayList<>();
    private final List<View> rows = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final SimpleDateFormat when = new SimpleDateFormat("EEE d · HH:mm", Locale.FRANCE);
    private final Runnable loadRecs = this::recommend;
    private int channel, tab = -1, recRequest;
    private Entry shown;
    private LinearLayout list, recs, buttons;
    private ScrollView listScroll;
    private TextView title, meta, people, desc, airings, empty, recsTitle;
    private ImageView picture;
    private View detail;
    private TextView watch;
    private float u;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        u = getResources().getDisplayMetrics().heightPixels / 1080f;
        channel = Math.max(0, Math.min(TvTonight.CHANNELS.length - 1, getIntent().getIntExtra("channel", 0)));
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF1B2230, 0xFF0D1118, 0xFF06080C}));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(80), px(40), px(80), px(30));
        page.setClipChildren(false);
        root.addView(page);

        // Header: the channel and its tabs.
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Bitmap logo = BitmapFactory.decodeFile(new File(new File(getFilesDir(), "logos2"),
                TvTonight.CHANNELS[channel][0] + ".png").getPath());
        if (logo != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(logo);
            iv.setScaleType(ImageView.ScaleType.FIT_START);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(px(150), px(52));
            ll.rightMargin = px(30);
            head.addView(iv, ll);
        } else {
            TextView n = text(TvTonight.CHANNELS[channel][1], 34, Typeface.BOLD, Color.WHITE);
            n.setPadding(0, 0, px(30), 0);
            head.addView(n);
        }
        for (int i = 0; i < TABS.length; i++) {
            TextView t = text(TABS[i], 26, Typeface.BOLD, 0xCCFFFFFF);
            t.setPadding(px(26), px(10), px(26), px(10));
            t.setFocusable(true);
            t.setClickable(true);
            final int index = i;
            t.setOnFocusChangeListener((v, has) -> {
                paintTabs();
                if (has) select(index);
            });
            t.setOnClickListener(v -> select(index));
            t.setOnKeyListener((v, code, e) -> {
                if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (e.getAction() == KeyEvent.ACTION_DOWN && !rows.isEmpty()) rows.get(0).requestFocus();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = px(8);
            head.addView(t, lp);
            tabViews.add(t);
        }
        page.addView(head);

        LinearLayout body = new LinearLayout(this);
        body.setPadding(0, px(24), 0, 0);
        body.setClipChildren(false);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(4), px(4), px(10), px(10));
        listScroll.addView(list);
        body.addView(listScroll, new LinearLayout.LayoutParams(0, -1, 0.40f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(px(44), 0, 0, 0);
        right.setClipChildren(false);
        picture = new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setBackground(pill(0x22FFFFFF));
        picture.setClipToOutline(true);
        right.addView(picture, new LinearLayout.LayoutParams(-1, px(250)));
        title = text("", 36, Typeface.BOLD, Color.WHITE);
        title.setPadding(0, px(14), 0, px(4));
        right.addView(title);
        meta = text("", 22, Typeface.NORMAL, 0xB3FFFFFF);
        right.addView(meta);
        people = text("", 22, Typeface.NORMAL, 0xFFFFD27A);
        people.setSingleLine(false);
        people.setMaxLines(2);
        people.setEllipsize(TextUtils.TruncateAt.END);
        people.setPadding(0, px(6), 0, px(6));
        right.addView(people);
        desc = text("", 22, Typeface.NORMAL, 0xE6FFFFFF);
        desc.setSingleLine(false);
        desc.setMaxLines(3);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        right.addView(desc);
        airings = text("", 21, Typeface.NORMAL, 0x99FFFFFF);
        airings.setPadding(0, px(6), 0, px(10));
        right.addView(airings);
        buttons = new LinearLayout(this);
        watch = button(getString(R.string.guide_watch, Watch.appName(this, channel)), true);
        watch.setOnClickListener(v -> Watch.open(this, channel));
        TextView trailer = button(getString(R.string.tonight_trailer), false);
        trailer.setOnClickListener(v -> {
            if (shown != null) startActivity(new Intent(this, TrailerActivity.class).putExtra("title", shown.title));
        });
        buttons.addView(watch);
        buttons.addView(trailer);
        right.addView(buttons);
        recsTitle = text(getString(R.string.channel_recs), 21, Typeface.BOLD, 0xB3FFFFFF);
        recsTitle.setPadding(0, px(14), 0, 0);
        right.addView(recsTitle);
        HorizontalScrollView rs = new HorizontalScrollView(this);
        rs.setHorizontalScrollBarEnabled(false);
        rs.setClipChildren(false);
        recs = new LinearLayout(this);
        recs.setPadding(px(10), px(10), px(10), px(10));
        recs.setClipChildren(false);
        recs.setClipToPadding(false);
        rs.addView(recs);
        right.addView(rs);
        detail = right;
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 0.60f));

        empty = text(getString(R.string.tonight_empty), 30, Typeface.NORMAL, 0xB3FFFFFF);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        io.execute(() -> {
            List<Prog> all = readWeek();
            main.post(() -> {
                week.addAll(all);
                tabViews.get(0).requestFocus();
                select(0);
            });
        });
    }

    // ---- data ------------------------------------------------------------------------------------

    private List<Prog> readWeek() {
        List<Prog> out = new ArrayList<>();
        SimpleDateFormat key = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        Calendar day = Calendar.getInstance();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 9; i++) {
            File f = TvTonight.dayFile(this, key.format(day.getTime()));
            day.add(Calendar.DAY_OF_MONTH, 1);
            if (!f.isFile()) continue;
            try {
                JSONArray a = new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                for (int n = 0; n < a.length(); n++) {
                    JSONObject o = a.getJSONObject(n);
                    if (o.optInt("c") != channel || o.optLong("e") <= now) continue;
                    Prog p = new Prog();
                    p.s = o.optLong("s");
                    p.e = o.optLong("e");
                    p.g = o.optInt("g", -1);
                    p.n = o.optInt("n");
                    p.t = o.optString("t");
                    p.st = o.optString("st");
                    p.d = o.optString("d");
                    p.k = o.optString("k");
                    p.kk = o.optString("kk");
                    p.i = o.optString("i");
                    p.a = o.optString("a");
                    p.r = o.optString("r");
                    p.y = o.optString("y");
                    out.add(p);
                }
            } catch (Exception broken) {
                // That day is skipped.
            }
        }
        return out;
    }

    private boolean inTab(Prog p, int t) {
        switch (t) {
            case 0: {
                int year = Calendar.getInstance().get(Calendar.YEAR);
                int made = 0;
                try {
                    made = Integer.parseInt(p.y.length() >= 4 ? p.y.substring(0, 4) : "0");
                } catch (NumberFormatException ignored) {
                    // No year.
                }
                boolean kind = p.g == 0 || p.g == 1 || p.g == 4;
                return p.n == 1 || kind && made >= year - 1;
            }
            case 1: return p.g == 0;
            case 2: return p.g == 1;
            case 3: return p.g == 4;
            default: return p.e - p.s >= 10 * 60_000L;
        }
    }

    // ---- tabs and list ---------------------------------------------------------------------------

    private void select(int t) {
        if (t == tab) return;
        tab = t;
        paintTabs();
        Map<String, Entry> grouped = new LinkedHashMap<>();
        for (Prog p : week) {
            if (!inTab(p, t)) continue;
            String key = Tmdb.clean(p.t).toLowerCase(Locale.FRANCE);
            Entry en = grouped.get(key);
            if (en == null) grouped.put(key, en = new Entry(Tmdb.clean(p.t)));
            en.airings.add(p);
        }
        entries.clear();
        entries.addAll(grouped.values());
        list.removeAllViews();
        rows.clear();
        for (int i = 0; i < entries.size(); i++) addRow(i);
        empty.setText(R.string.channel_none);
        empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        detail.setVisibility(entries.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        listScroll.scrollTo(0, 0);
        if (!entries.isEmpty()) show(0);
    }

    private void addRow(int index) {
        Entry en = entries.get(index);
        Prog first = en.airings.get(0);
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(px(18), px(10), px(18), px(10));
        line.setFocusable(true);
        line.setClickable(true);
        TextView t = text(en.title, 26, Typeface.NORMAL, Color.WHITE);
        t.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(t);
        String sub = when.format(new Date(first.s)) + (first.k.isEmpty() ? "" : " · " + first.k)
                + (en.airings.size() > 1 ? " · " + getString(R.string.channel_airings, en.airings.size()) : "");
        line.addView(text(sub, 20, Typeface.NORMAL, 0x99FFFFFF));
        line.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? framed(0x33FFFFFF) : null);
            if (has) show(index);
        });
        line.setOnClickListener(v -> watch.requestFocus());
        line.setOnHoverListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_HOVER_ENTER && !v.hasFocus()) v.setBackground(pill(0x1AFFFFFF));
            else if (e.getAction() == MotionEvent.ACTION_HOVER_EXIT && !v.hasFocus()) v.setBackground(null);
            return false;
        });
        line.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) watch.requestFocus();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP && index == 0) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) tabViews.get(tab).requestFocus();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
        ll.bottomMargin = px(4);
        list.addView(line, ll);
        rows.add(line);
    }

    private void show(int index) {
        Entry en = entries.get(index);
        if (en == shown) return;
        shown = en;
        Prog p = en.airings.get(0);
        title.setText(en.title);
        List<String> bits = new ArrayList<>();
        if (!p.kk.isEmpty()) bits.add(p.kk);
        else if (!p.k.isEmpty()) bits.add(p.k);
        if (!p.y.isEmpty()) bits.add(p.y.length() >= 4 ? p.y.substring(0, 4) : p.y);
        bits.add((p.e - p.s) / 60_000L + " min");
        if (p.n == 1) bits.add(getString(R.string.channel_new));
        meta.setText(TextUtils.join(" · ", bits));
        String who = (p.a.isEmpty() ? "" : getString(R.string.channel_with, p.a))
                + (p.r.isEmpty() ? "" : (p.a.isEmpty() ? "" : "   ") + getString(R.string.channel_by, p.r));
        people.setText(who);
        people.setVisibility(who.isEmpty() ? View.GONE : View.VISIBLE);
        String d = !p.st.isEmpty() && en.airings.size() == 1 ? p.st + " — " + p.d : p.d;
        desc.setText(d.isEmpty() ? getString(R.string.tonight_no_desc) : d);
        List<String> next = new ArrayList<>();
        for (int i = 0; i < Math.min(4, en.airings.size()); i++) next.add(when.format(new Date(en.airings.get(i).s)));
        airings.setText(getString(R.string.channel_next, TextUtils.join("  ·  ", next)));
        picture.setImageDrawable(null);
        String img = "";
        for (Prog a : en.airings) if (a.i.startsWith("https://")) { img = a.i; break; }
        load(picture, img, false);
        recs.removeAllViews();
        recsTitle.setVisibility(View.INVISIBLE);
        main.removeCallbacks(loadRecs);
        if (Tmdb.enabled()) main.postDelayed(loadRecs, 450);
    }

    // ---- recommendations -------------------------------------------------------------------------

    private void recommend() {
        Entry en = shown;
        if (en == null) return;
        int g = en.airings.get(0).g;
        Boolean tv = g == 0 ? Boolean.FALSE : g == 1 ? Boolean.TRUE : null;
        int mine = ++recRequest;
        io.execute(() -> {
            List<Tmdb.Title> found;
            try {
                found = Tmdb.recommendations(en.title, tv);
            } catch (Exception offline) {
                found = new ArrayList<>();
            }
            List<Tmdb.Title> list = found;
            main.post(() -> {
                if (mine != recRequest || shown != en || isFinishing()) return;
                recsTitle.setVisibility(list.isEmpty() ? View.INVISIBLE : View.VISIBLE);
                for (Tmdb.Title t : list) addRec(t);
            });
        });
    }

    private void addRec(Tmdb.Title t) {
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(pill(0x22FFFFFF));
        poster.setClipToOutline(true);
        poster.setFocusable(true);
        poster.setClickable(true);
        poster.setContentDescription(t.name);
        poster.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(120).start();
            v.setForeground(has ? frame() : null);
            if (has) recsTitle.setText(getString(R.string.channel_recs) + " — " + t.name);
        });
        poster.setOnHoverListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_HOVER_ENTER) v.setForeground(frame());
            else if (e.getAction() == MotionEvent.ACTION_HOVER_EXIT && !v.hasFocus()) v.setForeground(null);
            return false;
        });
        poster.setOnClickListener(v -> startActivity(new Intent(this, TitleActivity.class)
                .putExtra("tmdb", t.id).putExtra("tv", t.tv)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(104), px(156));
        lp.rightMargin = px(14);
        recs.addView(poster, lp);
        load(poster, Tmdb.image(t.poster, "w185"), true);
    }

    // ---- small things ----------------------------------------------------------------------------

    private void load(ImageView view, String url, boolean small) {
        if (url == null || url.isEmpty()) return;
        Bitmap hit = cache.get(url);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        view.setTag(url);
        images.execute(() -> {
            Bitmap b = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8_000);
                c.setReadTimeout(10_000);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                try (InputStream in = c.getInputStream()) {
                    b = BitmapFactory.decodeStream(in, null, o);
                }
            } catch (Exception offline) {
                // The frame stays empty.
            } finally {
                if (c != null) c.disconnect();
            }
            Bitmap done = b;
            main.post(() -> {
                if (done == null || isFinishing()) return;
                cache.put(url, done);
                if (url.equals(view.getTag())) view.setImageBitmap(done);
            });
        });
    }

    private void paintTabs() {
        for (int i = 0; i < tabViews.size(); i++) {
            TextView t = (TextView) tabViews.get(i);
            boolean focused = t.hasFocus(), current = i == tab;
            t.setBackground(focused ? pill(Color.WHITE) : current ? pill(0x33FFFFFF) : null);
            t.setTextColor(focused ? Color.BLACK : current ? Color.WHITE : 0x99FFFFFF);
        }
    }

    private TextView button(String label, boolean primary) {
        TextView b = text(label, 24, Typeface.BOLD, primary ? Color.BLACK : Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(28), px(12), px(28), px(12));
        b.setBackground(pill(primary ? Color.WHITE : 0x33FFFFFF));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnFocusChangeListener((v, has) -> {
            v.setForeground(has ? frame() : null);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(120).start();
        });
        b.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && v == watch) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) backToList();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && recs.getChildCount() > 0) recs.getChildAt(0).requestFocus();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) backToList();
                return true;
            }
            return Nav.lastInRow(v, code);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = px(14);
        b.setLayoutParams(lp);
        return b;
    }

    private void backToList() {
        int i = entries.indexOf(shown);
        if (i >= 0 && i < rows.size()) rows.get(i).requestFocus();
    }

    private GradientDrawable frame() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x00000000);
        d.setStroke(px(4), Color.WHITE);
        d.setCornerRadius(px(14));
        return d;
    }

    private GradientDrawable framed(int colour) {
        GradientDrawable d = pill(colour);
        d.setStroke(px(3), Color.WHITE);
        return d;
    }

    private GradientDrawable pill(int colour) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(px(14));
        return d;
    }

    private TextView text(String s, float size, int style, int colour) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTypeface(Typeface.create("sans-serif", style));
        t.setTextColor(colour);
        t.setSingleLine(true);
        return t;
    }

    private int px(float at1080) {
        return Math.round(at1080 * u);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(loadRecs);
        io.shutdownNow();
        images.shutdownNow();
        super.onDestroy();
    }
}
