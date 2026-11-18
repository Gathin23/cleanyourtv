package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Inputs on Coocaa OS (Skyworth) TVs, which register nothing with TvInputManager. com.skyworth.tv lists
 * the sources its own Source panel shows (HDMI 1, HDMI 2, RF, AV), and switches to one and brings up its
 * player, as picking it in that panel does.
 */
final class CoocaaSources {

    // Parsed lazily (not as Uri constants) so the class can initialise in a plain JVM unit test, where
    // Uri.parse is not mocked; the pure parseAndSort/firstNonEmpty helpers carry the tested logic.
    private static final String LIST = "content://com.skyworth.tv.provider/system/source_list";
    // Some Coocaa builds answer nothing on the provider above but have the whole list on the SDK
    // authority next to it (the one METHOD already uses), where a projection is mandatory (#1351).
    private static final String LIST_SDK = "content://com.skyworth.tv.sdk.provider/system/source_list";
    private static final String METHOD = "content://com.skyworth.tv.sdk.provider/method";
    private static final String[] COLUMNS = {"sourceName", "sourceIndex", "displayName"};

    static final class Source {
        /** HDMI, AV, VOD (the RF tuner), ... */
        final String name;
        final int index;
        final String label;

        Source(String name, int index, String label) {
            this.name = name;
            this.index = index;
            this.label = label;
        }
    }

    private CoocaaSources() {
    }

    /** HDMI ports in port order, then AV, then the tuner; empty where the provider is missing. */
    static List<Source> list(Context ctx) {
        List<Object[]> primary = query(ctx, LIST);
        return parseAndSort(firstNonEmpty(primary, primary == null || primary.isEmpty() ? query(ctx, LIST_SDK) : null));
    }

    /** The provider's rows, or null when that authority answers nothing or fails. */
    private static List<Object[]> query(Context ctx, String uri) {
        List<Object[]> rows = new ArrayList<>();
        try (Cursor c = ctx.getContentResolver().query(Uri.parse(uri), COLUMNS, null, null, null)) {
            if (c == null) return null;
            while (c.moveToNext()) {
                rows.add(new Object[]{c.getString(0), c.getInt(1), c.getString(2)});
            }
        } catch (RuntimeException e) {
            return null;
        }
        return rows;
    }

    /** The primary rows when it returned any, otherwise the fallback (which may itself be empty/null). */
    static List<Object[]> firstNonEmpty(List<Object[]> primary, List<Object[]> fallback) {
        if (primary != null && !primary.isEmpty()) return primary;
        return fallback == null ? Collections.emptyList() : fallback;
    }

    /** Drop nameless rows, default an empty label to the name, and order HDMI ports, then AV, then tuner. */
    static List<Source> parseAndSort(List<Object[]> rows) {
        List<Source> out = new ArrayList<>();
        if (rows != null) {
            for (Object[] row : rows) {
                String name = (String) row[0];
                String label = (String) row[2];
                if (name == null) continue;
                out.add(new Source(name, (Integer) row[1], label == null || label.isEmpty() ? name : label));
            }
        }
        Collections.sort(out, (a, b) -> a.name.equals(b.name) ? Integer.compare(a.index, b.index)
                : Integer.compare(rank(a.name), rank(b.name)));
        return out;
    }

    private static int rank(String name) {
        switch (name) {
            case "HDMI": return 0;
            case "AV": return 1;
            case "VOD": return 3;
            default: return 2;
        }
    }

    static boolean open(Context ctx, Source source) {
        Bundle extras = new Bundle();
        extras.putString("name", source.name);
        extras.putInt("index", source.index);
        try {
            Bundle ret = ctx.getContentResolver().call(Uri.parse(METHOD), "METHOD_GOTO_SOURCE", null, extras);
            return ret != null && ret.getBoolean("ret");
        } catch (RuntimeException e) {
            return false;
        }
    }
}
