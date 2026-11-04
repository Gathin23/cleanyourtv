package io.github.rahulk847.themelibrary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class LibraryTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void saved_themes_load_newest_first_with_their_names() throws IOException {
        Library lib = new Library(new File(tmp.getRoot(), "library"));
        lib.save(ThemeFilesTest.single(tmp.newFolder()), "Aurora", 1000);
        lib.save(ThemeFilesTest.collection(tmp.newFolder()), "Aerial", 2000);

        List<Library.Entry> all = lib.load("x");
        assertEquals(2, all.size());
        assertEquals("Aerial", all.get(0).name);
        assertEquals(3, all.get(0).pictures);
        assertEquals("Aurora", all.get(1).name);
        assertEquals(1000, all.get(1).saved);
    }

    @Test
    public void the_theme_on_screen_is_found_by_content() throws IOException {
        Library lib = new Library(new File(tmp.getRoot(), "library"));
        File onScreen = ThemeFilesTest.collection(tmp.newFolder());
        lib.save(ThemeFilesTest.single(tmp.newFolder()), "Aurora", 1000);
        assertNull(Library.find(lib.load("x"), ThemeFiles.fingerprint(onScreen)));

        Library.Entry saved = lib.save(onScreen, "Aerial", 2000);
        List<Library.Entry> all = lib.load("x");
        assertEquals(saved.dir, Library.find(all, ThemeFiles.fingerprint(onScreen)).dir);
        assertNull(Library.find(all, null));
    }

    @Test
    public void switching_back_restores_the_same_content() throws IOException {
        Library lib = new Library(new File(tmp.getRoot(), "library"));
        File launcher = ThemeFilesTest.collection(tmp.newFolder());
        String original = ThemeFiles.fingerprint(launcher);
        Library.Entry first = lib.save(launcher, "First", 1000);

        ThemeFiles.copy(ThemeFilesTest.single(tmp.newFolder()), launcher);
        assertFalse(original.equals(ThemeFiles.fingerprint(launcher)));

        ThemeFiles.copy(first.dir, launcher);
        assertEquals(original, ThemeFiles.fingerprint(launcher));
    }

    @Test
    public void same_millisecond_saves_get_separate_folders() throws IOException {
        Library lib = new Library(new File(tmp.getRoot(), "library"));
        Library.Entry a = lib.save(ThemeFilesTest.single(tmp.newFolder()), "A", 5);
        Library.Entry b = lib.save(ThemeFilesTest.collection(tmp.newFolder()), "B", 5);
        assertFalse(a.dir.equals(b.dir));
        assertEquals(2, lib.load("x").size());
    }

    @Test
    public void a_failed_save_leaves_no_folder_behind() throws IOException {
        File root = new File(tmp.getRoot(), "library");
        Library lib = new Library(root);
        try {
            lib.save(tmp.newFolder(), "Empty", 7);
            fail("saved a folder without a theme");
        } catch (IOException expected) {
            File[] left = root.listFiles();
            assertTrue(left == null || left.length == 0);
        }
    }

    @Test
    public void rename_and_delete() throws IOException {
        Library lib = new Library(new File(tmp.getRoot(), "library"));
        Library.Entry e = lib.save(ThemeFilesTest.single(tmp.newFolder()), "Old", 1);
        Library.rename(e, "New");
        assertEquals("New", lib.load("x").get(0).name);
        Library.delete(lib.load("x").get(0));
        assertTrue(lib.load("x").isEmpty());
    }

    @Test
    public void a_folder_without_config_or_name_is_handled() throws IOException {
        File root = new File(tmp.getRoot(), "library");
        Library lib = new Library(root);
        new File(root, "broken").mkdirs();
        File unnamed = ThemeFilesTest.single(new File(root, "123"));
        unnamed.mkdirs();
        List<Library.Entry> all = lib.load("Saved theme");
        assertEquals(1, all.size());
        assertEquals("Saved theme", all.get(0).name);
        assertSame(all.get(0), Library.find(all, ThemeFiles.fingerprint(unnamed)));
    }
}
