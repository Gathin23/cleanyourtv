package com.gathin.tvhome;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * remap_buttons (config.xml or a theme): remote buttons the owner maps to an app or an action from Launcher settings, as
 * "4020=TCL Channel". A choice is saved as "button_<keycode>" in the launcher's settings and replaces that keycode's
 * tap entry in app_keys; with no choice saved the button keeps its app_keys entry. Needs HOME_KEY_SERVICE=1.
 */
final class ButtonMap {

    static final String KEY_PREFIX = "button_";
    /** The target that swallows the button: app_keys' "ignore" action word. */
    static final String NOTHING = "ignore";

    private ButtonMap() { }

    /** keycode -> label from "keycode=Label" entries, in list order; malformed entries are skipped. */
    static Map<Integer, String> buttons(Iterable<String> entries) {
        Map<Integer, String> buttons = new LinkedHashMap<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String[] parts = entry.split("=", 2);
            if (parts.length != 2 || parts[1].trim().isEmpty()) continue;
            try {
                int code = Integer.parseInt(parts[0].trim());
                if (code > 0) buttons.put(code, parts[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return buttons;
    }

    /** The saved choice of each listed button that has one. */
    static Map<Integer, String> choices(SharedPreferences prefs, Iterable<String> entries) {
        Map<Integer, String> chosen = new LinkedHashMap<>();
        for (int code : buttons(entries).keySet()) {
            String target = prefs.getString(KEY_PREFIX + code, null);
            if (target != null && !target.trim().isEmpty()) chosen.put(code, target.trim());
        }
        return chosen;
    }

    /** app_keys with the tap entry of each chosen button replaced; hold:, front: and scan: entries stay. */
    static List<String> apply(List<String> appKeys, Map<Integer, String> chosen) {
        if (chosen.isEmpty()) return appKeys;
        List<String> keys = new ArrayList<>();
        for (String entry : appKeys) {
            int colon = entry == null ? -1 : entry.indexOf(':');
            String key = colon < 0 ? "" : entry.substring(0, colon).trim();
            boolean replaced = false;
            for (int code : chosen.keySet()) replaced |= key.equals(String.valueOf(code));
            if (!replaced) keys.add(entry);
        }
        for (Map.Entry<Integer, String> c : chosen.entrySet()) keys.add(c.getKey() + ":" + c.getValue());
        return keys;
    }
}
