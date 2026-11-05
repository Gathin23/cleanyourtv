package io.github.anhost77.salon;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The TV guide, full screen, in tabs: En ce moment (every channel's programme now, with its progress and what
 * follows), Ce soir (prime time), then Aujourd'hui, Demain and the next days (the channels on the left, the chosen
 * channel's whole day in the middle). The chosen programme shows on the right with its picture, times, genre and
 * summary, and a Trailer button that searches YouTube for it. D-pad, mouse or pointer. Opened from its own tile or by
 * a click on the launcher's TV panel (extra "index": a row of tonight's panel). Under the tabs, the genre chips
 * (Tout, Films, Séries…) filter any tab; on a day tab a genre lists every channel's programmes of that kind.
 */
public final class TonightActivity extends Activity {
    private static final int NOW = 0, TONIGHT = 1, DAY = 2, SLOT = 3;
    /** The tabs' positions: Ce matin, Cet après-midi, En ce moment, Ce soir, then the days. */
    private static final int TAB_NOW = 2, TAB_TONIGHT = 3;

    private static final class Prog {
        int c, g;
        long s, e;
        String t, st, d, k, i;
    }

    private static final class Tab {
        final int kind;
        final String day;
        final String label;
        /** SLOT: the hours it covers today. */
        int from, to;

        Tab(int kind, String day, String label) {
            this.kind = kind;
            this.day = day;
            this.label = label;
        }

