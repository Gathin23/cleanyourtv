package io.github.maxhayim.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Against this checkout's own READMEs, the files the TV reads from the repository. */
public class CatalogTest {
    /** The repository root: the tests run in addons/manager/app. */
    private static String read(String path) throws Exception {
        File root = new File("../../..").getCanonicalFile();
        return new String(Files.readAllBytes(new File(root, path).toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void everyAddonFolderIsInTheCatalog() throws Exception {
        List<Catalog.Item> addons = Catalog.addons(read("addons/README.md"));
        File[] folders = new File("../..").getCanonicalFile().listFiles(File::isDirectory);
        assertNotNull(folders);
        assertEquals(folders.length, addons.size());
        for (Catalog.Item a : addons) assertTrue(a.id, new File("../../" + a.id).isDirectory());
    }

    @Test
    public void themesWithTheirScreenshots() throws Exception {
        List<Catalog.Item> themes = Catalog.themes(read("themes/README.md"));
        assertTrue(themes.size() >= 5);
        for (Catalog.Item t : themes) {
            assertNotNull(t.id, t.screenshot);
            assertTrue(t.screenshot, new File("../../../" + t.screenshot).isFile());
        }
    }

    @Test
    public void switchesWithTheirTypesAndScreenshots() throws Exception {
        List<Catalog.Item> sw = Catalog.switches(read("themes/README.md"), read("docs/switches.md"));
        assertTrue(sw.size() > 40);
        Catalog.Item columns = null;
        for (Catalog.Item s : sw) if (s.id.equals("columns")) columns = s;
        assertNotNull(columns);
        assertEquals("themes/six-columns/screenshot.jpg", columns.screenshot);
        assertTrue(columns.type.length() > 0);
    }

    @Test
    public void packagesFromGradle() {
        assertEquals("a.b.c", Catalog.packageOf("    applicationId 'a.b.c'\n"));
        assertEquals("io.github.maxhayim.tvhub", Catalog.packageOf("applicationId = \"io.github.maxhayim.tvhub\""));
        assertNull(Catalog.packageOf("android {}"));
    }

    @Test
    public void commandsAreTheCreatorsWay() {
        Catalog.Item weather = new Catalog.Item(Catalog.Kind.ADDON, "weather", "Weather", "", "", "", "", null, null);
        assertEquals("TV_HOST=10.0.0.5 tools/venv/bin/python -I tools/manage.py install weather",
                Catalog.installCommand(weather, "10.0.0.5"));
        Catalog.Item aurora = new Catalog.Item(Catalog.Kind.THEME, "aurora", "aurora", "", null, null, null, null, null);
        assertEquals("TV_HOST=<tv ip> tools/theme.sh themes/aurora", Catalog.installCommand(aurora, null));
    }

    @Test
    public void paths() {
        assertEquals("themes/x/screenshot.jpg", Catalog.resolve("docs/", "../themes/x/screenshot.jpg"));
        assertEquals("docs/sky.jpg", Catalog.resolve("docs/", "sky.jpg"));
    }
}
