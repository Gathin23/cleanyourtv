package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Remote buttons that only the stock launcher answered, for InputKeyService and HomeKeyService: app_keys
 * (config.xml or a theme) maps a keycode to the app it opens, as "190:com.google.android.youtube.tv" or
 * "KEYCODE_BUTTON_3:com.google.android.youtube.tv", or a scancode as "scan:490:com.wbd.hbomax". A "hold:"
 * prefix makes an entry the long-press action instead: "hold:KEYCODE_BUTTON_8:org.xbmc.kodi" opens Kodi when
 * that button is held for HOLD_MS, while the unprefixed entry still opens on a tap; "hold:scan:490:..." does
 * the same for a scancode. One set of maps for both services; Android hands a key to every service that filters,
 * so both may call handle() for one event, and it answers each press once.
 * A target of "input:<input id>" switches to that TV input (an HDMI port) instead of opening an app, and one of
 * "package/.Activity" opens that activity rather than the app's launcher entry (an add-on's quick bar, say).
 */
final class RemoteKeys {

    /** How long a button is held before its "hold:" entry wins (Android's long-press timeout). */
    static final long HOLD_MS = 500;

    private RemoteKeys() { }

    /** keycode -> package, with key names resolved by Android. */
    static Map<Integer, String> parseAppKeys(String[] entries) {
        return parseAppKeys(Arrays.asList(entries), KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseAppKeys(Iterable<String> entries) {
        return parseAppKeys(entries, KeyEvent::keyCodeFromString);
    }

    /**
     * keycode -> package. The key is a number or a KEYCODE_ name (resolved by {@code names}; 0, KEYCODE_UNKNOWN,
     * means it isn't one). Entries without a colon, with an unknown key or with no package are skipped; for a
     * keycode listed twice the last entry wins.
     */
    static Map<Integer, String> parseAppKeys(Iterable<String> entries, ToIntFunction<String> names) {
        Map<Integer, String> keys = new HashMap<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String[] parts = entry.split(":", 2);
            if (parts.length != 2 || parts[1].trim().isEmpty()) continue;
            String key = parts[0].trim();
            int code;
            if (key.matches("[0-9]{1,6}")) {
                code = Integer.parseInt(key);
            } else if (key.matches("KEYCODE_[A-Z0-9_]+")) {
                code = names.applyAsInt(key);   // keyCodeFromString takes digits only from API 29, names always
            } else {
                continue;
            }
            if (code > 0) keys.put(code, parts[1].trim());
        }
        return keys;
    }

    /** keycode -> package from the "hold:" entries; the rest of each entry reads as it does in parseAppKeys. */
    static Map<Integer, String> parseHoldAppKeys(String[] entries) {
        return parseHoldAppKeys(Arrays.asList(entries), KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseHoldAppKeys(Iterable<String> entries) {
        return parseHoldAppKeys(entries, KeyEvent::keyCodeFromString);
    }

    static Map<Integer, String> parseHoldAppKeys(Iterable<String> entries, ToIntFunction<String> names) {
        return parseAppKeys(holdEntries(entries), names);
    }

    /**
     * Applies the key_filter choice (config.xml or a theme): when false the service drops
     * FLAG_REQUEST_FILTER_KEY_EVENTS at runtime, so it stays enabled for nothing but stops filtering.
     * Some firmware (TCL Google TV on Android 12) drops key auto-repeat once ANY service filters keys:
     * long-pressing volume then steps once per press. Filtering can only be skipped while the service
     * runs, since the flag is also declared in the manifest metadata.
     */
    static void applyKeyFilter(AccessibilityService service, boolean keyFilter) {
        if (keyFilter) return;
        AccessibilityServiceInfo info = service.getServiceInfo();
        if (info == null) return;
        info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        service.setServiceInfo(info);
    }

    /**
     * scancode -> package, from the "scan:490:com.wbd.hbomax" entries of the same list. For remotes that send
     * several app buttons as one keycode and tell them apart by scancode (Sony BRAVIA: YouTube, Prime Video and
     * Disney+ are all keycode 196, scancodes 485, 490 and 491). Find it with `getevent -lt` while pressing the
     * button: the EV_KEY code, in hex. Other entries are skipped; for a scancode listed twice the last entry wins.
     */
    static Map<Integer, String> parseAppScancodes(Iterable<String> entries) {
        Map<Integer, String> keys = new HashMap<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String[] parts = entry.split(":", 3);
            if (parts.length != 3 || !parts[0].trim().equals("scan") || parts[2].trim().isEmpty()) continue;
            String code = parts[1].trim();
            if (!code.matches("[0-9]{1,6}")) continue;
            int scan = Integer.parseInt(code);
            if (scan > 0) keys.put(scan, parts[2].trim());
        }
        return keys;
    }

    /** scancode -> package from the "hold:scan:..." entries; the rest reads as it does in parseAppScancodes. */
    static Map<Integer, String> parseHoldAppScancodes(Iterable<String> entries) {
        return parseAppScancodes(holdEntries(entries));
    }

    /** The entries with the "hold:" prefix removed, so the normal parsers can read them. */
    private static Iterable<String> holdEntries(Iterable<String> entries) {
        List<String> holds = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String text = entry.trim();
            if (text.startsWith("hold:")) holds.add(text.substring(5).trim());
        }
        return holds;
    }

    /** Created on first use: building it while the class loads breaks the JVM tests. */
    private static Handler handler;
    private static Runnable pendingHold;
    /**
     * Presses are told apart by KeyEvent.getDownTime(): the press whose hold is pending, the one whose hold fired
     * and the one that already launched. Both services get every event, so each press is answered once.
     */
    private static long armedDownTime = -1, firedDownTime = -1, launchedDownTime = -1;

    /**
     * Consumes a key mapped by either list: the hold app opens once the button has been down for HOLD_MS, the
     * normal one on an earlier release. False means neither list has this key, so the caller can pass it on.
     */
    static boolean handle(Context ctx, KeyEvent event, String app, String holdApp) {
        if (app == null && holdApp == null) return false;
        long downTime = event.getDownTime();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (holdApp == null || event.getRepeatCount() != 0 || (pendingHold != null && armedDownTime == downTime)) return true;
            if (handler == null) handler = new Handler(Looper.getMainLooper());
            if (pendingHold != null) handler.removeCallbacks(pendingHold);
            armedDownTime = downTime;
            pendingHold = () -> {
                pendingHold = null;
                firedDownTime = downTime;
                launch(ctx, holdApp);
            };
            handler.postDelayed(pendingHold, HOLD_MS);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (pendingHold != null && armedDownTime == downTime) {
                handler.removeCallbacks(pendingHold);
                pendingHold = null;
            }
            if (downTime != firedDownTime && downTime != launchedDownTime && !event.isCanceled()) {
                launchedDownTime = downTime;
                launch(ctx, target(app, holdApp, event.getEventTime() - downTime));
            }
        }
        return true;
    }

