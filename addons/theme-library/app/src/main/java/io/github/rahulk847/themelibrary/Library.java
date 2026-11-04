package io.github.rahulk847.themelibrary;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The saved themes: one folder each under {@code root}, holding the theme files plus {@code name.txt} and
 * {@code saved.txt} (milliseconds since 1970). Newest first. Plain java.io, so the JVM tests cover it.
 */
final class Library {
    static final String NAME = "name.txt";
    static final String SAVED = "saved.txt";
    /** Where a downloaded theme came from: its folder name in the CleanYourTV repo. Absent for saved ones. */
    static final String SOURCE = "source.txt";

    static final class Entry {
        final File dir;
        final String name;
        final long saved;
        final String fingerprint;
        final int pictures;
        final String source;

        Entry(File dir, String name, long saved, String fingerprint, int pictures, String source) {
            this.dir = dir;
            this.name = name;
            this.saved = saved;
            this.fingerprint = fingerprint;
            this.pictures = pictures;
            this.source = source;
        }
    }

    private final File root;

    Library(File root) {
        this.root = root;
    }

    /** Every saved theme, newest first. A folder without a config.json is skipped, not shown broken. */
    List<Entry> load(String fallbackName) {
        List<Entry> out = new ArrayList<>();
        File[] dirs = root.listFiles();
        if (dirs == null) return out;
        for (File d : dirs) {
            if (!d.isDirectory() || !ThemeFiles.hasTheme(d)) continue;
            try {
                String source = ThemeFiles.readText(new File(d, SOURCE));
                out.add(new Entry(d, ThemeFiles.cleanName(ThemeFiles.readText(new File(d, NAME)), fallbackName),
                        savedTime(d), ThemeFiles.fingerprint(d), ThemeFiles.pictureCount(d),
                        source.isEmpty() ? null : source));
            } catch (IOException ignored) {
                // An unreadable folder is left out; the rest of the library still shows.
            }
        }
        out.sort((a, b) -> a.saved != b.saved ? Long.compare(b.saved, a.saved) : a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /** The saved theme with this content, or null. */
    static Entry find(List<Entry> entries, String fingerprint) {
        if (fingerprint == null) return null;
        for (Entry e : entries) if (fingerprint.equals(e.fingerprint)) return e;
        return null;
    }

    /** The saved theme downloaded from this repo folder, or else one with the same name; null if neither. */
    static Entry fromRepo(List<Entry> entries, String folder, String prettyName) {
        for (Entry e : entries) if (folder.equals(e.source)) return e;
        for (Entry e : entries) if (e.source == null && e.name.equalsIgnoreCase(prettyName)) return e;
        return null;
    }

    /** Records which repo folder a saved theme matches, so the download list shows it as already in the library. */
    static void markSource(Entry e, String folder) throws IOException {
        ThemeFiles.writeText(new File(e.dir, SOURCE), folder);
    }

    /** Copies the theme in {@code source} into a new library folder. A half-written copy is removed. */
    Entry save(File source, String name, long now) throws IOException {
        return save(source, name, now, null);
    }

    /** As {@link #save(File, String, long)}, recording the repo folder a downloaded theme came from. */
    Entry save(File source, String name, long now, String repoFolder) throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("cannot create " + root);
        File dir = new File(root, Long.toString(now));
        for (int i = 1; dir.exists(); i++) dir = new File(root, now + "-" + i);
        try {
            ThemeFiles.copy(source, dir);
            ThemeFiles.writeText(new File(dir, NAME), name);
            ThemeFiles.writeText(new File(dir, SAVED), Long.toString(now));
            if (repoFolder != null) ThemeFiles.writeText(new File(dir, SOURCE), repoFolder);
            return new Entry(dir, name, now, ThemeFiles.fingerprint(dir), ThemeFiles.pictureCount(dir), repoFolder);
        } catch (IOException e) {
            try {
                ThemeFiles.deleteTree(dir);
            } catch (IOException ignored) {
                // Nothing more to do; load() skips a folder without a config.json.
            }
            throw e;
        }
    }

    static void rename(Entry e, String name) throws IOException {
        ThemeFiles.writeText(new File(e.dir, NAME), name);
    }

    static void delete(Entry e) throws IOException {
        ThemeFiles.deleteTree(e.dir);
    }

    private static long savedTime(File dir) {
        try {
            return Long.parseLong(ThemeFiles.readText(new File(dir, SAVED)));
        } catch (NumberFormatException e) {
            return dir.lastModified();
        }
    }
}
