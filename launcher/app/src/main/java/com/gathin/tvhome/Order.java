package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The owner's own tile order, set with the reorder button and kept in the launcher's private files (order.txt).
 *
 * Saved tiles come first in this order; apps not in it (new installs) follow by the usual rule (dock_apps, cards,
 * alphabetical) and uninstalled ones simply drop out. The file also records the dock_apps it was made under: when
 * dock_apps changes (a rebuild or a pushed theme), the saved order is ignored, so the new dock takes effect until
 * the owner reorders again.
 *
 * Format, UTF-8 text: {@code tvhome-order 1}, then {@code dock <comma-separated dock_apps>}, then optionally
 * {@code docked <n>}, then one tile key (component, flattened short) per line. {@code docked} says how many of the
 * first keys are in the dock, for fill_dock false, where an app the owner moved into the dock is not in dock_apps
 * (#499). Files without it (older launchers) leave the dock to dock_apps; an older launcher reads the line as a key
 * that matches no tile.
 */
final class Order {

    private static final String TAG = "TvHome.Order";
    static final String FILE_NAME = "order.txt";
    private static final String MAGIC = "tvhome-order 1";
    private static final String DOCK = "dock ";
    private static final String DOCKED = "docked ";

    /** dock_apps the order was saved under, comma-joined (package names cannot contain commas). */
    final String dock;
    final List<String> keys;
    /** How many of the first keys are in the dock, or -1 when not recorded (dock_apps decides). */
    final int docked;

    Order(String dock, List<String> keys) {
        this(dock, keys, -1);
    }

    Order(String dock, List<String> keys, int docked) {
        this.dock = dock;
        this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
        this.docked = docked;
    }

    static String dockKey(List<String> dockApps) {
        return String.join(",", dockApps);
    }

    static String encode(Order order) {
        StringBuilder sb = new StringBuilder(MAGIC).append('\n').append(DOCK).append(order.dock).append('\n');
        if (order.docked >= 0) sb.append(DOCKED).append(order.docked).append('\n');
        for (String k : order.keys) sb.append(k).append('\n');
        return sb.toString();
    }

    /** The order in this text, or null when it is not one (wrong header, a blank or duplicate key). */
    static Order decode(String text) {
        if (text == null) return null;
        String[] lines = text.split("\n", -1);
        if (lines.length < 2 || !lines[0].equals(MAGIC) || !lines[1].startsWith(DOCK)) return null;
        int docked = -1, first = 2;
        if (lines.length > 2 && lines[2].startsWith(DOCKED)) {
            try {
                docked = Integer.parseInt(lines[2].substring(DOCKED.length()));
            } catch (NumberFormatException e) {
                return null;
            }
            if (docked < 0) return null;
            first = 3;
        }
        List<String> keys = new ArrayList<>();
        for (int i = first; i < lines.length; i++) {
            String k = lines[i];
            if (k.isEmpty()) {
                if (i == lines.length - 1) break;   // the trailing newline
                return null;
            }
            if (keys.contains(k)) return null;
            keys.add(k);
        }
        if (docked > keys.size()) return null;
        return new Order(lines[1].substring(DOCK.length()), keys, docked);
    }

    /**
     * Rank of each saved key (0 = first) when the order still applies to these dock_apps, else an empty map.
     * Tiles without a rank sort after every ranked one.
     */
    static Map<String, Integer> ranks(Order order, List<String> dockApps) {
        Map<String, Integer> r = new HashMap<>();
        if (order == null || !order.dock.equals(dockKey(dockApps))) return r;
        for (int i = 0; i < order.keys.size(); i++) r.put(order.keys.get(i), i);
        return r;
    }

    /** The saved dock size when the order still applies to these dock_apps and recorded one, else -1. */
    static int docked(Order order, List<String> dockApps) {
        return order == null || !order.dock.equals(dockKey(dockApps)) ? -1 : order.docked;
    }

    /** Sort step for two tiles: saved ones first in saved order, then 0 to fall through to the usual rule. */
    static int compare(Map<String, Integer> ranks, String keyA, String keyB) {
        Integer a = ranks.get(keyA), b = ranks.get(keyB);
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return Integer.compare(a, b);
    }

    // ---- file ------------------------------------------------------------------------------------

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    /** The saved order, or null when there is none or it does not decode (then it is ignored, not deleted). */
    static Order load(File file) {
        if (!file.exists()) return null;
        try {
            Order o = decode(new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8));
            if (o == null) Log.w(TAG, file.getName() + " is not valid; ignored");
            return o;
        } catch (IOException e) {
            Log.w(TAG, "cannot read " + file.getName() + ": " + e);
            return null;
        }
    }

    /** Writes the order atomically. False (and a log line) when the write failed. */
    static boolean save(File file, Order order) {
        AtomicFile af = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = af.startWrite();
            out.write(encode(order).getBytes(StandardCharsets.UTF_8));
            af.finishWrite(out);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "cannot write " + file.getName() + ": " + e);
            if (out != null) af.failWrite(out);
            return false;
        }
    }
}