    /** The app a mapped key opens: its hold entry after HOLD_MS, otherwise its normal one. */
    static String target(String app, String holdApp, long heldMs) {
        return heldMs >= HOLD_MS && holdApp != null ? holdApp : app;
    }

    /** The TV input id of an "input:<id>" target, e.g. "input:com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413743104"; null for a package. */
    static String inputId(String target) {
        if (target == null || !target.startsWith("input:")) return null;
        String id = target.substring("input:".length()).trim();
        return id.isEmpty() ? null : id;
    }

    /** Opens an app from a remote button: its TV entry if it has one, else its phone entry. An "input:<id>" target
     *  switches to that TV input (an HDMI port) instead; a "package/.Activity" target opens that activity. */
    static void launch(Context ctx, String pkg) {
        if (pkg == null) return;
        ComponentName activity = pkg.indexOf('/') > 0 ? ComponentName.unflattenFromString(pkg) : null;
        if (activity != null) {
            try {
                ctx.startActivity(new Intent().setComponent(activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
            return;
        }
        String inputId = inputId(pkg);
        if (inputId != null) {
            // The same passthrough URI the Inputs menu opens, which the Live TV app handles.
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(inputId),
                            "vnd.android.cursor.item/channel")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(view);
            } catch (ActivityNotFoundException ignored) {
            }
            return;
        }
        Intent open = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (open == null) open = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (open == null) return;
        try {
            ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }
}
