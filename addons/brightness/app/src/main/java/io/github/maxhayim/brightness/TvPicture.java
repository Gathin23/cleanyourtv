package io.github.maxhayim.brightness;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.slice.Slice;
import android.app.slice.SliceItem;
import android.app.slice.SliceManager;
import android.app.slice.SliceSpec;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * The TV's own picture settings on Realtek-based TVs, through the settings slices its picture menu is built from:
 * the backlight, contrast, brightness and dynamic backlight. These change the light the panel gives off, which
 * the dimming layer can't. Not public API: other TVs simply don't have the provider, and then this does nothing.
 * Binder calls: use it off the main thread. Slices need Android 9; before that available() is false.
 */
@TargetApi(28)
final class TvPicture {
    static final String AUTHORITY = "com.realtek.menuslices";
    private static final String BASE = "content://" + AUTHORITY + "/";
    private static final String SEEKBAR = "TYPE_SEEKBAR_CONTROL";
    private static final String SWITCH = "TYPE_SWITCH_CONTROL";
    // The extras the TV's own settings fill in when a slider or switch changes.
    private static final String SLIDER_VALUE = "android.app.slice.extra.SLIDER_VALUE";
    private static final String TOGGLE_STATE = "android.app.slice.extra.TOGGLE_STATE";
    private static final String PREFERENCE_KEY = "extra_preference_key";

    /** The four controls, in the order of Dimming.Picture's fields. */
    private static final Control[] CONTROLS = {
            new Control(BASE + "MainMenu_Picture/Pic_Backlight", SEEKBAR, "Pic_Backlight_SeekBar"),
            new Control(BASE + "Pic_Picture_Mode_Item_Personal/Pic_Contrast", SEEKBAR, null),
            new Control(BASE + "Pic_Picture_Mode_Item_Personal/Pic_Brightness", SEEKBAR, null),
            new Control(BASE + "MainMenu_Picture/Pic_Backlight", SWITCH, "Pic_Dynamic_Backlight"),
    };

    private static final class Control {
        final Uri uri;
        final String type;
        final String key;

        Control(String uri, String type, String key) {
            this.uri = Uri.parse(uri);
            this.type = type;
            this.key = key;
        }
    }

    /** What a bound slice holds for one control: its value and the action that changes it. */
    private static final class Found {
        int value = -1;
        PendingIntent action;
    }

    private final Context context;
    private final SliceManager slices;

    TvPicture(Context context) {
        this.context = context.getApplicationContext();
        this.slices = Build.VERSION.SDK_INT >= 28 ? context.getSystemService(SliceManager.class) : null;
    }

    /** True on a TV whose picture menu offers these slices. */
    boolean available() {
        return slices != null && context.getPackageManager().resolveContentProvider(AUTHORITY, 0) != null;
    }

    /**
     * The system's "Allow Brightness to show … slices" prompt when this app isn't allowed yet, or null when it is
     * (or the TV has no such slices). The owner answers it once.
     */
    PendingIntent permissionRequest() {
        if (!available()) return null;
        Slice s = bind(CONTROLS[0].uri);
        return s != null && s.getHints().contains("permission_request") ? firstAction(s) : null;
    }

    /** The TV's picture now, or null when it can't be read. */
    Dimming.Picture read() {
        int[] v = new int[CONTROLS.length];
        for (int i = 0; i < CONTROLS.length; i++) {
            Found f = find(CONTROLS[i]);
            if (f == null || f.value < 0) return null;
            v[i] = f.value;
        }
        return new Dimming.Picture(v[0], v[1], v[2], v[3] != 0);
    }

    /** Sets each control whose value differs from was (all of them when was is null). True when all were sent. */
    boolean write(Dimming.Picture p, Dimming.Picture was) {
        int[] want = p.values();
        int[] had = was == null ? null : was.values();
        boolean ok = true;
        for (int i = 0; i < CONTROLS.length; i++) {
            if (had != null && had[i] == want[i]) continue;
            ok &= set(CONTROLS[i], want[i]);
        }
        return ok;
    }

    private boolean set(Control c, int value) {
        Found f = find(c);
        if (f == null || f.action == null) return false;
        Intent fill = new Intent();
        if (SWITCH.equals(c.type)) fill.putExtra(TOGGLE_STATE, value != 0);
        else fill.putExtra(SLIDER_VALUE, value);
        if (c.key != null) fill.putExtra(PREFERENCE_KEY, c.key);
        try {
            f.action.send(context, 0, fill);
            return true;
        } catch (PendingIntent.CanceledException e) {
            Log.w(Prefs.TAG, "tv picture not set: " + c.uri);
            return false;
        }
    }

    private Found find(Control c) {
        Slice s = bind(c.uri);
        if (s == null || s.getHints().contains("permission_request")) return null;
        for (SliceItem item : s.getItems()) {
            if (!"slice".equals(item.getFormat()) || !c.type.equals(item.getSubType())) continue;
            Slice row = item.getSlice();
            if (c.key != null && !c.key.equals(textOf(row, "keywords"))) continue;
            Found f = new Found();
            for (SliceItem i : row.getItems()) {
                if ("int".equals(i.getFormat()) && "SUBTYPE_SEEKBAR_VALUE".equals(i.getSubType())) {
                    if (SEEKBAR.equals(c.type)) f.value = i.getInt();
                } else if ("slice".equals(i.getFormat()) && i.getHints().contains("shortcut")) {
                    for (SliceItem a : i.getSlice().getItems()) {
                        if (!"action".equals(a.getFormat())) continue;
                        f.action = a.getAction();
                        if (SWITCH.equals(c.type)) f.value = a.getHints().contains("selected") ? 1 : 0;
                    }
                }
            }
            return f;
        }
        return null;
    }

    private Slice bind(Uri uri) {
        Set<SliceSpec> specs = new HashSet<>();
        specs.add(new SliceSpec("androidx.slice.LIST", 1));
        try {
            return slices.bindSlice(uri, specs);
        } catch (RuntimeException e) {
            Log.w(Prefs.TAG, "tv picture slice not bound: " + e.getMessage());
            return null;
        }
    }

    private static String textOf(Slice s, String hint) {
        for (SliceItem i : s.getItems()) {
            if ("text".equals(i.getFormat()) && i.getHints().contains(hint)) return String.valueOf(i.getText());
        }
        return null;
    }

    private static PendingIntent firstAction(Slice s) {
        for (SliceItem i : s.getItems()) {
            if ("action".equals(i.getFormat())) return i.getAction();
            if ("slice".equals(i.getFormat())) {
                PendingIntent p = firstAction(i.getSlice());
                if (p != null) return p;
            }
        }
        return null;
    }
}
