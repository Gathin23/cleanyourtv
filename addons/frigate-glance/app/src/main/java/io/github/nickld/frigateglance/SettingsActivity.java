package io.github.nickld.frigateglance;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The tile and module card open this: a menu of sections on the left, that section's rows on the right.
 * Moving through the menu switches the right side; Right enters the rows, Left goes back to the menu. Every
 * row changes with OK. No text fields: addresses and passwords come from the pushed config file.
 */
public class SettingsActivity extends Activity {
    private static final long REFRESH_MS = 2000;
    private static final int SNOOZED = 0xFFFFB454, CARDS_ON = 0xFF3DDC84, WARNING = 0xFFFFB454;
    private static final String[] SECTIONS = {"Overview", "Cameras", "Alerts", "Card", "Remote"};
    private static final Map<String, String> CORNER_NAMES = Map.of(
            "top-right", "Top right", "top-left", "Top left", "bottom-left", "Bottom left", "bottom-right", "Bottom right");

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout menu, rows;
    private ConfigStore store;
    private TextView status;
    private SettingRow snoozeRow, mqttRow, frigateRow, messagesRow, lastCardRow, tvRow;
    private int section;
    private boolean waitingForFrigate;
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            updateLive();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(px(48), px(32), px(48), px(24));
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF8B949E);
        status.setPadding(0, 0, 0, px(16));
        page.addView(title);
        page.addView(status);

        LinearLayout panes = new LinearLayout(this);
        panes.setOrientation(LinearLayout.HORIZONTAL);
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        panes.addView(menu, new LinearLayout.LayoutParams(px(220), ViewGroup.LayoutParams.WRAP_CONTENT));
        View divider = new View(this);
        divider.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(px(1), ViewGroup.LayoutParams.MATCH_PARENT);
        dp.leftMargin = px(16);
        dp.rightMargin = px(16);
        panes.addView(divider, dp);
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        panes.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        page.addView(panes, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);

        for (int i = 0; i < SECTIONS.length; i++) {
            int index = i;
            SettingRow item = new SettingRow(this, SECTIONS[i], "", () -> focusFirstRow());
            item.setId(View.generateViewId());
            item.setOnFocusChangeListener((v, has) -> {
                if (has && section != index) {
                    section = index;
                    buildRows(-1);
                }
            });
            menu.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        GlanceService.start(this);
        buildRows(-1);
        menu.getChildAt(0).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A config pushed while this screen was open: take it now and reconnect with it.
        if (store.importPushed()) {
            GlanceService.reload(this);
            buildRows(-1);
        }
        main.post(refresh);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(refresh);
        super.onPause();
    }

    /** Rebuilds the right side for the current section; focusIndex keeps focus on a row after a change. */
    private void buildRows(int focusIndex) {
        rows.removeAllViews();
        snoozeRow = mqttRow = frigateRow = messagesRow = lastCardRow = tvRow = null;
        GlanceConfig c = store.load();
        if (c == null) {
            rows.addView(new SettingRow(this, "No config yet", "push one with install.sh", null));
            return;
        }
        JSONObject frigate = GlanceService.frigateConfig();
        waitingForFrigate = frigate == null;
        switch (SECTIONS[section]) {
            case "Overview":
                snoozeRow = row("Snooze", "No cards from any camera for the snooze length (Remote section). OK again resumes.", "", () -> {
                    if (GlanceService.snoozedUntilWallClock() > 0) GlanceService.resume();
                    else GlanceService.snoozeCard();
                    main.postDelayed(this::updateLive, 200);
                });
                divider();
                mqttRow = info("MQTT broker", "");
                frigateRow = row("Frigate", "", "", () -> {
                    GlanceService.checkFrigate(!GlanceService.overview().offeredPin.isEmpty());
                    main.postDelayed(this::updateLive, 1500);
                });
                messagesRow = info("Last message from Frigate", "");
                lastCardRow = info("Last card", "");
                tvRow = info("On this TV", "");
                break;
            case "Cameras":
                List<String> cameras = SettingsModel.cameraNames(frigate);
                row("New cameras", "Cameras added to Frigate later open cards too, without switching them on here."
                        + " Cameras switched off below stay off.", onOff(c.newCameras || c.cameras.isEmpty()),
                        () -> save(cfg -> {
                            boolean on = cfg.newCameras || cfg.cameras.isEmpty();
                            if (on && cfg.cameras.isEmpty()) {
                                // "No list" meant every camera: switching new ones off keeps today's as the list.
                                JSONObject names = new JSONObject();
                                try {
                                    for (String cam : cameras) names.put(cam, CardController.pretty(cam));
                                } catch (JSONException ignored) {
                                    // names stay as far as they got
                                }
                                store.saveSetting("cameras", names);
                            }
                            store.saveSetting("newCameras", !on);
                        }));
                divider();
                if (cameras.isEmpty()) rows.addView(new SettingRow(this, "Cameras", "waiting for Frigate", null));
                for (String cam : cameras) {
                    String name = c.cameras.containsKey(cam) ? c.cameras.get(cam) : CardController.pretty(cam);
                    row(name, "Alerts from this camera open a card.",
                            onOff(SettingsModel.cameraOn(c, cam)), () -> save(cfg -> {
                        try {
                            JSONObject changes = SettingsModel.toggleCamera(cfg, cameras, cam);
                            for (java.util.Iterator<String> it = changes.keys(); it.hasNext(); ) {
                                String key = it.next();
                                store.saveSetting(key, changes.get(key));
                            }
                        } catch (JSONException ignored) {
                            // unchanged
                        }
                    }));
                }
                break;
            case "Alerts":
                row("Show", "Alerts are what Frigate's review settings mark as important (zones, objects). Detections"
                        + " are everything else it tracks, such as cars passing on the street.",
                        "alerts".equals(c.severity) ? "Alerts only" : "Alerts and detections", () -> save(cfg ->
                        store.saveSetting("severity", SettingsModel.next(SettingsModel.SEVERITIES, cfg.severity))));
                divider();
                List<String> labels = SettingsModel.labelNames(frigate);
                if (labels.isEmpty()) rows.addView(new SettingRow(this, "Objects", "waiting for Frigate", null));
                for (String label : labels) {
                    row(CardController.pretty(label), "Alerts with this object open a card.",
                            onOff(SettingsModel.labelOn(c, label)),
                            () -> save(cfg -> store.saveSetting("labels", SettingsModel.toggleLabel(cfg, labels, label))));
                }
                break;
            case "Card":
                row("Show a test card", "Pick a camera and its card shows now, to check where it sits and that the remote buttons work.",
                        "", () -> pickTestCamera(c));
                divider();
                row("Card size", "How big the card is, picture and text together.", c.cardScale + "%", () -> save(cfg ->
                        store.saveSetting("cardScale", SettingsModel.next(SettingsModel.CARD_SCALES, cfg.cardScale))));
                row("Corner", "Which corner of the screen the card slides into.", CORNER_NAMES.get(c.corner),
                        () -> save(cfg -> store.saveSetting("corner", SettingsModel.next(SettingsModel.CORNERS, cfg.corner))));
                row("Card stays", "How long the card stays once the objects have left, while the bar on top runs down.",
                        c.dismissSeconds + " s", () -> save(cfg -> store.saveSetting(
                        "dismissSeconds", SettingsModel.next(SettingsModel.DISMISS_SECONDS, cfg.dismissSeconds))));
                row("Parked objects", "A car or person that stops moving counts as gone, so the card starts counting down.",
                        onOff(c.dismissOnStationary), () -> save(cfg -> store.saveSetting("dismissOnStationary", !cfg.dismissOnStationary)));
                row("Live video", "Moving picture from the camera. Off, or while another app plays sound, a snapshot every second instead.",
                        onOff(c.liveVideo), () -> save(cfg -> store.saveSetting("liveVideo", !cfg.liveVideo)));
                row("Boxes", "Outlines around the objects Frigate detected, gone when the object leaves.", onOff(c.boxes), () -> save(cfg -> store.saveSetting("boxes", !cfg.boxes)));
                break;
            default:
                row("Dismiss key", "The remote button that closes a card. It only does this while a card is up. OK, then press it.",
                        CardText.key(c.dismissKey), () -> pickKey("Dismiss", "dismissKey", others(c, "Dismiss")));
                row("Hold to dismiss", "Dismiss only when the button is held for a moment, so a quick press while"
                        + " moving around an app does nothing.", onOff(c.dismissHold),
                        () -> save(cfg -> store.saveSetting("dismissHold", !cfg.dismissHold)));
                row("Snooze key", "The remote button that closes a card and keeps cards away for the snooze length."
                        + " OK, then press it.", CardText.key(c.snoozeKey), () -> pickKey("Snooze", "snoozeKey", others(c, "Snooze")));
                row("Hold to snooze", "Snooze only when the button is held for a moment.", onOff(c.snoozeHold),
                        () -> save(cfg -> store.saveSetting("snoozeHold", !cfg.snoozeHold)));
                row("Snooze length", "How long a snooze lasts, from the remote or from Overview.", CardText.duration(c.snoozeMinutes), () -> save(cfg -> store.saveSetting(
                        "snoozeMinutes", SettingsModel.next(SettingsModel.SNOOZE_MINUTES, cfg.snoozeMinutes))));
                divider();
                row("Full screen", "A third card key that opens the camera full screen. Back returns to what was playing.",
                        onOff(c.fullscreen), () -> save(cfg -> {
                            if (!cfg.fullscreen) {
                                // Its key must differ from the other two: take the first free one if it clashes.
                                for (String k : new String[] {cfg.fullscreenKey, "KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN",
                                        "KEYCODE_MENU", "KEYCODE_PROG_RED", "KEYCODE_PROG_GREEN"}) {
                                    if (!k.equals(cfg.dismissKey) && !k.equals(cfg.snoozeKey)) {
                                        store.saveSetting("fullscreenKey", k);
                                        break;
                                    }
                                }
                            }
                            store.saveSetting("fullscreen", !cfg.fullscreen);
                        }));
                if (c.fullscreen) {
                    row("Full screen key", "The remote button that opens the card's camera full screen. OK, then press it.",
                            CardText.key(c.fullscreenKey), () -> pickKey("Full screen", "fullscreenKey", others(c, "Full screen")));
                    row("Hold for full screen", "Open full screen only when the button is held for a moment.",
                            onOff(c.fullscreenHold), () -> save(cfg -> store.saveSetting("fullscreenHold", !cfg.fullscreenHold)));
                }
                break;
        }
        // Left from any row goes back to this section's menu item; Right from the menu enters at the first row.
        int back = menu.getChildAt(section).getId();
        int first = View.NO_ID;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View r = rows.getChildAt(i);
            r.setNextFocusLeftId(back);
            if (first == View.NO_ID && r.isFocusable()) {
                r.setId(View.generateViewId());
                first = r.getId();
            }
        }
        for (int i = 0; i < menu.getChildCount(); i++) {
            menu.getChildAt(i).setActivated(i == section);
            menu.getChildAt(i).setNextFocusRightId(first);
        }
        updateLive();
        if (focusIndex >= 0 && focusIndex < rows.getChildCount()) rows.getChildAt(focusIndex).requestFocus();
    }

    private void focusFirstRow() {
        for (int i = 0; i < rows.getChildCount(); i++) {
            if (rows.getChildAt(i).isFocusable()) {
                rows.getChildAt(i).requestFocus();
                return;
            }
        }
    }

    /** The camera list for a test card; falls back to the first camera while Frigate's list is not in yet. */
    private void pickTestCamera(GlanceConfig c) {
        List<String> cameras = SettingsModel.cameraNames(GlanceService.frigateConfig());
        if (cameras.isEmpty()) {
            GlanceService.showTestCard(this, null);
            return;
        }
        Map<String, String> names = new java.util.LinkedHashMap<>();
        for (String cam : cameras) names.put(cam, c.cameras.containsKey(cam) ? c.cameras.get(cam) : CardController.pretty(cam));
        new CameraPickerDialog(this, names, cam -> GlanceService.showTestCard(this, cam)).show();
    }

    /** Opens the "press the button" window; the button pressed is saved like any other setting. */
    private void pickKey(String what, String key, Map<String, String> others) {
        new KeyPickerDialog(this, what, others, name -> save(cfg -> store.saveSetting(key, name))).show();
    }

    /** The card keys other than this one, by name, for the picker's "already the ... key" check. */
    private static Map<String, String> others(GlanceConfig c, String self) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        if (!self.equals("Dismiss")) m.put("Dismiss", c.dismissKey);
        if (!self.equals("Snooze")) m.put("Snooze", c.snoozeKey);
        if (c.fullscreen && !self.equals("Full screen")) m.put("Full screen", c.fullscreenKey);
        return m;
    }

    private interface Change {
        void apply(GlanceConfig current);
    }

    private void save(Change change) {
        GlanceConfig c = store.load();
        if (c == null) return;
        View focused = getCurrentFocus();
        int index = focused == null ? -1 : rows.indexOfChild(focused);
        change.apply(c);
        GlanceService.settingsChanged(this);
        // Removing the old rows would hand focus to the menu on the left, which reads as backing out of the
        // section; keep the menu out of reach while the rows are rebuilt, then put focus back on the same row.
        menu.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        buildRows(index);
        menu.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (index >= 0 && index < rows.getChildCount()) {
            View again = rows.getChildAt(index);
            again.requestFocus();
            rows.post(again::requestFocus);
        }
    }

    /** A thin line between a section's switch or action and the list of rows it governs. */
    private void divider() {
        View line = new View(this);
        line.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1));
        lp.setMargins(px(16), px(8), px(16), px(8));
        rows.addView(line, lp);
    }

    /** A status line in Overview: not focusable, refreshed every two seconds by updateLive. */
    private SettingRow info(String name, String value) {
        SettingRow r = new SettingRow(this, name, "", value, null);
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private SettingRow row(String name, String description, String value, Runnable onClick) {
        SettingRow r = new SettingRow(this, name, description, value, onClick);
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void updateLive() {
        status.setText(GlanceService.statusLine());
        if (mqttRow != null) {
            GlanceService.Overview o = GlanceService.overview();
            // Errors go under the address: in the value they squeeze the address into a narrow column.
            mqttRow.setDetail(dots(o.mqttHost, o.running && !o.mqttUp ? o.mqttError : ""));
            mqttRow.setValue(!o.running ? "Not running" : o.mqttUp ? "Connected " + ago(o.mqttSince) : "Not connected");
            if (!o.running) {
                frigateRow.setDetail(o.frigateHost);
                frigateRow.setValue("Not running");
            } else if (!o.offeredPin.isEmpty()) {
                frigateRow.setDetail(dots(o.frigateHost, "Frigate shows a new certificate (" + o.offeredPin.substring(0, 12)
                        + ", trusted was " + o.pin.substring(0, 12) + "). Frigate makes a new one when it is"
                        + " reinstalled or updated; if that just happened, OK trusts it."));
                frigateRow.setValue("New certificate", WARNING);
            } else if (o.frigateUp) {
                frigateRow.setDetail(dots(o.frigateHost, o.cameras > 0 ? o.cameras + " cameras" : "", "OK reads the cameras again."));
                frigateRow.setValue("Reachable");
            } else {
                frigateRow.setDetail(dots(o.frigateHost, o.frigateError, "OK tries again."));
                frigateRow.setValue("Not reached");
            }
            messagesRow.setDetail("An alert or a tracked object. Quiet is normal while nothing moves.");
            messagesRow.setValue(o.lastMessageAt == 0 ? "None yet" : ago(o.lastMessageAt));
            lastCardRow.setDetail(OverlayCard.lastTitle);
            lastCardRow.setValue(OverlayCard.lastShownAt == 0 ? "None yet" : ago(OverlayCard.lastShownAt));
            boolean overlay = android.provider.Settings.canDrawOverlays(this);
            tvRow.setDetail("Version " + version() + " · display over other apps " + (overlay ? "allowed" : "NOT allowed")
                    + " · remote buttons " + (KeyService.isRunning() ? "on" : "off (run install.sh)"));
            tvRow.setValue(overlay && KeyService.isRunning() ? "Ready" : "Needs install.sh");
        }
        long until = GlanceService.snoozedUntilWallClock();
        String snoozedText = until > 0 ? "Snoozed until " + DateFormat.getTimeFormat(this).format(new Date(until)) : null;
        if (snoozedText != null) {
            // Seen from every section: the status line under the title says it too.
            SpannableStringBuilder line = new SpannableStringBuilder(GlanceService.statusLine() + " · ");
            int from = line.length();
            line.append(snoozedText);
            line.setSpan(new ForegroundColorSpan(SNOOZED), from, line.length(), 0);
            status.setText(line);
        }
        if (snoozeRow != null) {
            GlanceConfig c = store.load();
            int minutes = c == null ? 60 : c.snoozeMinutes;
            if (snoozedText != null) {
                snoozeRow.setValue(snoozedText, SNOOZED);
                snoozeRow.setDetail("Cards are paused. OK resumes them.");
            } else {
                snoozeRow.setValue("Cards on", CARDS_ON);
                snoozeRow.setDetail("OK snoozes cards from every camera for " + CardText.duration(minutes) + ".");
            }
        }
        // The camera and object lists appear once Frigate's config has been read.
        if (waitingForFrigate && GlanceService.frigateConfig() != null) {
            View focused = getCurrentFocus();
            buildRows(focused == null ? -1 : rows.indexOfChild(focused));
        }
    }

    /** "12 s ago", "5 min ago", or the time of day for anything older than an hour. */
    private String ago(long when) {
        if (when == 0) return "";
        long s = (System.currentTimeMillis() - when) / 1000;
        if (s < 60) return Math.max(0, s) + " s ago";
        if (s < 3600) return (s / 60) + " min ago";
        return "at " + DateFormat.getTimeFormat(this).format(new Date(when));
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /** The non-empty parts, joined with " · ": the address is empty until the service has read the config. */
    private static String dots(String... parts) {
        StringBuilder s = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (s.length() > 0) s.append(" · ");
            s.append(p);
        }
        return s.toString();
    }

    private static String onOff(boolean on) {
        return on ? "On" : "Off";
    }
}
