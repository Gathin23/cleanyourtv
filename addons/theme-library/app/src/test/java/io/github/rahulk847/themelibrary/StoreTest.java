package io.github.rahulk847.themelibrary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

public class StoreTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void names_that_could_leave_the_folder_are_refused() {
        assertTrue(Store.safeName("red-rain"));
        assertTrue(Store.safeName("01-amber-demon.jpg"));
        assertFalse(Store.safeName(".."));
        assertFalse(Store.safeName("a..b"));
        assertFalse(Store.safeName("../config.json"));
        assertFalse(Store.safeName("a/b"));
        assertFalse(Store.safeName(".hidden"));
        assertFalse(Store.safeName(""));
        assertFalse(Store.safeName(null));
        assertFalse(Store.safeName("x".repeat(65)));
    }

    @Test
    public void only_theme_files_are_kept() {
        assertEquals("config.json", Store.keep("config.json", false));
        assertEquals("wallpaper.jpg", Store.keep("wallpaper.jpg", false));
        assertNull(Store.keep("screenshot.jpg", false));
        assertNull(Store.keep("CREDITS.md", false));
        assertNull(Store.keep("make_wallpaper.py", false));
        assertEquals("wallpapers/01.jpg", Store.keep("01.jpg", true));
        assertEquals("wallpapers/captions.txt", Store.keep("captions.txt", true));
        assertNull(Store.keep("config.json", true));
        assertNull(Store.keep("notes.md", true));
        assertNull(Store.keep("../01.jpg", true));
    }

    @Test
    public void downloads_come_only_from_this_repository_and_folder() {
        String ok = "https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/themes/aurora/wallpaper.jpg";
        assertTrue(Store.trustedDownload(ok, "aurora"));
        assertFalse(Store.trustedDownload(ok, "weeb"));
        assertFalse(Store.trustedDownload("https://raw.githubusercontent.com/someone/fork/main/themes/aurora/wallpaper.jpg", "aurora"));
        assertFalse(Store.trustedDownload("http://raw.githubusercontent.com/Gathin23/cleanyourtv/main/themes/aurora/x.jpg", "aurora"));
        assertFalse(Store.trustedDownload("https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/themes/aurora/../../x", "aurora"));
        assertFalse(Store.trustedDownload(ok + "?token=1", "aurora"));
        assertFalse(Store.trustedDownload(null, "aurora"));
    }

    @Test
    public void folder_urls_point_at_the_contents_api_on_main() {
        assertEquals("https://api.github.com/repos/Gathin23/cleanyourtv/contents/themes/aerial?ref=main",
                Store.folderUrl("aerial", false));
        assertEquals("https://api.github.com/repos/Gathin23/cleanyourtv/contents/themes/aerial/wallpapers?ref=main",
                Store.folderUrl("aerial", true));
    }

    @Test
    public void pretty_names() {
        assertEquals("Red rain", Store.prettyName("red-rain"));
        assertEquals("Aerial", Store.prettyName("aerial"));
        assertEquals("Six columns", Store.prettyName("six-columns"));
    }

    @Test
    public void descriptions_are_read_from_table_rows_as_plain_text() {
        String md = "# Themes\n\n| Theme | Look | Shot |\n|---|---|---|\n"
                + "| `aurora/` | A dark aurora wallpaper with a **matching** colour | <img src=\"x\"> |\n"
                + "| `retro/` | Pure black. By [REGT-URRED](https://github.com/REGT-URRED), see `retro/CREDITS.md` | <img> |\n"
                + "| not a row |\n";
        Map<String, String> d = Store.descriptions(md);
        assertEquals(2, d.size());
        assertEquals("A dark aurora wallpaper with a matching colour", d.get("aurora"));
        assertEquals("Pure black. By REGT-URRED, see retro/CREDITS.md", d.get("retro"));
        assertTrue(Store.descriptions(null).isEmpty());
    }

    @Test
    public void long_descriptions_are_cut_at_a_word() {
        String md = "| `long/` | " + "word ".repeat(80) + "| <img> |";
        String d = Store.descriptions(md).get("long");
        assertTrue(d.length() <= Store.MAX_DESCRIPTION);
        assertTrue(d.endsWith("word…"));
    }

    @Test
    public void the_repo_readme_describes_every_theme_folder_in_this_checkout() throws IOException {
        File themes = new File("../../../themes");
        File readme = new File(themes, "README.md");
        if (!readme.isFile()) return; // built outside the repository
        Map<String, String> d = Store.descriptions(new String(Files.readAllBytes(readme.toPath()), StandardCharsets.UTF_8));
        File[] dirs = themes.listFiles(File::isDirectory);
        assertNotNull(dirs);
        for (File dir : dirs) {
            if (!new File(dir, "config.json").isFile()) continue;
            assertNotNull("no README description read for " + dir.getName(), d.get(dir.getName()));
            assertTrue(Store.safeName(dir.getName()));
        }
    }

    @Test
    public void a_downloaded_theme_is_recognised_by_folder_then_by_name() throws IOException {
        Library lib = new Library(new File(tmp.getRoot(), "library"));
        lib.save(ThemeFilesTest.single(tmp.newFolder()), "Aurora", 1);
        lib.save(ThemeFilesTest.collection(tmp.newFolder()), "My space photos", 2, "aerial");

        assertEquals("My space photos", Library.fromRepo(lib.load("x"), "aerial", "Aerial").name);
        assertEquals("Aurora", Library.fromRepo(lib.load("x"), "aurora", "Aurora").name);
        assertNull(Library.fromRepo(lib.load("x"), "weeb", "Weeb"));

        Library.markSource(Library.fromRepo(lib.load("x"), "aurora", "Aurora"), "aurora");
        Library.rename(Library.fromRepo(lib.load("x"), "aurora", "Aurora"), "Northern lights");
        assertEquals("Northern lights", Library.fromRepo(lib.load("x"), "aurora", "Aurora").name);
    }
}
