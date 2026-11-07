package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ThemeCatalogTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File f, String text) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void catalogFilesAreAllowed() {
        assertEquals("themes/aurora/config.json", ThemeFiles.path(Arrays.asList("themes", "aurora", "config.json")));
        assertEquals("themes/istanbul_v2/wallpaper.jpg",
                ThemeFiles.path(Arrays.asList("themes", "istanbul_v2", "wallpaper.jpg")));
        assertEquals("themes/scenes/wallpapers/01-dusk.jpg",
                ThemeFiles.path(Arrays.asList("themes", "scenes", "wallpapers", "01-dusk.jpg")));
        assertEquals("themes/scenes/wallpapers/captions.txt",
                ThemeFiles.path(Arrays.asList("themes", "scenes", "wallpapers", "captions.txt")));
        assertEquals("device.json", ThemeFiles.path(Collections.singletonList("device.json")));
    }

    @Test public void catalogPathsOutsideAThemeAreRefused() {
        assertNull(ThemeFiles.path(Arrays.asList("themes", "aurora", "theme_revision")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "aurora", "device.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "..", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", ".hidden", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "Aurora", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "aurora", "run.sh")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "aurora", "wallpapers", "..")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "aurora", "sub", "wallpapers", "a.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "config.json")));
    }

    @Test public void foldersADeleteClears() {
        assertTrue(ThemeFiles.isCollection(Arrays.asList("themes", "scenes", "wallpapers")));
        assertTrue(ThemeFiles.isCatalog(Collections.singletonList("themes")));
        assertTrue(ThemeFiles.isCatalog(Arrays.asList("themes", "aurora")));
        assertFalse(ThemeFiles.isCatalog(Arrays.asList("themes", "..")));
        assertFalse(ThemeFiles.isCatalog(Arrays.asList("themes", "aurora", "wallpapers")));
        assertFalse(ThemeFiles.isCatalog(Collections.singletonList("wallpapers")));
    }

    @Test public void namesAreTheFoldersWithAConfig() throws IOException {
        File dir = tmp.getRoot();
        assertTrue(ThemeCatalog.names(dir).isEmpty());
        write(new File(dir, "themes/minimal/config.json"), "{}");
        write(new File(dir, "themes/aurora/config.json"), "{}");
        write(new File(dir, "themes/empty/wallpaper.jpg"), "x");
        write(new File(dir, "themes/Bad Name/config.json"), "{}");
        assertEquals(Arrays.asList("aurora", "minimal"), ThemeCatalog.names(dir));
        assertTrue(ThemeCatalog.names(null).isEmpty());
    }

    @Test public void applyReplacesTheActiveTheme() throws IOException {
        File dir = tmp.getRoot();
        write(new File(dir, "config.json"), "{\"columns\":5}");
        write(new File(dir, "wallpaper.jpg"), "old");
        write(new File(dir, "wallpapers/old.jpg"), "old");
        write(new File(dir, "wallpapers/captions.txt"), "old");
        write(new File(dir, "theme_revision"), "0123456789abcdef");
        write(new File(dir, "themes/scenes/config.json"), "{\"columns\":6}");
        write(new File(dir, "themes/scenes/wallpapers/a.jpg"), "a");
        write(new File(dir, "themes/scenes/wallpapers/captions.txt"), "a\tA");

        ThemeCatalog.apply(dir, "scenes");

        assertEquals("{\"columns\":6}", read(new File(dir, "config.json")));
        assertFalse(new File(dir, "wallpaper.jpg").exists());
        assertFalse(new File(dir, "wallpapers/old.jpg").exists());
        assertEquals("a", read(new File(dir, "wallpapers/a.jpg")));
        assertEquals("a\tA", read(new File(dir, "wallpapers/captions.txt")));
        assertEquals("pick:scenes", read(new File(dir, "theme_revision")));
        assertEquals("scenes", ThemeCatalog.current(dir));
    }

    @Test public void applyCopiesASingleWallpaper() throws IOException {
        File dir = tmp.getRoot();
        write(new File(dir, "themes/aurora/config.json"), "{}");
        write(new File(dir, "themes/aurora/wallpaper.jpg"), "sky");
        ThemeCatalog.apply(dir, "aurora");
        assertEquals("sky", read(new File(dir, "wallpaper.jpg")));
    }

    @Test public void applyRefusesAThemeThatIsNotThere() throws IOException {
        File dir = tmp.getRoot();
        write(new File(dir, "config.json"), "{\"columns\":5}");
        for (String name : new String[]{"missing", "..", "../themes/x"}) {
            try {
                ThemeCatalog.apply(dir, name);
                fail(name);
            } catch (IOException expected) {
                // the active theme is untouched
            }
        }
        assertEquals("{\"columns\":5}", read(new File(dir, "config.json")));
    }

    @Test public void onlyAPickNamesTheCurrentTheme() throws IOException {
        File dir = tmp.getRoot();
        assertNull(ThemeCatalog.current(dir));
        write(new File(dir, "theme_revision"), "0123456789abcdef0123456789abcdef");
        assertNull(ThemeCatalog.current(dir));
        assertNull(ThemeCatalog.picked("pick:"));
        assertNull(ThemeCatalog.picked("pick:../x"));
        assertEquals("glass", ThemeCatalog.picked("pick:glass"));
    }

    @Test public void deleteRemovesAFolderAndEverythingInIt() throws IOException {
        File dir = tmp.getRoot();
        write(new File(dir, "themes/a/config.json"), "{}");
        write(new File(dir, "themes/a/wallpapers/x.jpg"), "x");
        write(new File(dir, "themes/b/config.json"), "{}");
        ThemeCatalog.delete(new File(dir, "themes/a"));
        assertEquals(Collections.singletonList("b"), ThemeCatalog.names(dir));
        ThemeCatalog.delete(new File(dir, "themes"));
        assertFalse(new File(dir, "themes").exists());
    }
}
