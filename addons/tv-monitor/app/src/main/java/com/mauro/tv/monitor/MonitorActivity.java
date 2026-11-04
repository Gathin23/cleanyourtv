package com.mauro.tv.monitor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * TV health in the launcher's own look: black, thin type, icons before words, one colour per verdict. The launcher
 * hands over its background and status colours as intent extras; opened on its own, the app uses the same palette.
 */
public final class MonitorActivity extends Activity {
    private static final int REFRESH_MS = 2000;
    private static final String X = "com.gathin.tvhome.extra.";
    private static final String TAG = "TvMonitor";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread worker;
    private volatile Handler background;
    private SharedPreferences prefs;

    private int bg = Color.BLACK, okColor = 0xFF34C759, warnColor = 0xFFFF9F0A, badColor = 0xFFFF453A;
    private final int ink = Color.WHITE, dim = Color.argb(140, 255, 255, 255);

    private LinearLayout column, chips, flaggedBox, aiBox, suggestedBox, journalBox;
    private ImageView statusIcon, networkIcon;
    private TextView headline, networkText, uptimeText, flaggedCount, aiTime, suggestedCount, journalCount, caption, askHint;
    private Meter ram, storage;
    private View askButton;
    private final List<String[]> flagged = new ArrayList<>();
    private final List<String[]> suggested = new ArrayList<>();
    private String lastAi = "", lastSuggestionKey = "";
    private ImageView usbButton;
    private static final int USB_MINUTES = 30;
    private long waitingSince, rebootAt;
    private LinearLayout confirmBox;
    private boolean manualShown;
    private ScrollView scroll;
    private android.widget.FrameLayout overlay;
    private LinearLayout card;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("journal", MODE_PRIVATE);
        Intent in = getIntent();
        bg = in.getIntExtra(X + "BG", bg);
        okColor = in.getIntExtra(X + "OK", okColor);
        warnColor = in.getIntExtra(X + "WARN", warnColor);
        badColor = in.getIntExtra(X + "BAD", badColor);

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setVerticalScrollBarEnabled(false);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(120, 64, 120, 64);
        scroll.addView(column);

