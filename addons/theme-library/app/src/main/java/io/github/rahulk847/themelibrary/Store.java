package io.github.rahulk847.themelibrary;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rules for downloading themes from the CleanYourTV repository: which repository (only that one), which
 * folder and file names are accepted, which files of a theme folder are kept, the size limits, and reading the
 * one-line descriptions out of themes/README.md. No Android and no network here, so the JVM tests cover it.
 */
final class Store {
    static final String OWNER_REPO = "Gathin23/cleanyourtv";
    static final String BRANCH = "main";
    static final String API = "https://api.github.com/repos/" + OWNER_REPO + "/contents/themes";
    static final String RAW = "https://raw.githubusercontent.com/" + OWNER_REPO + "/" + BRANCH + "/themes/";
    static final String README = RAW + "README.md";
    /** One picture, and one whole theme: the largest in the repo today is about 3.3 MB. */
    static final long MAX_FILE = 10L * 1024 * 1024;
    static final long MAX_THEME = 40L * 1024 * 1024;
    static final int MAX_DESCRIPTION = 150;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}");
    private static final Pattern ROW = Pattern.compile("^\\|\\s*`([A-Za-z0-9_.-]+)/`\\s*\\|([^|]*)\\|");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");

    private Store() {
    }

    /** A folder or file name that is safe to use as a path part on the TV: no slashes, no "..", no hidden files. */
    static boolean safeName(String name) {
        return name != null && NAME.matcher(name).matches() && !name.contains("..");
    }

    /** API URL listing a theme folder, or its wallpapers/ subfolder. */
    static String folderUrl(String folder, boolean wallpapers) {
        return API + "/" + folder + (wallpapers ? "/" + ThemeFiles.WALLPAPERS : "") + "?ref=" + BRANCH;
    }

    /**
     * Where a file of a theme folder goes in the downloaded theme, or null to skip it. Kept: config.json,
     * wallpaper.jpg, and in wallpapers/ the images and captions.txt. Skipped: screenshots, credits, scripts.
     */
    static String keep(String name, boolean inWallpapers) {
        if (!safeName(name)) return null;
        if (inWallpapers) {
            return ThemeFiles.isImage(name) || name.equals(ThemeFiles.CAPTIONS) ? ThemeFiles.WALLPAPERS + "/" + name : null;
        }
        return name.equals(ThemeFiles.CONFIG) || name.equals(ThemeFiles.WALLPAPER) ? name : null;
    }

    /** Only files from this repository's raw host and branch are downloaded, whatever the listing says. */
    static boolean trustedDownload(String url, String folder) {
        return url != null && url.startsWith(RAW + folder + "/") && !url.contains("..") && !url.contains("?");
    }

    /** "red-rain" becomes "Red rain", "six-columns" becomes "Six columns". */
    static String prettyName(String folder) {
        String s = folder.replace('-', ' ').replace('_', ' ').trim();
        return s.isEmpty() ? folder : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /**
     * The description cell of each row of themes/README.md's table, keyed by folder name, as plain one-line text
     * (links reduced to their text, code marks and bold removed), cut at MAX_DESCRIPTION characters.
     */
    static Map<String, String> descriptions(String readme) {
        Map<String, String> out = new LinkedHashMap<>();
        if (readme == null) return out;
        for (String line : readme.split("\n")) {
            Matcher m = ROW.matcher(line.trim());
            if (!m.find()) continue;
            String text = LINK.matcher(m.group(2)).replaceAll("$1").replace("`", "").replace("**", "").trim();
            text = text.replaceAll("\\s+", " ");
            if (text.length() > MAX_DESCRIPTION) {
                int cut = text.lastIndexOf(' ', MAX_DESCRIPTION - 1);
                text = text.substring(0, cut > 40 ? cut : MAX_DESCRIPTION - 1).replaceAll("[\\s,;:.]+$", "") + "…";
            }
            out.put(m.group(1), text);
        }
        return out;
    }
}
