package io.github.rahulk847.themelibrary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ThemeFilesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    static void put(File dir, String path, String content) throws IOException {
        File f = new File(dir, path);
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** A rotating-collection theme like aerial: config, three pictures and captions. */
    static File collection(File dir) throws IOException {
        put(dir, "config.json", "{\"wallpaper\": true, \"wallpaper_rotation_minutes\": 15}");
        put(dir, "wallpapers/01.jpg", "aaaa");
        put(dir, "wallpapers/02.png", "bbbbbb");
        put(dir, "wallpapers/03.webp", "cc");
        put(dir, "wallpapers/captions.txt", "01.jpg\tDusk");
        return dir;
    }

    /** A single-picture theme like aurora. */
    static File single(File dir) throws IOException {
        put(dir, "config.json", "{\"background_color\": \"#070A14\", \"wallpaper\": true}");
        put(dir, "wallpaper.jpg", "picture");
        return dir;
    }

    @Test
    public void images_are_the_four_types_sorted_and_captions_are_not_an_image() throws IOException {
        File d = collection(tmp.newFolder());
        put(d, "wallpapers/notes.md", "x");
        put(d, "wallpapers/00.JPEG", "x");
        List<File> images = ThemeFiles.images(d);
        assertEquals(4, images.size());
        assertEquals("00.JPEG", images.get(0).getName());
        assertEquals("03.webp", images.get(3).getName());
    }

    @Test
    public void picture_count_prefers_the_collection_then_the_single_wallpaper() throws IOException {
        assertEquals(3, ThemeFiles.pictureCount(collection(tmp.newFolder())));
        assertEquals(1, ThemeFiles.pictureCount(single(tmp.newFolder())));
        File none = tmp.newFolder();
        put(none, "config.json", "{}");
        assertEquals(0, ThemeFiles.pictureCount(none));
    }

    @Test
    public void no_config_means_no_theme_and_no_fingerprint() throws IOException {
        File d = tmp.newFolder();
        put(d, "wallpaper.jpg", "orphan");
        assertFalse(ThemeFiles.hasTheme(d));
        assertNull(ThemeFiles.fingerprint(d));
    }

    @Test
    public void fingerprint_is_the_same_for_a_copy_and_changes_with_content() throws IOException {
        File a = collection(tmp.newFolder());
        File b = tmp.newFolder();
        ThemeFiles.copy(a, b);
        assertEquals(ThemeFiles.fingerprint(a), ThemeFiles.fingerprint(b));

        put(b, "config.json", "{\"wallpaper\": false}");
        assertNotEquals(ThemeFiles.fingerprint(a), ThemeFiles.fingerprint(b));
    }

    @Test
    public void fingerprint_ignores_the_revision_stamp() throws IOException {
        File a = single(tmp.newFolder());
        String before = ThemeFiles.fingerprint(a);
        ThemeFiles.stampRevision(a);
        assertEquals(before, ThemeFiles.fingerprint(a));
    }

    @Test
    public void copy_replaces_every_theme_file_and_keeps_other_launcher_files() throws IOException {
        File target = collection(tmp.newFolder());
        put(target, "unrelated.txt", "launcher data");
        ThemeFiles.copy(single(tmp.newFolder()), target);

        assertTrue(new File(target, "wallpaper.jpg").isFile());
        assertTrue("old collection images are gone", ThemeFiles.images(target).isEmpty());
        assertFalse("old captions are gone", new File(target, "wallpapers/captions.txt").exists());
        assertTrue(new File(target, "unrelated.txt").isFile());
        assertEquals("{\"background_color\": \"#070A14\", \"wallpaper\": true}",
                ThemeFiles.readText(new File(target, "config.json")));
    }

    @Test
    public void copy_from_a_folder_without_a_theme_changes_nothing() throws IOException {
        File target = single(tmp.newFolder());
        String before = ThemeFiles.fingerprint(target);
        try {
            ThemeFiles.copy(tmp.newFolder(), target);
            fail("copied an empty folder");
        } catch (IOException expected) {
            assertEquals(before, ThemeFiles.fingerprint(target));
        }
    }

    @Test
    public void clear_leaves_the_built_in_look() throws IOException {
        File d = collection(tmp.newFolder());
        put(d, "wallpaper.jpg", "x");
        ThemeFiles.clear(d);
        assertFalse(ThemeFiles.hasTheme(d));
        assertEquals(0, ThemeFiles.pictureCount(d));
        assertFalse(new File(d, "wallpapers/captions.txt").exists());
    }

    @Test
    public void revision_is_short_ascii_and_new_each_time() throws IOException {
        File d = tmp.newFolder();
        ThemeFiles.stampRevision(d);
        String one = ThemeFiles.readText(new File(d, "theme_revision"));
        ThemeFiles.stampRevision(d);
        String two = ThemeFiles.readText(new File(d, "theme_revision"));
        assertNotNull(one);
        assertTrue("the launcher reads at most 64 bytes", one.length() <= 64 && one.length() >= 16);
        assertNotEquals(one, two);
    }

    @Test
    public void names_are_one_trimmed_line_of_at_most_forty_characters() {
        assertEquals("Family photo", ThemeFiles.cleanName("  Family \n\t photo  ", "x"));
        assertEquals("fallback", ThemeFiles.cleanName("  \n ", "fallback"));
        assertEquals("fallback", ThemeFiles.cleanName(null, "fallback"));
        assertEquals(40, ThemeFiles.cleanName("a".repeat(60), "x").length());
        assertEquals("Diwali 🪔", ThemeFiles.cleanName("Diwali 🪔", "x"));
    }
}
