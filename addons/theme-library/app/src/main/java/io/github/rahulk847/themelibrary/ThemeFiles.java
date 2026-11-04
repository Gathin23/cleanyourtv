package io.github.rahulk847.themelibrary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A theme as the launcher reads it, and as tools/theme.sh writes it: {@code config.json}, an optional
 * {@code wallpaper.jpg}, and an optional {@code wallpapers/} collection of images with an optional
 * {@code captions.txt}. {@code theme_revision} is a stamp the launcher compares to drop on-TV choices when a new
 * theme arrives; a switch writes a fresh one. Plain java.io, no Android, so the JVM tests cover it.
 */
final class ThemeFiles {
    static final String CONFIG = "config.json";
    static final String WALLPAPER = "wallpaper.jpg";
    static final String WALLPAPERS = "wallpapers";
    static final String CAPTIONS = "captions.txt";
    static final String REVISION = "theme_revision";
    static final int MAX_NAME = 40;

    private ThemeFiles() {
    }

    /** The image types the launcher shows from wallpapers/ (the same four theme.sh pushes). */
    static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    /** True when {@code dir} holds a theme: the launcher ignores wallpapers without a config.json. */
    static boolean hasTheme(File dir) {
        return dir != null && new File(dir, CONFIG).isFile();
    }

    /** The images in wallpapers/, sorted by name; empty when there is none. */
    static List<File> images(File dir) {
        File[] files = new File(dir, WALLPAPERS).listFiles();
        List<File> out = new ArrayList<>();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (File f : files) if (f.isFile() && isImage(f.getName())) out.add(f);
        return out;
    }

    /** Pictures the theme shows: its wallpapers/ collection, else its single wallpaper.jpg. */
    static int pictureCount(File dir) {
        int n = images(dir).size();
        return n > 0 ? n : (new File(dir, WALLPAPER).isFile() ? 1 : 0);
    }

    /**
     * Identifies a theme's content, so the same theme is saved once and the one on screen is recognised in the
     * library: the config bytes, the captions bytes, and each picture's name and size. Null when there is no theme.
     */
    static String fingerprint(File dir) throws IOException {
        if (!hasTheme(dir)) return null;
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        sha.update(read(new File(dir, CONFIG)));
        File single = new File(dir, WALLPAPER);
        sha.update(("|wallpaper:" + (single.isFile() ? single.length() : -1)).getBytes(StandardCharsets.UTF_8));
        for (File f : images(dir)) sha.update(("|" + f.getName() + ":" + f.length()).getBytes(StandardCharsets.UTF_8));
        File captions = new File(new File(dir, WALLPAPERS), CAPTIONS);
        if (captions.isFile()) {
            sha.update("|captions:".getBytes(StandardCharsets.UTF_8));
            sha.update(read(captions));
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : sha.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
        return hex.toString();
    }

    /** Removes the theme files from {@code dir} (and nothing else the launcher keeps there). */
    static void clear(File dir) throws IOException {
        delete(new File(dir, CONFIG));
        delete(new File(dir, WALLPAPER));
        File wallpapers = new File(dir, WALLPAPERS);
        File[] files = wallpapers.listFiles();
        if (files != null) for (File f : files) {
            if (f.isFile() && (isImage(f.getName()) || f.getName().equals(CAPTIONS))) delete(f);
        }
    }

    /**
     * Replaces the theme in {@code to} with the one in {@code from}. The config goes last, so a launcher that starts
     * mid-copy reads either no config (built-in look) or the whole new theme, never a config pointing at half of it.
     */
    static void copy(File from, File to) throws IOException {
        if (!hasTheme(from)) throw new IOException("no " + CONFIG + " in " + from);
        mkdirs(to);
        clear(to);
        File single = new File(from, WALLPAPER);
        if (single.isFile()) copyFile(single, new File(to, WALLPAPER));
        List<File> images = images(from);
        File captions = new File(new File(from, WALLPAPERS), CAPTIONS);
        if (!images.isEmpty() || captions.isFile()) {
            File target = new File(to, WALLPAPERS);
            mkdirs(target);
            for (File f : images) copyFile(f, new File(target, f.getName()));
            if (captions.isFile()) copyFile(captions, new File(target, CAPTIONS));
        }
        copyFile(new File(from, CONFIG), new File(to, CONFIG));
    }

    /** A fresh theme_revision, so the launcher resets the on-TV choices a new theme replaces (as theme.sh does). */
    static void stampRevision(File dir) throws IOException {
        mkdirs(dir);
        write(new File(dir, REVISION), UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.US_ASCII));
    }

    /** A name to show and store: one line, no control characters, at most MAX_NAME characters; fallback if empty. */
    static String cleanName(String raw, String fallback) {
        if (raw == null) return fallback;
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length(); ) {
            int c = raw.codePointAt(i);
            i += Character.charCount(c);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                space = out.length() > 0;
                continue;
            }
            if (space) out.append(' ');
            space = false;
            out.appendCodePoint(c);
        }
        String s = out.toString();
        if (s.codePointCount(0, s.length()) > MAX_NAME) s = s.substring(0, s.offsetByCodePoints(0, MAX_NAME)).trim();
        return s.isEmpty() ? fallback : s;
    }

    static String readText(File f) {
        try {
            return new String(read(f), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    static void writeText(File f, String text) throws IOException {
        write(f, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Deletes a folder and everything in it. */
    static void deleteTree(File f) throws IOException {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        delete(f);
    }

    private static byte[] read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), 16 * 1024 * 1024)];
            int off = 0, n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) off += n;
            return off == buf.length ? buf : Arrays.copyOf(buf, off);
        }
    }

    private static void write(File f, byte[] bytes) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (to.length() != from.length()) throw new IOException("short copy of " + from.getName());
    }

    private static void delete(File f) throws IOException {
        if (f.exists() && !f.delete()) throw new IOException("cannot delete " + f);
    }

    private static void mkdirs(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
    }
}
