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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent order and pinned state for the installed-app catalogue. */
final class LibraryOrder {
    private static final String TAG = "TvHome.LibraryOrder";
    static final String FILE_NAME = "library_order.txt";
    private static final String MAGIC = "tvhome-library 1";
    private static final String PINNED = "pinned ";
    private static final String ORDER = "order ";
    private static final int MAX_ENTRIES = 128;
    private static final int MAX_BYTES = MAX_ENTRIES * 256;

    final List<String> keys;
    final Set<String> pinned;

    LibraryOrder(List<String> keys, Set<String> pinned) {
        List<String> unique = new ArrayList<>();
        for (String key : keys) {
            if (!LibraryApps.validPackage(key) || !unique.add(key) || unique.size() > MAX_ENTRIES)
                throw new IllegalArgumentException("Invalid library order");
        }
        Set<String> savedPinned = new HashSet<>();
        for (String key : pinned) {
            if (!unique.contains(key) || !savedPinned.add(key)) throw new IllegalArgumentException("Invalid pinned app");
        }
        this.keys = Collections.unmodifiableList(unique);
        this.pinned = Collections.unmodifiableSet(savedPinned);
    }

    static LibraryOrder empty() { return new LibraryOrder(Collections.emptyList(), Collections.emptySet()); }

    boolean isPinned(String pkg) { return pinned.contains(pkg); }

    LibraryOrder pin(String pkg, List<String> catalog) {
        if (!LibraryApps.validPackage(pkg)) throw new IllegalArgumentException("Invalid package");
        List<String> next = new ArrayList<>(sort(catalog));
        if (!next.contains(pkg)) next.add(pkg);
        next.remove(pkg);
        next.add(0, pkg);
        Set<String> nextPinned = new HashSet<>(pinned);
        nextPinned.add(pkg);
        return new LibraryOrder(next, nextPinned);
    }

    LibraryOrder unpin(String pkg) {
        Set<String> nextPinned = new HashSet<>(pinned);
        nextPinned.remove(pkg);
        return new LibraryOrder(keys, nextPinned);
    }

    LibraryOrder move(String pkg, int delta, List<String> catalog) {
        List<String> next = new ArrayList<>(sort(catalog));
        int from = next.indexOf(pkg);
        if (from < 0) return this;
        int to = Math.max(0, Math.min(next.size() - 1, from + delta));
        if (from != to) {
            next.remove(from);
            next.add(to, pkg);
        }
        return new LibraryOrder(next, pinned);
    }

    List<String> topBarApps(List<String> configured) {
        List<String> result = new ArrayList<>(configured);
        for (String key : sort(keys)) {
            if (pinned.contains(key) && !result.contains(key)) result.add(key);
        }
        return result;
    }

    List<String> sort(List<String> catalog) {
        Set<String> available = new HashSet<>(catalog);
        Set<String> added = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            if (available.contains(key) && pinned.contains(key) && added.add(key)) result.add(key);
        }
        for (String key : keys) {
            if (available.contains(key) && added.add(key)) result.add(key);
        }
        for (String key : catalog) {
            if (added.add(key)) result.add(key);
        }
        return result;
    }

    static String encode(LibraryOrder order) {
        StringBuilder text = new StringBuilder(MAGIC).append('\n');
        for (String key : order.keys) {
            if (order.pinned.contains(key)) text.append(PINNED).append(key).append('\n');
        }
        for (String key : order.keys) text.append(ORDER).append(key).append('\n');
        return text.toString();
    }

    static LibraryOrder decode(String text) {
        if (text == null || text.length() > MAX_BYTES) return null;
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !MAGIC.equals(lines[0])) return null;
        List<String> keys = new ArrayList<>();
        Set<String> pinned = new HashSet<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                if (i == lines.length - 1) continue;
                return null;
            }
            if (line.startsWith(PINNED)) {
                String key = line.substring(PINNED.length());
                if (!LibraryApps.validPackage(key) || !pinned.add(key)) return null;
            } else if (line.startsWith(ORDER)) {
                String key = line.substring(ORDER.length());
                if (!LibraryApps.validPackage(key) || keys.contains(key) || !keys.add(key) || keys.size() > MAX_ENTRIES) return null;
            } else {
                return null;
            }
        }
        if (!keys.containsAll(pinned)) return null;
        return new LibraryOrder(keys, pinned);
    }

    static File file(Context context) { return new File(context.getFilesDir(), FILE_NAME); }

    static LibraryOrder load(Context context) { return load(file(context)); }

    static LibraryOrder load(File file) {
        if (!file.exists()) return empty();
        try {
            LibraryOrder order = decode(new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8));
            if (order == null) Log.w(TAG, file.getName() + " is not valid; ignored");
            return order == null ? empty() : order;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "cannot read " + file.getName() + ": " + e);
            return empty();
        }
    }

    static boolean save(Context context, LibraryOrder order) { return save(file(context), order); }

    static boolean save(File file, LibraryOrder order) {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            output.write(encode(order).getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(output);
            return true;
        } catch (IOException | RuntimeException e) {
            if (output != null) atomic.failWrite(output);
            Log.w(TAG, "cannot write " + file.getName() + ": " + e);
            return false;
        }
    }
}
