package io.github.vitorcamillo.wolfgames;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class WolfCatalogSyncTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private static final String HOST = "11111111-2222-4333-8444-555555555555";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x49, 0x45, 0x4e, 0x44, (byte) 0xae, 0x42, 0x60, (byte) 0x82};
    private static class Fake implements WolfCatalogSync.Source {
        List<WolfCatalogSync.Entry> entries = new ArrayList<>();
        byte[] image = PNG;
        String failure;
        String host = HOST;
        int downloads;
        Runnable onGames, onCover;
        public String hostUuid() { return host; }
        public List<WolfCatalogSync.Entry> games() {
            if (onGames != null) onGames.run();
            return entries;
        }
        public byte[] cover(String id) throws WolfControlClient.ControlException {
            downloads++;
            if (onCover != null) onCover.run();
            if (failure != null) throw new WolfControlClient.ControlException(failure);
            return image;
        }
    }
    private WolfCatalogSync.Entry entry(String id, String name) { return new WolfCatalogSync.Entry(id, name, id + ".png"); }
    private JSONObject json(File dir) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(new File(dir, "catalog.json").toPath()), StandardCharsets.UTF_8));
    }
    @Test public void writesStableCatalogAndCleansOnlyOldCovers() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake();
        fake.entries.addAll(Arrays.asList(entry("1", "One"), entry("2", "Two")));
        assertTrue(WolfCatalogSync.sync(fake, dir));
        assertTrue(new File(dir, "wolf-111111112222-1.png").isFile());assertTrue(new File(dir, "wolf-111111112222-2.png").isFile());
        File catalog = new File(dir, "catalog.json");assertTrue(catalog.setLastModified(100000));
        assertFalse(WolfCatalogSync.sync(fake, dir));assertEquals(100000, catalog.lastModified());
        File other = new File(dir, "other.png");Files.write(other.toPath(), PNG);
        fake.entries.remove(1);assertTrue(WolfCatalogSync.sync(fake, dir));
        assertFalse(new File(dir, "wolf-111111112222-2.png").exists());assertTrue(other.exists());
        JSONObject data = json(dir);JSONArray games = data.getJSONArray("games");List<MoonlightCatalog.Game> parsed = new ArrayList<>();
        for (int i = 0; i < games.length(); i++) {
            JSONObject game = games.getJSONObject(i);
            parsed.add(new MoonlightCatalog.Game(game.getString("name"), game.optString("app_id", null), game.optString("image", null)));
        }
        assertEquals(1, new MoonlightCatalog(data.getString("host_name"), data.getString("host_uuid"), parsed).games.size());
    }
    @Test public void preservesHostsAndDropsDuplicateNamesAndBadCovers() throws Exception {
        File dir = folder.newFolder();Fake fake = new Fake();
        Files.write(new File(dir, "catalog.json").toPath(), ("{\"host_name\":\"My Wolf\",\"hosts\":[{\"name\":\"Other\",\"uuid\":\"" + HOST + "\"}],\"games\":[]}").getBytes(StandardCharsets.UTF_8));
        fake.entries.addAll(Arrays.asList(entry("1", "One"), entry("2", "One")));fake.failure = "not_found";
        assertTrue(WolfCatalogSync.sync(fake, dir));JSONObject data = json(dir);
        assertEquals("My Wolf", data.getString("host_name"));assertEquals(1, data.getJSONArray("hosts").length());
        assertEquals(1, data.getJSONArray("games").length());assertFalse(data.getJSONArray("games").getJSONObject(0).has("image"));
        fake.failure = null;fake.image = new byte[]{1, 2, 3};assertFalse(WolfCatalogSync.sync(fake, dir));
        assertFalse(json(dir).getJSONArray("games").getJSONObject(0).has("image"));
    }
    @Test public void networkFailurePreservesCatalog() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake();
        fake.entries.add(entry("1", "One")); assertTrue(WolfCatalogSync.sync(fake, dir));
        File catalog = new File(dir, "catalog.json"); assertTrue(catalog.setLastModified(100000));
        byte[] before = Files.readAllBytes(catalog.toPath());
        fake.entries.add(entry("2", "Two")); fake.failure = "network_error";
        try { WolfCatalogSync.sync(fake, dir); fail(); } catch (java.io.IOException expected) { }
        assertArrayEquals(before, Files.readAllBytes(catalog.toPath())); assertEquals(100000, catalog.lastModified());
    }
    @Test public void deadlinePreservesCatalog() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake(); fake.entries.add(entry("1", "One"));
        try { WolfCatalogSync.sync(fake, dir, 9, () -> 10); fail(); } catch (java.io.IOException expected) { assertEquals("Sync deadline", expected.getMessage()); }
        assertFalse(new File(dir, "catalog.json").exists()); assertEquals(0, fake.downloads);
    }
    @Test public void truncatedDownloadHasNoImage() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake(); fake.entries.add(entry("1", "One"));
        fake.image = Arrays.copyOf(PNG, 8); assertTrue(WolfCatalogSync.sync(fake, dir));
        assertFalse(json(dir).getJSONArray("games").getJSONObject(0).has("image"));
    }
    @Test public void truncatedCacheIsDownloadedAgain() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake(); fake.entries.add(entry("1", "One"));
        File cover = new File(dir, "wolf-111111112222-1.png"); Files.write(cover.toPath(), Arrays.copyOf(PNG, 8));
        assertTrue(WolfCatalogSync.sync(fake, dir)); assertEquals(1, fake.downloads); assertArrayEquals(PNG, Files.readAllBytes(cover.toPath()));
    }
    @Test public void hostsUseDifferentCoverNames() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake(); fake.entries.add(entry("1", "One"));
        assertTrue(WolfCatalogSync.sync(fake, dir)); String first = json(dir).getJSONArray("games").getJSONObject(0).getString("image");
        fake.host = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
        assertTrue(WolfCatalogSync.sync(fake, dir)); String second = json(dir).getJSONArray("games").getJSONObject(0).getString("image");
        assertNotEquals(first, second); assertTrue(new File(dir, second).isFile()); assertEquals(2, fake.downloads);
    }
    private File seedCatalog(File dir, Fake fake, String name) throws Exception {
        fake.entries.add(entry("1", name));
        assertTrue(WolfCatalogSync.sync(fake, dir));
        File catalog = new File(dir, "catalog.json");
        assertTrue(catalog.setLastModified(100000));
        return catalog;
    }
    @Test public void lateCatalogResponsePreservesCatalog() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake();
        File catalog = seedCatalog(dir, fake, "One");
        byte[] before = Files.readAllBytes(catalog.toPath());
        fake.entries.set(0, entry("1", "Renamed")); // Catalog-only change: the cover is already cached.
        long[] now = {0};
        fake.onGames = () -> now[0] = 100;
        try { WolfCatalogSync.sync(fake, dir, 50, () -> now[0]); fail(); } catch (java.io.IOException expected) { assertEquals("Sync deadline", expected.getMessage()); }
        assertArrayEquals(before, Files.readAllBytes(catalog.toPath())); assertEquals(100000, catalog.lastModified());
    }
    @Test public void lateLastCoverPreservesCatalog() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake();
        File catalog = seedCatalog(dir, fake, "One");
        byte[] before = Files.readAllBytes(catalog.toPath());
        fake.entries.add(entry("2", "Two"));
        long[] now = {0};
        fake.onCover = () -> now[0] = 100;
        try { WolfCatalogSync.sync(fake, dir, 50, () -> now[0]); fail(); } catch (java.io.IOException expected) { assertEquals("Sync deadline", expected.getMessage()); }
        assertFalse(new File(dir, "wolf-111111112222-2.png").exists());
        assertArrayEquals(before, Files.readAllBytes(catalog.toPath())); assertEquals(100000, catalog.lastModified());
    }
    @Test public void lateNotFoundPreservesCatalog() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake();
        File catalog = seedCatalog(dir, fake, "One");
        byte[] before = Files.readAllBytes(catalog.toPath());
        fake.entries.add(entry("2", "Two")); fake.failure = "not_found";
        long[] now = {0};
        fake.onCover = () -> now[0] = 100;
        try { WolfCatalogSync.sync(fake, dir, 50, () -> now[0]); fail(); } catch (java.io.IOException expected) { assertEquals("Sync deadline", expected.getMessage()); }
        assertFalse(new File(dir, "wolf-111111112222-2.png").exists());
        assertArrayEquals(before, Files.readAllBytes(catalog.toPath())); assertEquals(100000, catalog.lastModified());
    }
    @Test public void interruptedSyncPreservesCatalog() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake();
        File catalog = seedCatalog(dir, fake, "One");
        byte[] before = Files.readAllBytes(catalog.toPath());
        fake.entries.add(entry("2", "Two"));
        Thread.currentThread().interrupt();
        try {
            try { WolfCatalogSync.sync(fake, dir); fail(); } catch (java.io.IOException expected) { assertEquals("Sync cancelled", expected.getMessage()); }
        } finally { Thread.interrupted(); }
        assertFalse(new File(dir, "wolf-111111112222-2.png").exists());
        assertArrayEquals(before, Files.readAllBytes(catalog.toPath())); assertEquals(100000, catalog.lastModified());
    }
    @Test public void interruptDuringCoverPreservesCatalog() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake();
        File catalog = seedCatalog(dir, fake, "One");
        byte[] before = Files.readAllBytes(catalog.toPath());
        fake.entries.add(entry("2", "Two"));
        fake.onCover = () -> Thread.currentThread().interrupt();
        try {
            try { WolfCatalogSync.sync(fake, dir); fail(); } catch (java.io.IOException expected) { assertEquals("Sync cancelled", expected.getMessage()); }
        } finally { Thread.interrupted(); }
        assertFalse(new File(dir, "wolf-111111112222-2.png").exists());
        assertArrayEquals(before, Files.readAllBytes(catalog.toPath())); assertEquals(100000, catalog.lastModified());
    }
    @Test public void repairedCoverReportsChangeWithoutRewritingCatalog() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake();
        File catalog = seedCatalog(dir, fake, "One");
        File cover = new File(dir, "wolf-111111112222-1.png");
        Files.write(cover.toPath(), new byte[0]);
        byte[] before = Files.readAllBytes(catalog.toPath());
        assertTrue(WolfCatalogSync.sync(fake, dir));
        assertTrue(cover.isFile()); assertArrayEquals(PNG, Files.readAllBytes(cover.toPath()));
        assertArrayEquals(before, Files.readAllBytes(catalog.toPath())); assertEquals(100000, catalog.lastModified());
    }
    @Test public void unchangedCacheStillReportsNoChange() throws Exception {
        File dir = folder.newFolder(); Fake fake = new Fake();
        File catalog = seedCatalog(dir, fake, "One");
        int downloads = fake.downloads;
        assertFalse(WolfCatalogSync.sync(fake, dir));
        assertEquals(downloads, fake.downloads);
        assertEquals(100000, catalog.lastModified());
    }

}
