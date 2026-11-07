package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Themes kept on the TV under themes/<name>/ by tools/theme-catalog.sh, each laid out like a pushed theme. Launcher
 * settings lists them and applies one by copying its files over the active theme, as theme.sh would, so everything
 * that reads the active theme stays as it is. The copy writes theme_revision as "pick:<name>": Config then clears
 * the on-TV choices as after a push, and the settings row reads the name back from it.
 */
final class ThemeCatalog {
    static final String DIR = "themes";
    private static final String PICK = "pick:";

    private ThemeCatalog() {}

    /** The catalog's theme names, sorted; empty without a catalog, which hides the settings row. */
    static List<String> names(File dir) {
        List<String> names = new ArrayList<>();
        File[] folders = dir == null ? null : new File(dir, DIR).listFiles();
        if (folders != null) for (File f : folders) {
            if (ThemeFiles.themeName(f.getName()) && new File(f, Config.FILE_NAME).isFile()) names.add(f.getName());
        }
        Collections.sort(names);
        return names;
    }

    /** The theme picked last, or null after a theme.sh push or reset (they write another revision). */
    static String current(File dir) {
        String token = dir == null ? "absent" : Config.revisionToken(new File(dir, Config.THEME_REVISION_NAME));
        return picked(token);
    }

    static String picked(String token) {
        return token.startsWith(PICK) && ThemeFiles.themeName(token.substring(PICK.length()))
                ? token.substring(PICK.length()) : null;
    }

    /**
     * Makes themes/<name> the active theme. The pictures go first and config.json and theme_revision last, so a
     * copy that fails part way leaves the old theme's settings in force rather than half of the new one.
     */
    static void apply(File dir, String name) throws IOException {
        File theme = new File(new File(dir, DIR), name);
        if (!ThemeFiles.themeName(name) || !new File(theme, Config.FILE_NAME).isFile()) throw new IOException(name);
        File wallpapers = new File(dir, ThemeFiles.WALLPAPERS);
        File[] old = wallpapers.listFiles();
        if (old != null) for (File f : old) if (collectionFile(f.getName())) remove(f);
        File[] pictures = new File(theme, ThemeFiles.WALLPAPERS).listFiles();
        if (pictures != null) for (File f : pictures) {
            if (collectionFile(f.getName())) copy(f, new File(wallpapers, f.getName()));
        }
        File wallpaper = new File(theme, Config.WALLPAPER_NAME);
        if (wallpaper.isFile()) copy(wallpaper, new File(dir, Config.WALLPAPER_NAME));
        else remove(new File(dir, Config.WALLPAPER_NAME));
        copy(new File(theme, Config.FILE_NAME), new File(dir, Config.FILE_NAME));
        File revision = new File(dir, Config.THEME_REVISION_NAME);
        remove(revision);
        try (OutputStream out = new FileOutputStream(revision)) {
            out.write((PICK + name).getBytes(StandardCharsets.US_ASCII));
        }
    }

    /** Deletes a file or a folder with everything in it; the number of files deleted. */
    static int delete(File f) {
        int n = 0;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) n += delete(c);
        return f.delete() ? n + 1 : n;
    }

    private static boolean collectionFile(String name) {
        return ThemeFiles.collectionImage(name) || WallpaperCaptions.FILE_NAME.equals(name);
    }

    /** A file theme.sh's shell fallback made belongs to the shell; delete it so the copy is the launcher's own. */
    private static void remove(File f) throws IOException {
        if (f.exists() && !f.delete()) throw new IOException("cannot replace " + f);
    }

    private static void copy(File from, File to) throws IOException {
        remove(to);
        File parent = to.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent);
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        }
    }
}
