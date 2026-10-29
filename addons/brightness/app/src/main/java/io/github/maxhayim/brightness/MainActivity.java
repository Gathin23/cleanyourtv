package io.github.maxhayim.brightness;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The tile and module card open this: one list, the first row the master switch, so normal screen is a single OK
 * away. Left and Right change a value and the layer follows at once, over this screen too, as a preview.
 * No text fields and nothing to type.
 */
public class MainActivity extends Activity {
    private Dimming.Settings s;
    private LinearLayout rows;
    private TextView status;
    private Row permissionRow, onRow, brightnessRow, nightRow, nightBrightnessRow, warmthRow, startRow, endRow;
    private TvPicture tv;
    private boolean tvAvailable;
    /** The TV's "allow" prompt while this app may not change the picture yet; null once allowed. */
    private volatile PendingIntent tvAsk;
    private Row tvRow, tvBacklightRow, tvContrastRow, tvBrightnessRow, tvDynamicRow, tvTakeRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("◀ ▶ change    OK switch    Back closes, settings stay");
        hint.setTextSize(13);
        hint.setTextColor(0xFF8B949E);
        hint.setPadding(px(16), px(12), 0, 0);
        page.addView(hint);
        setContentView(page);
        tv = new TvPicture(this);
        tvAvailable = tv.available();
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The overlay permission may have been granted in Settings or over ADB meanwhile.
        s = Prefs.load(this);
        boolean allowed = DimService.canDraw(this);
        if ((permissionRow == null) == !allowed) build();
        refresh();
        DimService.start(this);
        if (tvAvailable) {
            // A binder call into the TV's settings app, which may have to start: off the main thread.
            new Thread(() -> {
                PendingIntent ask = tv.permissionRequest();
                runOnUiThread(() -> {
                    boolean changed = (ask == null) != (tvAsk == null);
                    tvAsk = ask;
                    if (changed && !isFinishing()) build();
                });
            }).start();
        }
    }

    private void build() {
        s = Prefs.load(this);
        rows.removeAllViews();
        permissionRow = null;
        if (!DimService.canDraw(this)) {
            permissionRow = add(new Row(this, "Allow display over other apps",
                    "Needed to dim other apps. OK opens the setting.", false, this::askPermission, null));
            permissionRow.setValue("Not allowed");
        }
        onRow = add(new Row(this, "Dimming", "Off puts the screen back to normal at once. The settings below stay.",
                false, () -> {
                    s.on = !s.on;
                    changed();
                }, d -> {
                    s.on = d > 0;
                    changed();
                }));
        brightnessRow = add(new Row(this, "Brightness", "How bright the picture is outside night mode.", true, null,
                d -> {
                    s.brightness = Dimming.stepBrightness(s.brightness, d);
                    changed();
                }));
        nightRow = add(new Row(this, "Night mode", null, false, () -> {
            s.night = Dimming.Night.values()[(s.night.ordinal() + 1) % 3];
            changed();
        }, d -> {
            s.night = Dimming.Night.values()[(s.night.ordinal() + d + 3) % 3];
            changed();
        }));
        nightBrightnessRow = add(new Row(this, "Night brightness", "How bright the picture is in night mode.", true,
                null, d -> {
                    s.nightBrightness = Dimming.stepBrightness(s.nightBrightness, d);
                    changed();
                }));
        warmthRow = add(new Row(this, "Warmth", "Night mode only: less blue, a warmer picture.", true, null, d -> {
            s.warmth += d * Dimming.WARMTH_STEP;
            changed();
        }));
        startRow = add(new Row(this, "Night starts", "Scheduled night mode begins. ◀ ▶ moves 15 minutes.", false, null,
                d -> {
                    s.nightStart += d * Dimming.TIME_STEP;
                    changed();
                }));
        endRow = add(new Row(this, "Night ends", "Back to the day brightness.", false, null, d -> {
            s.nightEnd += d * Dimming.TIME_STEP;
            changed();
        }));
        if (tvAvailable) buildTvRows();
        (permissionRow != null ? permissionRow : onRow).requestFocus();
    }

    /** The TV's own picture, on TVs whose picture menu allows it: the light the dimming layer can't take away. */
    private void buildTvRows() {
        tvRow = add(new Row(this, "TV picture at night", "Also turns the TV's own backlight, contrast and brightness"
                + " down for the night, and puts the day's back in the morning.", false, () -> {
                    s.tvPicture = !s.tvPicture;
                    changed();
                }, d -> {
                    s.tvPicture = d > 0;
                    changed();
                }));
        if (tvAsk != null) {
            Row ask = add(new Row(this, "Allow TV picture control",
                    "The TV asks once whether this app may change its picture settings. OK asks.", false, () -> {
                        try {
                            tvAsk.send();
                        } catch (PendingIntent.CanceledException ignored) {
                            // Asked again on the next open.
                        }
                    }, null));
            ask.setValue("Not allowed");
        }
        tvBacklightRow = add(new Row(this, "Night backlight", "The TV's backlight: the light the panel gives off.",
                true, null, d -> {
                    s.tvNight = s.tvNight.withBacklight(Dimming.stepPercent(s.tvNight.backlight, d));
                    changed();
                }));
        tvContrastRow = add(new Row(this, "Night contrast", "The TV's contrast. Lower dims the whites.", true, null,
                d -> {
                    s.tvNight = s.tvNight.withContrast(Dimming.stepPercent(s.tvNight.contrast, d));
                    changed();
                }));
        tvBrightnessRow = add(new Row(this, "Night TV brightness", "The TV's brightness. Lower darkens the shadows.",
                true, null, d -> {
                    s.tvNight = s.tvNight.withBrightness(Dimming.stepPercent(s.tvNight.brightness, d));
                    changed();
                }));
        tvDynamicRow = add(new Row(this, "Night dynamic backlight", "The TV lowers its backlight further on dark pictures.",
                false, () -> {
                    s.tvNight = s.tvNight.withDynamicBacklight(!s.tvNight.dynamicBacklight);
                    changed();
                }, null));
        tvTakeRow = add(new Row(this, "Use the TV's picture now",
                "Takes what is set in the TV's own picture menu as the night values.", false, this::takeTvPicture, null));
    }

    private void takeTvPicture() {
        tvTakeRow.setValue("Reading…");
        new Thread(() -> {
            Dimming.Picture now = tv.read();
            runOnUiThread(() -> {
                if (now == null) {
                    tvTakeRow.setValue("Not allowed");
                    return;
                }
                s.tvNight = now;
                changed();
                tvTakeRow.setValue("Taken");
            });
        }).start();
    }

    private Row add(Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    /** Saves, which the running service sees at once; starts it when the new settings need it. */
    private void changed() {
        Prefs.save(this, s);
        refresh();
        DimService.start(this);
    }

    private void refresh() {
        boolean h24 = DateFormat.is24HourFormat(this);
        int minute = DimService.minuteNow();
        onRow.setValue(s.on ? "On" : "Off");
        brightnessRow.setValue(s.brightness + " %");
        brightnessRow.setBar(fraction(s.brightness));
        nightRow.setValue(s.night == Dimming.Night.OFF ? "Off" : s.night == Dimming.Night.ON ? "On" : "Scheduled");
        nightRow.setDetail(s.night == Dimming.Night.SCHEDULED
                ? "Between the times below, every day."
                : s.night == Dimming.Night.ON ? "Now, until switched off here." : "Day brightness all the time.");
        nightBrightnessRow.setValue(s.nightBrightness + " %");
        nightBrightnessRow.setBar(fraction(s.nightBrightness));
        warmthRow.setValue(s.warmth + " %");
        warmthRow.setBar(s.warmth / 100f);
        startRow.setValue(Dimming.clock(s.nightStart, h24));
        endRow.setValue(Dimming.clock(s.nightEnd, h24));
        boolean scheduled = s.night == Dimming.Night.SCHEDULED;
        startRow.setAlpha(scheduled ? 1f : 0.5f);
        endRow.setAlpha(scheduled ? 1f : 0.5f);

        if (tvAvailable && tvRow != null) {
            tvRow.setValue(s.tvPicture ? "On" : "Off");
            tvBacklightRow.setValue(s.tvNight.backlight + " %");
            tvBacklightRow.setBar(s.tvNight.backlight / 100f);
            tvContrastRow.setValue(s.tvNight.contrast + " %");
            tvContrastRow.setBar(s.tvNight.contrast / 100f);
            tvBrightnessRow.setValue(s.tvNight.brightness + " %");
            tvBrightnessRow.setBar(s.tvNight.brightness / 100f);
            tvDynamicRow.setValue(s.tvNight.dynamicBacklight ? "On" : "Off");
            float a = s.tvPicture ? 1f : 0.5f;
            tvBacklightRow.setAlpha(a);
            tvContrastRow.setAlpha(a);
            tvBrightnessRow.setAlpha(a);
            tvDynamicRow.setAlpha(a);
        }

        Dimming.Level l = Dimming.level(s, minute);
        String text;
        if (!s.on) text = "Off: the screen is normal.";
        else if (permissionRow != null) text = "On, but nothing is drawn until display over other apps is allowed.";
        else if (l.night) text = "Night mode now: " + l.brightness + " %, warmth " + l.warmth + " %"
                + (scheduled ? ", until " + Dimming.clock(s.nightEnd, h24) : "") + ".";
        else text = "Now: " + l.brightness + " %"
                + (scheduled && s.nightStart != s.nightEnd ? ". Night mode from " + Dimming.clock(s.nightStart, h24) : "") + ".";
        status.setText(text);
    }

    private void askPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException | SecurityException e) {
            // Many TVs hide this setting; the ADB shell can still grant it.
            permissionRow.setDetail("This TV has no screen for it. From a computer: adb shell appops set "
                    + getPackageName() + " SYSTEM_ALERT_WINDOW allow");
        }
    }

    private static float fraction(int brightness) {
        return (brightness - Dimming.MIN_BRIGHTNESS) / (float) (Dimming.MAX_BRIGHTNESS - Dimming.MIN_BRIGHTNESS);
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