        // A question from the assistant is shown in this box, over everything else, so it cannot be missed.
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(scroll);
        overlay = new android.widget.FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(215, 0, 0, 0));
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(56, 40, 56, 40);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.argb(255, 28, 28, 30));
        cardBg.setCornerRadius(28);
        cardBg.setStroke(3, warnColor);
        card.setBackground(cardBg);
        android.widget.FrameLayout.LayoutParams cardLp = new android.widget.FrameLayout.LayoutParams(1240, -2);
        cardLp.gravity = Gravity.CENTER;
        overlay.addView(card, cardLp);
        root.addView(overlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        // Verdict: one big icon and a short word; the reasons are icons only.
        LinearLayout top = row();
        statusIcon = icon(R.drawable.ic_ok, okColor, 84);
        headline = label("", 56, ink, 0f);
        headline.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        headline.setPadding(28, 0, 0, 0);
        top.addView(statusIcon);
        top.addView(headline);
        column.addView(top);
        chips = row();
        chips.setPadding(0, 12, 0, 0);
        column.addView(chips);

        // Actions: three icon buttons, the focused one names itself underneath.
        LinearLayout bar = row();
        bar.setPadding(0, 36, 0, 0);
        askButton = pcButton(R.drawable.ic_ai, getString(R.string.ai_ask), this::askAssistant);
        bar.addView(askButton);
        bar.addView(iconButton(R.drawable.ic_history, getString(R.string.history_title), () -> {
            Intent i = new Intent(this, HistoryActivity.class);
            i.putExtra(X + "BG", bg).putExtra(X + "OK", okColor).putExtra(X + "WARN", warnColor).putExtra(X + "BAD", badColor);
            startActivity(i);
        }));
        bar.addView(pcButton(R.drawable.ic_list, getString(R.string.details_button), () -> {
            Intent i = new Intent(this, DetailsActivity.class);
            i.putExtra(X + "BG", bg).putExtra(X + "OK", okColor).putExtra(X + "WARN", warnColor).putExtra(X + "BAD", badColor);
            startActivity(i);
        }));
        bar.addView(iconButton(R.drawable.ic_broom, getString(R.string.clear_cache), () -> open(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))));
        bar.addView(pcButton(R.drawable.ic_power, getString(R.string.reboot), this::askReboot));
        usbButton = (ImageView) iconButton(R.drawable.ic_usb, getString(R.string.usb_label), this::askUsb);
        bar.addView(usbButton);
        bar.addView(iconButton(R.drawable.ic_help, getString(R.string.help_title), () -> {
            Intent i = new Intent(this, HelpActivity.class);
            i.putExtra(X + "BG", bg).putExtra(X + "OK", okColor).putExtra(X + "WARN", warnColor);
            startActivity(i);
        }));
        column.addView(bar);
        caption = label("", 16, dim, 0f);
        column.addView(caption);
        // The key to the monitor on a button: always shown, so it is clear without focusing anything.
        LinearLayout legend = row();
        legend.addView(icon(R.drawable.ic_pc, warnColor, 24));
        TextView legendText = label(getString(R.string.pc_legend), 14, dim, 0f);
        legendText.setPadding(10, 0, 0, 0);
        legend.addView(legendText);
        column.addView(legend);
        askHint = label("", 15, dim, 0f);
        column.addView(askHint);
        confirmBox = vertical();
        column.addView(confirmBox);

        gap(28);
        ram = new Meter(this, R.drawable.ic_ram);
        storage = new Meter(this, R.drawable.ic_disk);
        column.addView(ram, new LinearLayout.LayoutParams(-1, 76));
        column.addView(storage, new LinearLayout.LayoutParams(-1, 76));

        LinearLayout info = row();
        info.setPadding(0, 8, 0, 0);
        networkIcon = icon(R.drawable.ic_ethernet, dim, 26);
        networkText = label("", 18, dim, 0f);
        networkText.setPadding(10, 0, 40, 0);
        info.addView(networkIcon);
        info.addView(networkText);
        info.addView(icon(R.drawable.ic_clock, dim, 26));
        uptimeText = label("", 18, dim, 0f);
        uptimeText.setPadding(10, 0, 0, 0);
        info.addView(uptimeText);
        column.addView(info);

        loadList(R.raw.flagged, flagged);
        loadList(R.raw.suggested, suggested);

        gap(28);
        LinearLayout fl = row();
        fl.addView(icon(R.drawable.ic_shield, dim, 26));
        flaggedCount = label("", 18, dim, 0f);
        flaggedCount.setPadding(10, 0, 0, 0);
        fl.addView(flaggedCount);
        column.addView(fl);
        flaggedBox = vertical();
        column.addView(flaggedBox);

        gap(28);
        LinearLayout ai = row();
        ai.addView(icon(R.drawable.ic_ai, dim, 26));
        aiTime = label("", 16, dim, 0f);
        aiTime.setPadding(10, 0, 0, 0);
        ai.addView(aiTime);
        column.addView(ai);
        aiBox = vertical();
        column.addView(aiBox);

        gap(28);
        LinearLayout sg = row();
        sg.addView(icon(R.drawable.ic_list, dim, 26));
        suggestedCount = label("", 18, dim, 0f);
        suggestedCount.setPadding(10, 0, 40, 0);
        sg.addView(suggestedCount);
        sg.addView(icon(R.drawable.ic_warn, warnColor, 22));
        TextView safe = label(getString(R.string.safety_short), 15, dim, 0f);
        safe.setPadding(8, 0, 0, 0);
        sg.addView(safe);
        column.addView(sg);
        suggestedBox = vertical();
        column.addView(suggestedBox);
        LinearLayout jn = row();
        jn.setPadding(0, 28, 0, 0);
        jn.addView(icon(R.drawable.ic_undo, dim, 26));
        journalCount = label("", 18, dim, 0f);
        journalCount.setPadding(10, 0, 0, 0);
        jn.addView(journalCount);
        column.addView(jn);
        journalBox = vertical();
        column.addView(journalBox);
        askButton.requestFocus();
    }

    // ---- small view helpers -------------------------------------------------------------------------------

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void gap(int px) {
        column.addView(new View(this), new LinearLayout.LayoutParams(-1, px));
    }

    private TextView label(String value, int size, int color, float spacing) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.START);
        v.setLetterSpacing(spacing);
        v.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        v.setPadding(0, 6, 0, 6);
        return v;
    }

    private ImageView icon(int res, int color, int sizePx) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        v.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        return v;
    }

    private StateListDrawable focusBackground() {
        StateListDrawable d = new StateListDrawable();
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(Color.argb(46, 255, 255, 255));
        focus.setCornerRadius(18);
        d.addState(new int[] {android.R.attr.state_focused}, focus);
        return d;
    }

    /** A square icon button; when it takes focus its name appears under the bar, so no word sits on the button. */
    private View iconButton(int res, final String name, final Runnable run) {
        ImageView v = icon(res, ink, 52);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(104, 104);
        lp.setMargins(0, 0, 20, 0);
        v.setLayoutParams(lp);
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setPadding(26, 26, 26, 26);
        v.setBackground(focusBackground());
        v.setFocusable(true);
        v.setClickable(true);
        v.setContentDescription(name);
        v.setOnFocusChangeListener((x, has) -> { if (has) setCaption(name, false); });
        v.setOnClickListener(x -> run.run());
        return v;
    }

    /**
     * An icon button for something that only works with the PC: the same button with a small monitor on its corner,
     * and its caption says so. Without the monitor, a button works on the TV alone.
     */
    private View pcButton(int res, final String name, final Runnable run) {
        View v = iconButton(res, name, run);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setLayoutParams(lp);
        v.setLayoutParams(new android.widget.FrameLayout.LayoutParams(104, 104));
        frame.addView(v);
        ImageView badge = icon(R.drawable.ic_pc, warnColor, 30);
        android.widget.FrameLayout.LayoutParams bl = new android.widget.FrameLayout.LayoutParams(30, 30);
        bl.gravity = Gravity.BOTTOM | Gravity.END;
        bl.setMargins(0, 0, 8, 8);
        frame.addView(badge, bl);
        v.setOnFocusChangeListener((x, has) -> { if (has) setCaption(name, true); });
        return frame;
    }

    /** The focused button's name under the bar; for a PC-only one, "needs the PC" with it. */
    private void setCaption(String name, boolean needsPc) {
        caption.setText(needsPc ? name + "   " + getString(R.string.needs_pc) : name);
    }

    /** A focusable line: an icon, a short text, and an action. */
    private View lineButton(int res, int iconColor, String text, int textColor, final Runnable run) {
        LinearLayout l = row();
        l.setPadding(18, 10, 18, 10);
        l.addView(icon(res, iconColor, 30));
        TextView t = label(text, 18, textColor, 0f);
        t.setPadding(16, 0, 0, 0);
        l.addView(t);
        l.setBackground(focusBackground());
        l.setFocusable(true);
        l.setClickable(true);
        l.setOnClickListener(x -> run.run());
        return l;
    }

    // ---- what the PC assistant needs, said before asking it ---------------------------------------------

    private boolean adbOn() {
        try {
            return Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean canToggleAdb() {
        return checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    /** Two icon lines: USB debugging (green when on, red when off, with a way to turn it on) and the PC command. */
    private void addPcNotice() {
        boolean on = adbOn();
        LinearLayout usb = row();
        usb.setPadding(0, 12, 0, 0);
        usb.addView(icon(R.drawable.ic_usb, on ? okColor : badColor, 32));
        TextView u = label(getString(R.string.usb_label) + (on ? "" : "  —  " + getString(R.string.usb_off_state)), 20, on ? ink : badColor, 0f);
        u.setPadding(14, 0, 0, 0);
        usb.addView(u);
        confirmBox.addView(usb);
        if (!on) {
            confirmBox.addView(lineButton(R.drawable.ic_usb, warnColor, getString(R.string.usb_turn_on), ink, this::askUsb));
        }
        LinearLayout pc = row();
        pc.addView(icon(R.drawable.ic_pc, dim, 32));
        TextView c = label("tv_ai_agent.py --watch", 18, dim, 0f);
        c.setPadding(14, 0, 0, 0);
        pc.addView(c);
        confirmBox.addView(pc);
    }

    /**
     * The PC only hears us over ADB. With USB debugging off, ask to turn it on before anything is sent;
     * if the owner refuses (or the one-time grant is missing) nothing is sent and the screen says so.
     */
    private void needUsb(Runnable send) {
        if (adbOn()) {
            send.run();
            return;
        }
        confirmBox.removeAllViews();
        LinearLayout q = row();
        q.setPadding(0, 12, 0, 0);
        q.addView(icon(R.drawable.ic_usb, badColor, 36));
        TextView t = label(getString(R.string.usb_needed_question, USB_MINUTES), 22, ink, 0f);
        t.setPadding(16, 0, 0, 0);
        q.addView(t);
        confirmBox.addView(q);
        LinearLayout b = row();
        View no = lineButton(R.drawable.ic_close, dim, getString(R.string.ai_not_now), ink, this::noRequestSent);
        b.addView(no);
        if (canToggleAdb()) {
            b.addView(lineButton(R.drawable.ic_check, warnColor, getString(R.string.usb_enable_send), ink, () -> {
                setAdb(true);
                prefs.edit().putBoolean(ReportReceiver.CLOSE_ADB, true).apply();   // closed again when the answer arrives
                refreshUsbIcon();
                confirmBox.removeAllViews();
                send.run();
            }));
        } else {
            b.addView(lineButton(R.drawable.ic_usb, ink, getString(R.string.usb_open_settings), ink,
                    () -> open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))));
        }
        confirmBox.addView(b);
        if (!canToggleAdb()) confirmBox.addView(label(getString(R.string.usb_grant_hint), 15, dim, 0f));
        no.requestFocus();
    }

    private void noRequestSent() {
        confirmBox.removeAllViews();
        LinearLayout r = row();
        r.setPadding(0, 12, 0, 0);
        r.addView(icon(R.drawable.ic_usb, badColor, 32));
        TextView t = label(getString(R.string.usb_refused), 20, ink, 0f);
        t.setPadding(14, 0, 0, 0);
        r.addView(t);
        confirmBox.addView(r);
    }

    /** Asking the assistant needs the PC: say so first, then let the owner go on. */
    private void askAssistant() {
        confirmBox.removeAllViews();
        manualShown = false;
        addPcNotice();
        LinearLayout b = row();
        View no = lineButton(R.drawable.ic_close, dim, getString(R.string.ai_not_now), ink, () -> confirmBox.removeAllViews());
        b.addView(no);
        b.addView(lineButton(R.drawable.ic_check, okColor, getString(R.string.ai_go), ink, () -> needUsb(() -> {
            confirmBox.removeAllViews();
            waitingSince = System.currentTimeMillis();
            android.util.Log.i(TAG, "REQUEST " + waitingSince);
            lastAi = "";
            showAi(true);
        })));
        confirmBox.addView(b);
        no.requestFocus();
    }

    /** Turns USB debugging on for a limited time, or off, from here. Needs a one-time grant; see usb_grant_hint. */
    private void askUsb() {
        confirmBox.removeAllViews();
        boolean on = adbOn();
        LinearLayout q = row();
        q.setPadding(0, 12, 0, 0);
        q.addView(icon(R.drawable.ic_usb, warnColor, 36));
        TextView t = label(on ? getString(R.string.usb_off_question)
                : getString(R.string.usb_on_question, USB_MINUTES), 22, ink, 0f);
        t.setPadding(16, 0, 0, 0);
        q.addView(t);
        confirmBox.addView(q);
        if (!canToggleAdb()) {
            confirmBox.addView(label(getString(R.string.usb_grant_hint), 15, dim, 0f));
            LinearLayout b = row();
            View no = lineButton(R.drawable.ic_close, dim, getString(R.string.ai_not_now), ink, () -> confirmBox.removeAllViews());
            b.addView(no);
            b.addView(lineButton(R.drawable.ic_usb, ink, getString(R.string.usb_open_settings), ink,
                    () -> open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))));
            confirmBox.addView(b);
            no.requestFocus();
            return;
        }
        LinearLayout b = row();
        View no = lineButton(R.drawable.ic_close, dim, getString(R.string.ai_not_now), ink, () -> confirmBox.removeAllViews());
        b.addView(no);
        b.addView(lineButton(R.drawable.ic_check, on ? okColor : warnColor, getString(on ? R.string.usb_turn_off : R.string.usb_turn_on), ink, () -> {
            setAdb(!on);
            confirmBox.removeAllViews();
            refreshUsbIcon();
        }));
        confirmBox.addView(b);
        no.requestFocus();
    }

    /** USB debugging on for USB_MINUTES, then an alarm turns it off by itself; off cancels the alarm. */
    private void setAdb(boolean on) {
        AdbOffReceiver.set(this, on);
        if (!on) prefs.edit().putBoolean(ReportReceiver.CLOSE_ADB, false).apply();
    }

    private void refreshUsbIcon() {
        usbButton.setColorFilter(adbOn() ? warnColor : ink, PorterDuff.Mode.SRC_IN);
    }

    /** Restart asks first, and the default answer is No. The PC assistant does the restart over ADB. */
    private void askReboot() {
        confirmBox.removeAllViews();
        manualShown = false;
        addPcNotice();
        LinearLayout q = row();
        q.setPadding(0, 12, 0, 0);
        q.addView(icon(R.drawable.ic_power, warnColor, 36));
        TextView t = label(getString(R.string.reboot_question), 24, ink, 0f);
        t.setPadding(16, 0, 0, 0);
        q.addView(t);
        confirmBox.addView(q);
        LinearLayout b = row();
        View no = lineButton(R.drawable.ic_close, dim, getString(R.string.ai_not_now), ink, () -> confirmBox.removeAllViews());
        b.addView(no);
        b.addView(lineButton(R.drawable.ic_check, badColor, getString(R.string.reboot_yes), ink, () -> needUsb(() -> {
            confirmBox.removeAllViews();
            rebootAt = System.currentTimeMillis();
            android.util.Log.i(TAG, "REBOOT " + rebootAt);
            lastAi = "";
            showAi(true);
        })));
        confirmBox.addView(b);
        no.requestFocus();
    }

    private void open(Intent target) {
        try {
            startActivity(target);
        } catch (RuntimeException ignored) { }
    }

    private Intent appInfo(String pkg) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
    }

    private void loadList(int resource, List<String[]> into) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(getResources().openRawResource(resource)))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.split("\\|", 3);
                if (p.length == 3 && !p[0].isEmpty()) into.add(p);
            }
        } catch (IOException | RuntimeException ignored) { }
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            showAi(true);
            ui.postDelayed(this, 1000);
        }
    };

    @Override public void onResume() {
        super.onResume();
        ui.postDelayed(tick, 1000);
        worker = new HandlerThread("tv-monitor");
        worker.start();
        final Handler current = new Handler(worker.getLooper());
        background = current;
        current.post(new Runnable() {
            @Override public void run() {
                if (background != current) return;
                final Reading r = sample();
                ui.post(() -> { if (background == current) show(r); });
                if (background == current) current.postDelayed(this, REFRESH_MS);
            }
        });
    }

    @Override public void onPause() {
        background = null;
        if (worker != null) { worker.quitSafely(); worker = null; }
        ui.removeCallbacksAndMessages(null);
        super.onPause();
    }

    // ---- sampling and the verdict -------------------------------------------------------------------------

    private static final class Reading {
        float ram = -1, storage = -1;
        String ramText = "", storageText = "", network = "", uptime = "";
        boolean ethernet;
        final List<String[]> active = new ArrayList<>();
        final List<String[]> suggestedOn = new ArrayList<>();
        final List<String[]> suggestedOff = new ArrayList<>();
        final List<Long> suggestedOffAt = new ArrayList<>();
        int total;
    }

    private Reading sample() {
        Reading r = new Reading();
        long[] mem = readMemory();
        if (mem[1] > 0) {
            long used = Math.max(0, mem[1] - mem[0]);
            r.ram = 100f * used / mem[1];
            r.ramText = used + " / " + mem[1] + " MB";
        }
        File data = Environment.getDataDirectory();
        long total = data.getTotalSpace();
        if (total > 0) {
            long used = total - data.getFreeSpace();
            r.storage = 100f * used / total;
            r.storageText = gb(used) + " / " + gb(total);
        }
        r.network = networkName();
        r.ethernet = !r.network.equals("Wi-Fi");
        long up = SystemClock.elapsedRealtime() / 1000;
        r.uptime = up >= 86400 ? String.format("%dd %02dh", up / 86400, (up / 3600) % 24)
                : String.format("%dh %02dm", up / 3600, (up / 60) % 60);
        PackageManager pm = getPackageManager();
        r.total = flagged.size();
        for (String[] f : flagged) {
            try {
                ApplicationInfo a = pm.getApplicationInfo(f[0], PackageManager.MATCH_DISABLED_COMPONENTS);
                if (a.enabled) r.active.add(f);
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
        }
        SharedPreferences.Editor edit = prefs.edit();
        for (String[] s : suggested) {
            String key = "off_" + s[0];
            try {
                ApplicationInfo a = pm.getApplicationInfo(s[0], PackageManager.MATCH_DISABLED_COMPONENTS);
                if (a.enabled) {
                    r.suggestedOn.add(s);
                    if (prefs.contains(key)) edit.remove(key);
                } else {
                    long at = prefs.getLong(key, 0);
                    if (at == 0) { at = System.currentTimeMillis(); edit.putLong(key, at); }
                    r.suggestedOff.add(s);
                    r.suggestedOffAt.add(at);
                }
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
        }
        edit.apply();
        return r;
    }

    private void show(Reading r) {
        AdbOffReceiver.closeIfOverdue(this);
        int ramLevel = level(r.ram < 0 ? -1 : 100 - r.ram, 15, 8);
        int storageLevel = level(r.storage < 0 ? -1 : 100 - r.storage, 10, 5);
        int flagLevel = r.active.isEmpty() ? 0 : 2;
        int worst = Math.max(Math.max(ramLevel, storageLevel), flagLevel);
        int issues = (ramLevel > 0 ? 1 : 0) + (storageLevel > 0 ? 1 : 0) + r.active.size();

        statusIcon.setImageResource(worst == 0 ? R.drawable.ic_ok : worst == 1 ? R.drawable.ic_warn : R.drawable.ic_alert);
        statusIcon.setColorFilter(colorOf(worst), PorterDuff.Mode.SRC_IN);
        headline.setText(worst == 0 ? getString(R.string.all_good) : String.valueOf(issues));
        headline.setTextColor(colorOf(worst));
        chips.removeAllViews();
        if (ramLevel > 0) chips.addView(chip(R.drawable.ic_ram, colorOf(ramLevel)));
        if (storageLevel > 0) chips.addView(chip(R.drawable.ic_disk, colorOf(storageLevel)));
        if (!r.active.isEmpty()) chips.addView(chip(R.drawable.ic_shield, badColor));

        refreshUsbIcon();
        ram.set(r.ram, r.ramText, colorOf(ramLevel));
        storage.set(r.storage, r.storageText, colorOf(storageLevel));
        networkIcon.setImageResource(r.ethernet ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
        networkText.setText(r.network);
        uptimeText.setText(r.uptime);

        int off = r.total - r.active.size();
        flaggedCount.setText(off + " / " + r.total);
        flaggedCount.setTextColor(r.active.isEmpty() ? dim : badColor);
        flaggedBox.removeAllViews();
        for (final String[] f : r.active) {
            flaggedBox.addView(lineButton(R.drawable.ic_dot, badColor, f[0], badColor, () -> open(appInfo(f[0]))));
        }
        showSuggestions(r);
        showAi(false);
    }

    private View chip(int res, int color) {
        ImageView v = icon(res, color, 34);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(34, 34);
        lp.setMargins(0, 0, 22, 0);
        v.setLayoutParams(lp);
        return v;
    }

    private static int level(float freePercent, int warnBelow, int badBelow) {
        if (freePercent < 0) return 0;
        if (freePercent < badBelow) return 2;
        if (freePercent < warnBelow) return 1;
        return 0;
    }

    private int colorOf(int level) {
        return level == 2 ? badColor : level == 1 ? warnColor : okColor;
    }

    // ---- suggestions and the journal ------------------------------------------------------------------------

    private void showSuggestions(Reading r) {
        StringBuilder key = new StringBuilder();
        for (String[] s : r.suggestedOn) key.append('+').append(s[0]);
        for (String[] s : r.suggestedOff) key.append('-').append(s[0]);
        if (key.toString().equals(lastSuggestionKey)) return;
        lastSuggestionKey = key.toString();
        View focused = getCurrentFocus();
        String focusedKey = focused == null ? null : (String) focused.getTag();

        suggestedCount.setText(String.valueOf(r.suggestedOn.size()));
        suggestedBox.removeAllViews();
        for (final String[] s : r.suggestedOn) {
            View row = lineButton(R.drawable.ic_dot, s[1].equals("med") ? warnColor : okColor, shortName(s[0]), ink, () -> open(appInfo(s[0])));
            row.setTag(s[0]);
            suggestedBox.addView(row);
        }
        journalCount.setText(String.valueOf(r.suggestedOff.size()));
        journalBox.removeAllViews();
        DateFormat when = DateFormat.getDateInstance(DateFormat.SHORT);
        for (int i = 0; i < r.suggestedOff.size(); i++) {
            final String[] s = r.suggestedOff.get(i);
            View row = lineButton(R.drawable.ic_undo, dim, shortName(s[0]) + "   " + when.format(new Date(r.suggestedOffAt.get(i))), dim, () -> open(appInfo(s[0])));
            row.setTag(s[0]);
            journalBox.addView(row);
        }
        if (focusedKey != null) {
            View again = suggestedBox.findViewWithTag(focusedKey);
            if (again == null) again = journalBox.findViewWithTag(focusedKey);
            if (again != null) again.requestFocus();
        }
    }

    /** org.droidtv.nettvapp1814 reads better as nettvapp1814. */
    private static String shortName(String pkg) {
        for (String prefix : new String[] {"org.droidtv.", "com.google.android.", "com.google."}) {
            if (pkg.startsWith(prefix)) return pkg.substring(prefix.length());
        }
        return pkg;
    }

    // ---- the assistant --------------------------------------------------------------------------------------

    private void showAi(boolean force) {
        String json = prefs.getString("ai_report", "");
        long now = System.currentTimeMillis() / 1000;
        org.json.JSONObject o = null;
        try { if (!json.isEmpty()) o = new org.json.JSONObject(json); } catch (org.json.JSONException ignored) { }
        String state = o == null ? "idle" : o.optString("state", "result");
        long deadline = o == null ? 0 : o.optLong("deadline");
        String id = o == null ? "" : o.optString("id");
        boolean answered = !id.isEmpty() && prefs.getString("answered", "").equals(id);
        boolean busy = (state.equals("proposal") && !answered) || (state.equals("applying") && deadline > now);
        askButton.setVisibility(busy ? View.GONE : View.VISIBLE);
        long waited = waitingSince > 0 ? System.currentTimeMillis() - waitingSince : 0;
        if (waited > 90000 || (o != null && waitingSince > 0 && o.optLong("at") * 1000 > waitingSince)) waitingSince = 0;
        long rebooting = rebootAt > 0 ? System.currentTimeMillis() - rebootAt : 0;
        if (rebooting > 90000) { rebootAt = 0; rebooting = 0; }
        String hint = waitingSince == 0 ? "" : waited > 25000 ? getString(R.string.ai_no_pc) : "…";
        if (rebootAt > 0) hint = rebooting > 25000 ? getString(R.string.reboot_no_pc) : "…";
        askHint.setText(hint);
        if (rebootAt > 0 && rebooting > 25000 && !manualShown) {
            manualShown = true;
            confirmBox.addView(lineButton(R.drawable.ic_power, dim, getString(R.string.reboot_manual), ink,
                    () -> open(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))));
        }
        String key = json + "|" + (state.equals("applying") ? Math.max(0, deadline - now) : 0) + "|" + answered + "|" + busy;
        if (key.equals(lastAi)) return;
        lastAi = key;
        aiBox.removeAllViews();
        aiTime.setText(o == null ? "" : DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(o.optLong("at") * 1000)));
        if (o == null) return;

        fillAnswer(aiBox, o);

        final String current = json;
        card.removeAllViews();
        boolean pending = false;
        if (state.equals("proposal") && !answered) {
            pending = true;
            addDecisionHeader(R.drawable.ic_warn, getString(R.string.ai_decide));
            fillAnswer(card, o);
            LinearLayout q = row();
            q.setPadding(0, 14, 0, 0);
            q.addView(icon(R.drawable.ic_clock, warnColor, 30));
            TextView secs = label(o.optInt("revert_seconds", 60) + " s", 22, warnColor, 0f);
            secs.setPadding(10, 0, 0, 0);
            q.addView(secs);
            card.addView(q);
            LinearLayout b = row();
            View choose = null;
            if (o.optBoolean("can_apply", false)) {
                choose = lineButton(R.drawable.ic_check, okColor, getString(R.string.ai_apply), ink, () -> decide(current, "apply"));
                b.addView(choose);
            } else {
                b.addView(label(getString(R.string.ai_preview), 16, dim, 0f));
            }
            View notNow = lineButton(R.drawable.ic_close, dim, getString(R.string.ai_not_now), ink, () -> decide(current, "cancel"));
            b.addView(notNow);
            card.addView(b);
            (choose != null ? choose : notNow).requestFocus();
        } else if (state.equals("applying")) {
            long left = Math.max(0, deadline - now);
            if (left > 0 && !answered) {
                pending = true;
                addDecisionHeader(R.drawable.ic_clock, getString(R.string.ai_decide_keep));
                fillAnswer(card, o);
                LinearLayout q = row();
                q.setPadding(0, 14, 0, 0);
                q.addView(icon(R.drawable.ic_clock, left > 10 ? warnColor : badColor, 44));
                TextView secs = label(String.valueOf(left), 48, left > 10 ? warnColor : badColor, 0f);
                secs.setPadding(14, 0, 0, 0);
                q.addView(secs);
                card.addView(q);
                LinearLayout b = row();
                View keep = lineButton(R.drawable.ic_check, okColor, getString(R.string.ai_all_good), ink, () -> decide(current, "keep"));
                b.addView(keep);
                b.addView(lineButton(R.drawable.ic_undo, badColor, getString(R.string.ai_undo_now), ink, () -> decide(current, "revert")));
                card.addView(b);
                keep.requestFocus();
            } else if (left > 0 || now - deadline < 20) {
                LinearLayout q = row();
                q.setPadding(0, 10, 0, 0);
                q.addView(icon(R.drawable.ic_clock, badColor, 40));
                TextView secs = label(left > 0 ? String.valueOf(left) : "↺", 44, badColor, 0f);
                secs.setPadding(14, 0, 0, 0);
                q.addView(secs);
                aiBox.addView(q);
            }
        }
        overlay.setVisibility(pending ? View.VISIBLE : View.GONE);
        scroll.setDescendantFocusability(pending ? android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                : android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    private void addDecisionHeader(int iconRes, String text) {
        LinearLayout h = row();
        h.setPadding(0, 0, 0, 12);
        h.addView(icon(iconRes, warnColor, 34));
        TextView w = label(text, 24, warnColor, 0f);
        w.setPadding(14, 0, 0, 0);
        h.addView(w);
        card.addView(h);
    }

    /** The AI's answer: its sentence, the first two observations, and each action with its state. */
    private void fillAnswer(LinearLayout into, org.json.JSONObject o) {
        into.addView(label(o.optString("summary"), 20, ink, 0f));
        org.json.JSONArray findings = o.optJSONArray("findings");
        for (int i = 0; findings != null && i < Math.min(2, findings.length()); i++) {
            into.addView(label("• " + findings.optString(i), 16, dim, 0f));
        }
        org.json.JSONArray actions = o.optJSONArray("actions");
        for (int i = 0; actions != null && i < actions.length(); i++) {
            try {
                org.json.JSONObject a = actions.getJSONObject(i);
                String st = a.optString("status");
                boolean good = st.equals("applied") || st.equals("kept");
                boolean bad = st.equals("rolled_back") || st.equals("reverted");
                int res = good ? R.drawable.ic_check : bad ? R.drawable.ic_undo
                        : st.equals("cancelled") || st.equals("skipped") ? R.drawable.ic_close : R.drawable.ic_dot;
                int color = good ? okColor : bad ? badColor : st.equals("proposed") ? ink : dim;
                String what = a.optString("package").isEmpty() ? a.optString("type") : shortName(a.optString("package"));
                LinearLayout l = row();
                l.setPadding(0, 6, 0, 6);
                l.addView(icon(res, color, 26));
                TextView t = label(typeWord(a.optString("type")) + "  " + what, 17, color, 0f);
                t.setPadding(14, 0, 0, 0);
                l.addView(t);
                into.addView(l);
            } catch (org.json.JSONException ignored) { }
        }
    }

    private static String typeWord(String type) {
        switch (type) {
            case "disable_package": return "⊗";
            case "force_stop": return "■";
            case "trim_caches": return "♻";
            default: return type;
        }
    }

    /** Writes the owner's answer where the PC assistant and the TV-side watchdog read it. */
    private void decide(String json, String what) {
        try {
            String id = new org.json.JSONObject(json).optString("id");
            android.util.Log.i(TAG, "DECISION " + id + " " + what);
            prefs.edit().putString("answered", id).apply();
            lastAi = "";
            showAi(true);
        } catch (org.json.JSONException ignored) { }
    }

    // ---- readings -------------------------------------------------------------------------------------------

    private static long[] readMemory() {
        long total = -1, avail = -1, free = 0, buffers = 0, cached = 0;
        try (BufferedReader in = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = in.readLine()) != null) {
                long kb = kb(line);
                if (line.startsWith("MemTotal:")) total = kb;
                else if (line.startsWith("MemAvailable:")) avail = kb;
                else if (line.startsWith("MemFree:")) free = kb;
                else if (line.startsWith("Buffers:")) buffers = kb;
                else if (line.startsWith("Cached:")) cached = kb;
            }
        } catch (IOException e) {
            return new long[] {-1, -1};
        }
        if (total <= 0) return new long[] {-1, -1};
        if (avail < 0) avail = Math.min(total, free + buffers + cached);
        return new long[] {avail / 1024, total / 1024};
    }

    private static long kb(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) return -1;
        try { return Long.parseLong(parts[1]); } catch (NumberFormatException e) { return -1; }
    }

    private String networkName() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps == null) return "—";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
            return "—";
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private static String gb(long bytes) {
        return String.format("%.1f GB", bytes / 1073741824.0);
    }

    /** An icon, a thin bar and the value: the bar takes the verdict's colour. */
    private final class Meter extends View {
        private final Drawable glyph;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float percent = -1;
        private String value = "";
        private int color = okColor;

        Meter(Context context, int res) {
            super(context);
            glyph = getDrawable(res).mutate();
            glyph.setColorFilter(dim, PorterDuff.Mode.SRC_IN);
            paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }

        void set(float percent, String value, int color) {
            this.percent = percent;
            this.value = value;
            this.color = color;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            glyph.setBounds(0, 0, 34, 34);
            glyph.draw(canvas);
            paint.setTextSize(26);
            paint.setColor(ink);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(value, w, 28, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            float top = h - 22, bottom = h - 14;
            rect.set(0, top, w, bottom);
            paint.setColor(Color.argb(40, 255, 255, 255));
            canvas.drawRoundRect(rect, 4, 4, paint);
            if (percent >= 0) {
                rect.set(0, top, Math.max(8, w * Math.min(100f, percent) / 100f), bottom);
                paint.setColor(color);
                canvas.drawRoundRect(rect, 4, 4, paint);
            }
        }
    }
}