        Tab slot(int from, int to) {
            this.from = from;
            this.to = to;
            return this;
        }
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, List<Prog>> days = new HashMap<>();
    private final List<Tab> tabs = new ArrayList<>();
    private final List<View> tabViews = new ArrayList<>();
    private final List<View> chipViews = new ArrayList<>();
    private final List<View> channelViews = new ArrayList<>();
    private final List<View> rowViews = new ArrayList<>();
    private final List<Prog> rowProgs = new ArrayList<>();
    private final SimpleDateFormat dayKey = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.FRANCE);
    private Bitmap[] logos;
    private LinearLayout channelList, rowList;
    private View channelColumn;
    private ScrollView rowScroll;
    private ImageView picture;
    private TextView title, when, sub, desc, empty;
    private View trailer, detail;
    private TextView watch;
    private Prog shown;
    private int tab = -1;
    /** The genre filter: -1 for Tout, else an index into {@link TvTonight#GENRES}. */
    private int genre = -1;
    private int channel;
    private int target;
    private int imageRequest;
    private float u;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        u = getResources().getDisplayMetrics().heightPixels / 1080f;
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF1B2230, 0xFF0D1118, 0xFF06080C}));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(80), px(46), px(80), px(46));
        root.addView(page);

        // The tabs.
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(0, px(4), 0, px(4));
        tabScroll.addView(tabRow);
        page.addView(tabScroll);

        // The genre chips.
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setPadding(0, px(12), 0, px(4));
        chipScroll.addView(chipRow);
        page.addView(chipScroll);

        LinearLayout body = new LinearLayout(this);
        body.setPadding(0, px(26), 0, 0);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Left: the channels (day tabs only).
        ScrollView chScroll = new ScrollView(this);
        chScroll.setVerticalScrollBarEnabled(false);
        channelList = new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        chScroll.addView(channelList);
        channelColumn = chScroll;
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(170), -1);
        cl.rightMargin = px(18);
        body.addView(chScroll, cl);

        // Middle: the programmes.
        rowScroll = new ScrollView(this);
        rowScroll.setVerticalScrollBarEnabled(false);
        rowList = new LinearLayout(this);
        rowList.setOrientation(LinearLayout.VERTICAL);
        rowList.setPadding(0, 0, px(10), px(10));
        rowScroll.addView(rowList);
        body.addView(rowScroll, new LinearLayout.LayoutParams(0, -1, 0.44f));

        // Right: the programme.
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(px(48), 0, 0, 0);
        picture = new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(0x22FFFFFF);
        frame.setCornerRadius(px(24));
        picture.setBackground(frame);
        picture.setClipToOutline(true);
        right.addView(picture, new LinearLayout.LayoutParams(-1, px(360)));
        title = text("", 42, Typeface.BOLD, Color.WHITE);
        title.setPadding(0, px(22), 0, px(6));
        right.addView(title);
        sub = text("", 27, Typeface.NORMAL, 0xCCFFFFFF);
        right.addView(sub);
        when = text("", 25, Typeface.NORMAL, 0x99FFFFFF);
        when.setPadding(0, px(8), 0, px(14));
        right.addView(when);
        desc = text("", 26, Typeface.NORMAL, 0xE6FFFFFF);
        desc.setSingleLine(false);
        desc.setMaxLines(6);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        desc.setLineSpacing(0, 1.15f);
        right.addView(desc, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView button = text(getString(R.string.tonight_trailer), 27, Typeface.BOLD, Color.BLACK);
        button.setGravity(Gravity.CENTER);
        button.setPadding(px(38), px(16), px(38), px(16));
        button.setBackground(pill(Color.WHITE));
        button.setFocusable(true);
        button.setClickable(true);
        button.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start());
        button.setOnClickListener(v -> trailer());
        trailer = button;
        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(0, px(14), 0, 0);
        watch = text("", 27, Typeface.BOLD, Color.WHITE);
        watch.setGravity(Gravity.CENTER);
        watch.setPadding(px(38), px(16), px(38), px(16));
        watch.setBackground(pill(0x33FFFFFF));
        watch.setFocusable(true);
        watch.setClickable(true);
        watch.setOnFocusChangeListener((v, has) -> {
            v.setBackground(pill(has ? Color.WHITE : 0x33FFFFFF));
            ((TextView) v).setTextColor(has ? Color.BLACK : Color.WHITE);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
        });
        watch.setOnClickListener(v -> watch());
        View.OnKeyListener backToRow = (v, code, e) -> {
            // Nothing under the buttons: Down stays put instead of dropping into the list at the bottom.
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            // Right of the last button: nothing, rather than the day tabs at the top of the screen.
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && v == button) return true;
            boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT && v == watch;
            if (!left && code != KeyEvent.KEYCODE_DPAD_UP) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN) focusRow(rowProgs.indexOf(shown));
            return true;
        };
        watch.setOnKeyListener(backToRow);
        button.setOnKeyListener(backToRow);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.rightMargin = px(20);
        buttons.addView(watch, wl);
        buttons.addView(button);
        right.addView(buttons);
        detail = right;
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 0.56f));

        empty = text(getString(R.string.tonight_empty), 30, Typeface.NORMAL, 0xB3FFFFFF);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        buildTabs(tabRow);
        buildChips(chipRow);
        loadLogos();
        boolean evening = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 18;
        int first = getIntent().hasExtra("index") || evening ? TAB_TONIGHT : TAB_NOW;
        channel = channelForIndex(getIntent().getIntExtra("index", -1));
        select(first, true);
    }

    // ---- tabs ------------------------------------------------------------------------------------

    private void buildTabs(LinearLayout row) {
        tabs.add(new Tab(SLOT, null, getString(R.string.guide_morning)).slot(6, 12));
        tabs.add(new Tab(SLOT, null, getString(R.string.guide_afternoon)).slot(12, 18));
        tabs.add(new Tab(NOW, null, getString(R.string.guide_now)));
        tabs.add(new Tab(TONIGHT, null, getString(R.string.guide_tonight)));
        Calendar day = Calendar.getInstance();
        SimpleDateFormat label = new SimpleDateFormat("EEE d", Locale.FRANCE);
        for (int i = 0; i < 8; i++) {
            String key = dayKey.format(day.getTime());
            if (TvTonight.dayFile(this, key).isFile()) {
                String name = i == 0 ? getString(R.string.guide_today) : i == 1 ? getString(R.string.guide_tomorrow)
                        : label.format(day.getTime());
                tabs.add(new Tab(DAY, key, name.substring(0, 1).toUpperCase(Locale.FRANCE) + name.substring(1)));
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        for (int i = 0; i < tabs.size(); i++) {
            TextView t = text(tabs.get(i).label, 26, Typeface.BOLD, 0xCCFFFFFF);
            t.setPadding(px(28), px(12), px(28), px(12));
            t.setFocusable(true);
            t.setClickable(true);
            final int index = i;
            t.setOnFocusChangeListener((v, has) -> {
                paintTab(index);
                if (has) select(index, false);
            });
            t.setOnClickListener(v -> select(index, false));
            t.setOnKeyListener((v, code, e) -> {
                if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                // Top to bottom, as on screen: the genre chips come before the list.
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    View chip = genre + 1 < chipViews.size() ? chipViews.get(genre + 1) : null;
                    if (chip != null && chip.isShown()) chip.requestFocus();
                    else focusRow(target);
                }
                return true;
            });
            t.setOnHoverListener(this::hover);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = px(10);
            row.addView(t, lp);
            tabViews.add(t);
        }
    }

    private void buildChips(LinearLayout row) {
        for (int i = -1; i < TvTonight.GENRES.length; i++) {
            TextView t = text(i < 0 ? getString(R.string.guide_all) : TvTonight.GENRES[i], 22, Typeface.NORMAL, 0x99FFFFFF);
            t.setPadding(px(22), px(8), px(22), px(8));
            t.setFocusable(true);
            t.setClickable(true);
            final int g = i;
            t.setOnFocusChangeListener((v, has) -> {
                paintChip(g);
                if (has && genre != g) setGenre(g);
            });
            t.setOnClickListener(v -> setGenre(g));
            t.setOnKeyListener((v, code, e) -> {
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN) focusRow(target);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN && tab >= 0) tabViews.get(tab).requestFocus();
                    return true;
                }
                return false;
            });
            t.setOnHoverListener(this::hover);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = px(8);
            row.addView(t, lp);
            chipViews.add(t);
        }
        for (int g = -1; g < TvTonight.GENRES.length; g++) paintChip(g);
    }

    private void paintChip(int g) {
        TextView t = (TextView) chipViews.get(g + 1);
        boolean focused = t.hasFocus(), current = g == genre;
        GradientDrawable d = pill(focused ? Color.WHITE : current ? 0x33FFFFFF : 0x00000000);
        if (!focused && !current) d.setStroke(Math.max(1, px(2)), 0x33FFFFFF);
        t.setBackground(d);
        t.setTextColor(focused ? Color.BLACK : current ? Color.WHITE : 0x99FFFFFF);
    }

    private void setGenre(int g) {
        genre = g;
        for (int i = -1; i < TvTonight.GENRES.length; i++) paintChip(i);
        if (tab >= 0) fill(tabs.get(tab));
    }

    private boolean wanted(Prog p) {
        return genre < 0 || p.g == genre;
    }

    private void paintTab(int i) {
        TextView t = (TextView) tabViews.get(i);
        boolean focused = t.hasFocus(), current = i == tab;
        t.setBackground(focused ? pill(Color.WHITE) : current ? pill(0x33FFFFFF) : null);
        t.setTextColor(focused ? Color.BLACK : current ? Color.WHITE : 0x99FFFFFF);
    }

    /** Shows a tab; focus stays where it is unless {@code focusRow} (the first opening). */
    private void select(int index, boolean focusRow) {
        if (index == tab && !focusRow) return;
        int old = tab;
        tab = index;
        if (old >= 0) paintTab(old);
        paintTab(index);
        Tab t = tabs.get(index);
        List<String> need = new ArrayList<>();
        String today = dayKey.format(new Date());
        if (t.kind == DAY) need.add(t.day);
        else {
            need.add(today);
            if (t.kind == NOW) need.add(yesterday());
        }
        io.execute(() -> {
            for (String d : need) {
                synchronized (days) {
                    if (days.containsKey(d)) continue;
                }
                List<Prog> list = readDay(d);
                synchronized (days) {
                    days.put(d, list);
                }
            }
            main.post(() -> {
                if (tab != index || isFinishing()) return;
                fill(t);
                if (focusRow) focusRow(target);
            });
        });
    }

    // ---- content ---------------------------------------------------------------------------------

    private void fill(Tab t) {
        boolean byChannel = (t.kind == DAY || t.kind == SLOT) && genre < 0;
        channelColumn.setVisibility(byChannel ? View.VISIBLE : View.GONE);
        if (t.kind == DAY && genre >= 0) {
            fillDayGenre(t.day);
            return;
        }
        if (t.kind == SLOT) {
            if (byChannel) {
                fillChannels();
                fillSlotChannel(t);
            } else {
                fillSlot(t);
            }
            return;
        }
        if (t.kind == DAY) {
            fillChannels();
            fillDay(t.day);
            return;
        }
        rowViews.clear();
        rowProgs.clear();
        rowList.removeAllViews();
        long now = System.currentTimeMillis();
        target = 0;
        for (int c = 0; c < TvTonight.CHANNELS.length; c++) {
            if (Settings.off(Settings.CHANNELS, TvTonight.CHANNELS[c][0])) continue; // Réglages
            Prog p = t.kind == NOW ? at(c, now) : tonight(c);
            if (p == null || !wanted(p)) continue;
            if (c == channel) target = rowProgs.size();
            Prog next = t.kind == NOW ? at(c, p.e + 1) : null;
            addRow(c, p, true, t.kind == NOW ? now : 0, next);
        }
        after();
    }

    private void fillChannels() {
        if (!channelViews.isEmpty()) {
            for (int c = 0; c < channelViews.size(); c++) channelViews.get(c).setBackground(c == channel ? pill(0x1FFFFFFF) : null);
            return;
        }
        for (int c = 0; c < TvTonight.CHANNELS.length; c++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(px(14), px(12), px(14), px(12));
            cell.setFocusable(true);
            cell.setClickable(true);
            cell.addView(logoView(c), new LinearLayout.LayoutParams(px(120), px(40)));
            // Switched off in Réglages: kept (indexes stay channel numbers) but out of sight and out of the D-pad.
            if (Settings.off(Settings.CHANNELS, TvTonight.CHANNELS[c][0])) cell.setVisibility(View.GONE);
            final int index = c;
            cell.setOnFocusChangeListener((v, has) -> {
                v.setBackground(has ? framed(0x40FFFFFF) : index == channel ? pill(0x1FFFFFFF) : null);
                if (has && channel != index) {
                    int old = channel;
                    channel = index;
                    if (old < channelViews.size()) channelViews.get(old).setBackground(null);
                    refillChannel();
                }
            });
            // OK on a channel: its page (Nouveautés, Films, Séries, Documentaires).
            cell.setOnClickListener(v -> startActivity(new Intent(this, ChannelActivity.class).putExtra("channel", index)));
            cell.setOnKeyListener((v, code, e) -> {
                if (code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                if (e.getAction() == KeyEvent.ACTION_DOWN) focusRow(target);
                return true;
            });
            cell.setOnHoverListener(this::hover);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = px(4);
            channelList.addView(cell, lp);
            channelViews.add(cell);
        }
        channelViews.get(channel).setBackground(pill(0x1FFFFFFF));
    }

    private void fillDay(String day) {
        rowViews.clear();
        rowProgs.clear();
        rowList.removeAllViews();
        List<Prog> list;
        synchronized (days) {
            list = days.get(day);
        }
        long now = System.currentTimeMillis();
        boolean today = day.equals(dayKey.format(new Date()));
        long evening = at(day, 20, 50);
        target = -1;
        if (list != null) {
            for (Prog p : list) {
                if (p.c != channel) continue;
                // Today: the programme on air. Other days: the evening's main programme, not the 5-minute weather.
                if (target < 0 && (today ? p.e > now : p.s >= evening && p.e - p.s >= 30 * 60_000L)) target = rowProgs.size();
                addRow(-1, p, false, today ? now : 0, null);
            }
        }
        if (target < 0) target = Math.max(0, rowProgs.size() - 1);
        after();
    }

    /** The chosen channel changed: its day, or its morning / afternoon. */
    private void refillChannel() {
        Tab t = tabs.get(tab);
        if (t.kind == SLOT) fillSlotChannel(t);
        else fillDay(t.day);
    }

    /** Ce matin / Cet après-midi for the chosen channel: everything it shows in those hours today. */
    private void fillSlotChannel(Tab t) {
        rowViews.clear();
        rowProgs.clear();
        rowList.removeAllViews();
        String today = dayKey.format(new Date());
        List<Prog> list;
        synchronized (days) {
            list = days.get(today);
        }
        long from = at(today, t.from, 0), to = at(today, t.to, 0), now = System.currentTimeMillis();
        target = -1;
        if (list != null) {
            for (Prog p : list) {
                if (p.c != channel || p.e <= from || p.s >= to) continue;
                if (target < 0 && p.e > now) target = rowProgs.size();
                addRow(-1, p, false, now, null);
            }
        }
        if (target < 0) target = 0;
        after();
    }

    /** Ce matin / Cet après-midi with a genre: every channel's programmes of 20 minutes or more starting in those hours today,
     *  channel by channel; the target is the one on air now, if any. */
    private void fillSlot(Tab t) {
        rowViews.clear();
        rowProgs.clear();
        rowList.removeAllViews();
        String today = dayKey.format(new Date());
        List<Prog> list;
        synchronized (days) {
            list = days.get(today);
        }
        long from = at(today, t.from, 0), to = at(today, t.to, 0), now = System.currentTimeMillis();
        target = 0;
        if (list != null) {
            for (Prog p : list) {
                if (p.s < from || p.s >= to || p.e - p.s < 20 * 60_000L || !wanted(p)) continue;
                if (p.s <= now && now < p.e && target == 0) target = rowProgs.size();
                addRow(p.c, p, true, now, null);
            }
        }
        after();
    }

    /** A day tab with a genre: every channel's programmes of that kind, in time order, with their logos. */
    private void fillDayGenre(String day) {
        rowViews.clear();
        rowProgs.clear();
        rowList.removeAllViews();
        List<Prog> list;
        synchronized (days) {
            list = days.get(day);
        }
        List<Prog> picked = new ArrayList<>();
        if (list != null) for (Prog p : list) if (wanted(p)) picked.add(p);
        picked.sort((a, b) -> a.s != b.s ? Long.compare(a.s, b.s) : Integer.compare(a.c, b.c));
        long now = System.currentTimeMillis();
        boolean today = day.equals(dayKey.format(new Date()));
        long evening = at(day, 20, 50);
        target = -1;
        for (Prog p : picked) {
            if (target < 0 && (today ? p.e > now : p.s >= evening)) target = rowProgs.size();
            addRow(p.c, p, true, today ? now : 0, null);
        }
        if (target < 0) target = Math.max(0, rowProgs.size() - 1);
        after();
    }

    /** One programme row: the channel's logo (lists of channels), the times, the title, and for En ce moment the
     *  progress and what follows. */
    private void addRow(int c, Prog p, boolean withLogo, long now, Prog next) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(px(16), px(10), px(16), px(10));
        line.setFocusable(true);
        line.setClickable(true);
        if (withLogo) {
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(px(104), px(38));
            cell.rightMargin = px(18);
            line.addView(logoView(c), cell);
        }
        line.addView(text(clock.format(new Date(p.s)), 24, Typeface.NORMAL,
                now > 0 && p.e <= now ? 0x66FFFFFF : 0xB3FFFFFF), new LinearLayout.LayoutParams(px(82), -2));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(p.t, 26, Typeface.NORMAL, now > 0 && p.e <= now ? 0x80FFFFFF : Color.WHITE);
        t.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(t);
        if (now > 0 && p.s <= now && now < p.e) {
            LinearLayout bar = new LinearLayout(this);
            bar.setBackground(pill(0x33FFFFFF));
            View done = new View(this);
            done.setBackground(pill(Color.WHITE));
            float part = (now - p.s) / (float) (p.e - p.s);
            bar.addView(done, new LinearLayout.LayoutParams(0, -1, Math.max(0.02f, part)));
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, -1, Math.max(0.02f, 1 - part)));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, px(5));
            barLp.topMargin = px(6);
            barLp.bottomMargin = px(2);
            words.addView(bar, barLp);
        }
        if (next != null) {
            TextView n = text(getString(R.string.guide_next, clock.format(new Date(next.s)), next.t), 21,
                    Typeface.NORMAL, 0x80FFFFFF);
            n.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(n);
        }
        line.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        final int index = rowProgs.size();
        line.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? framed(0x33FFFFFF) : null);
            if (has) show(index);
        });
        line.setOnClickListener(v -> {
            if (shown == rowProgs.get(index)) trailer.requestFocus();
            show(index);
        });
        line.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && channelColumn.getVisibility() == View.VISIBLE) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && channel < channelViews.size()) channelViews.get(channel).requestFocus();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // Right always reaches the programme's buttons, never the chips above.
                if (e.getAction() == KeyEvent.ACTION_DOWN) watch.requestFocus();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP && index == 0) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) chipViews.get(genre + 1).requestFocus();
                return true;
            }
            return false;
        });
        line.setOnHoverListener(this::hover);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
        ll.bottomMargin = px(4);
        rowList.addView(line, ll);
        rowViews.add(line);
        rowProgs.add(p);
    }

    private void after() {
        boolean none = rowProgs.isEmpty();
        boolean loaded;
        synchronized (days) {
            loaded = days.containsKey(dayKey.format(new Date())) && !days.get(dayKey.format(new Date())).isEmpty();
        }
        empty.setText(loaded ? R.string.guide_none : R.string.tonight_empty);
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        detail.setVisibility(none ? View.INVISIBLE : View.VISIBLE);
        if (none && !loaded) {
            getContentResolver().query(TvTonight.URI, null, null, null, null); // starts a fetch for next time
            return;
        }
        if (none) {
            return;
        }
        show(target);
        // Bring the target row into view without taking focus from the tabs or the channels.
        rowScroll.post(() -> {
            if (target < rowViews.size()) rowScroll.scrollTo(0, Math.max(0, rowViews.get(target).getTop() - px(120)));
        });
    }

    private void focusRow(int i) {
        if (rowViews.isEmpty()) return;
        View v = rowViews.get(Math.max(0, Math.min(rowViews.size() - 1, i)));
        v.requestFocus();
    }

    private void show(int index) {
        if (index < 0 || index >= rowProgs.size()) return;
        Prog p = rowProgs.get(index);
        if (p == shown) return;
        shown = p;
        title.setText(p.t);
        sub.setText(p.st);
        sub.setVisibility(p.st.isEmpty() ? View.GONE : View.VISIBLE);
        String day = new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(new Date(p.s));
        when.setText(TvTonight.CHANNELS[p.c][1] + " · " + day + " · " + clock.format(new Date(p.s)) + " – "
                + clock.format(new Date(p.e)) + (p.k.isEmpty() ? "" : " · " + p.k));
        desc.setText(p.d.isEmpty() ? getString(R.string.tonight_no_desc) : p.d);
        watch.setText(getString(R.string.guide_watch, Watch.appName(this, p.c)));
        picture.setImageDrawable(null);
        int request = ++imageRequest;
        String image = p.i;
        if (!image.startsWith("https://")) return;
        io.execute(() -> {
            Bitmap b = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(image).openConnection();
                c.setConnectTimeout(8_000);
                c.setReadTimeout(10_000);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                try (InputStream in = c.getInputStream()) {
                    b = BitmapFactory.decodeStream(in, null, o);
                }
            } catch (Exception offline) {
                // No picture: the frame stays empty.
            } finally {
                if (c != null) c.disconnect();
            }
            Bitmap done = b;
            main.post(() -> {
                if (request == imageRequest && done != null) picture.setImageBitmap(done);
            });
        });
    }

    // ---- data ------------------------------------------------------------------------------------

    private List<Prog> readDay(String day) {
        List<Prog> out = new ArrayList<>();
        File f = TvTonight.dayFile(this, day);
        if (!f.isFile()) return out;
        try {
            JSONArray a = new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (int n = 0; n < a.length(); n++) {
                JSONObject o = a.getJSONObject(n);
                Prog p = new Prog();
                p.c = o.getInt("c");
                p.g = o.optInt("g", -1);
                p.s = o.getLong("s");
                p.e = o.getLong("e");
                p.t = o.optString("t");
                p.st = o.optString("st");
                p.d = o.optString("d");
                p.k = o.optString("k");
                p.i = o.optString("i");
                if (p.c >= 0 && p.c < TvTonight.CHANNELS.length) out.add(p);
            }
        } catch (Exception broken) {
            out.clear();
        }
        return out;
    }

    /** The channel's programme on air at {@code ms}, from yesterday's and today's files. */
    private Prog at(int c, long ms) {
        for (String d : new String[] {dayKey.format(new Date(ms)), yesterday()}) {
            List<Prog> list;
            synchronized (days) {
                list = days.get(d);
            }
            if (list == null) continue;
            for (Prog p : list) if (p.c == c && p.s <= ms && ms < p.e) return p;
        }
        return null;
    }

    /** Prime time: the first programme of 30 minutes or more starting between 20:50 and 22:00 today. */
    private Prog tonight(int c) {
        List<Prog> list;
        synchronized (days) {
            list = days.get(dayKey.format(new Date()));
        }
        if (list == null) return null;
        long from = at(dayKey.format(new Date()), 20, 50), to = from + 70 * 60_000L;
        for (Prog p : list) if (p.c == c && p.s >= from && p.s < to && p.e - p.s >= 30 * 60_000L) return p;
        return null;
    }

    private long at(String day, int hour, int minute) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(dayKey.parse(day));
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private String yesterday() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return dayKey.format(cal.getTime());
    }

    /** The launcher's panel row {@code index} (tonight's rows, in channel order) as a channel. */
    private int channelForIndex(int index) {
        if (index < 0) return 0;
        SharedPreferences cache = getSharedPreferences("tv", 0);
        try {
            String id = new JSONArray(cache.getString("rows", "[]")).getJSONObject(index).optString("id");
            for (int c = 0; c < TvTonight.CHANNELS.length; c++) if (TvTonight.CHANNELS[c][0].equals(id)) return c;
        } catch (Exception gone) {
            // The panel changed since: the first channel.
        }
        return 0;
    }

    private void loadLogos() {
        logos = new Bitmap[TvTonight.CHANNELS.length];
        File dir = new File(getFilesDir(), "logos2");
        for (int c = 0; c < logos.length; c++) {
            logos[c] = BitmapFactory.decodeFile(new File(dir, TvTonight.CHANNELS[c][0] + ".png").getPath());
        }
    }

    private View logoView(int c) {
        if (logos[c] == null) return text(TvTonight.CHANNELS[c][1], 22, Typeface.BOLD, Color.WHITE);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(logos[c]);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    // ---- small things ----------------------------------------------------------------------------

    /** The trailer, played inside the app ({@link TrailerActivity}: TMDB, else a YouTube search). */
    private void trailer() {
        if (shown == null) return;
        startActivity(new Intent(this, TrailerActivity.class).putExtra("title", shown.t));
    }

    /** The channel, live, in the app that carries it ({@link Watch}). */
    private void watch() {
        if (shown != null) Watch.open(this, shown.c);
    }

    private boolean hover(View v, MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_HOVER_ENTER && !v.hasFocus()) v.setAlpha(0.8f);
        else if (e.getAction() == MotionEvent.ACTION_HOVER_EXIT) v.setAlpha(1f);
        return false;
    }

    /** The focused row: its tint and a white frame, so the remote's place is plain to see. */
    private GradientDrawable framed(int colour) {
        GradientDrawable d = pill(colour);
        d.setStroke(px(3), Color.WHITE);
        return d;
    }

    private GradientDrawable pill(int colour) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(px(18));
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

    /** Already open (its own task): a click on another row of the launcher's panel shows that channel tonight. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!intent.hasExtra("index")) return;
        channel = channelForIndex(intent.getIntExtra("index", 0));
        tab = -1;
        select(TAB_TONIGHT, true);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
