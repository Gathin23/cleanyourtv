package io.github.knochango.remotebuttons;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.TreeMap;

/** Which app each button opens: key code to package, one preference per button. */
final class Prefs {
    private Prefs() { }

    private static final String NAME = "buttons";
    private static final String PREFIX = "key_";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** Every button that opens an app, by key code. */
    static Map<Integer, String> mappings(Context ctx) {
        Map<Integer, String> out = new TreeMap<>();
        for (Map.Entry<String, ?> entry : prefs(ctx).getAll().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX) || !(entry.getValue() instanceof String)) continue;
            try {
                out.put(Integer.parseInt(entry.getKey().substring(PREFIX.length())), (String) entry.getValue());
            } catch (NumberFormatException ignored) {
                // Not one of ours.
            }
        }
        return out;
    }

    /** The package this button opens, or "" for none. */
    static String app(Context ctx, int keyCode) {
        return prefs(ctx).getString(PREFIX + keyCode, "");
    }

    /** An empty package takes the button off. */
    static void set(Context ctx, int keyCode, String pkg) {
        SharedPreferences.Editor edit = prefs(ctx).edit();
        if (pkg == null || pkg.isEmpty()) edit.remove(PREFIX + keyCode);
        else edit.putString(PREFIX + keyCode, pkg);
        edit.apply();
    }
}
