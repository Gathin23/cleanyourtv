package com.gathin.tvhome;

import java.util.List;
import java.util.regex.Pattern;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);
    /** A catalog theme's folder name (themes/<name>/), as tools/theme-catalog.sh accepts it. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,39}");

    private ThemeFiles() {}

    /**
     * The path relative to the theme directory, e.g. "config.json" or "wallpapers/dusk.jpg", or null if refused.
     * A catalog theme's files are the same under themes/<name>/ (ThemeCatalog).
     */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1 && Config.DEVICE_NAME.equals(segments.get(0))) return Config.DEVICE_NAME;
        String prefix = "";
        if (segments.size() > 2 && ThemeCatalog.DIR.equals(segments.get(0)) && themeName(segments.get(1))) {
            prefix = segments.get(0) + "/" + segments.get(1) + "/";
            segments = segments.subList(2, segments.size());
        }
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name)
                    || prefix.isEmpty() && "theme_revision".equals(name) ? prefix + name : null;
        }
        if (segments.size() == 2 && WALLPAPERS.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return prefix + WALLPAPERS + "/" + segments.get(1);
        }
        return null;
    }

    /** True for "wallpapers" alone or themes/<name>/wallpapers: a delete of it clears the collection's images. */
    static boolean isCollection(List<String> segments) {
        return segments != null && (segments.size() == 1 && WALLPAPERS.equals(segments.get(0))
                || segments.size() == 3 && ThemeCatalog.DIR.equals(segments.get(0)) && themeName(segments.get(1))
                && WALLPAPERS.equals(segments.get(2)));
    }

    /** True for "themes" or themes/<name>: a delete of it removes that part of the catalog. */
    static boolean isCatalog(List<String> segments) {
        return segments != null && !segments.isEmpty() && segments.size() <= 2
                && ThemeCatalog.DIR.equals(segments.get(0)) && (segments.size() == 1 || themeName(segments.get(1)));
    }

    static boolean themeName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    static boolean collectionImage(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }
}
