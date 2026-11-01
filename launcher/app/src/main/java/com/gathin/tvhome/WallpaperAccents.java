package com.gathin.tvhome;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One accent colour per wallpaper: {@code wallpapers/accents.txt}, one "filename<TAB>#RRGGBB" per line. Blank lines,
 * lines starting with # and anything that does not parse are skipped. No Android types, so it is tested on the JVM.
 */
final class WallpaperAccents {
    static final String FILE_NAME = "accents.txt";

    private WallpaperAccents() { }

    static Map<String, Integer> parse(List<String> lines) {
        Map<String, Integer> accents = new HashMap<>();
        for (String line : lines) {
            if (line == null || line.trim().startsWith("#")) continue;
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String file = line.substring(0, tab).trim();
            String hex = line.substring(tab + 1).trim();
            if (file.isEmpty() || hex.length() != 7 || hex.charAt(0) != '#') continue;
            try {
                accents.put(file, 0xFF000000 | Integer.parseInt(hex.substring(1), 16));
            } catch (NumberFormatException ignored) {
                // A colour that does not parse leaves that picture with the theme's colour.
            }
        }
        return accents;
    }

    /** The accents in {@code folder}/accents.txt, or none when the file is missing or unreadable. */
    static Map<String, Integer> read(File folder) {
        File file = folder == null ? null : new File(folder, FILE_NAME);
        if (file == null || !file.isFile() || file.length() > 256 * 1024) return Collections.emptyMap();
        try {
            return parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
