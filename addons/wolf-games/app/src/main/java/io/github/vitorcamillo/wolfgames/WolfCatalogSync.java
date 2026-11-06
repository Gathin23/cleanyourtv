package io.github.vitorcamillo.wolfgames;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

final class WolfCatalogSync {
    static final class Entry {
        final String appId, name, cover;
        Entry(String appId, String name, String cover) {
            this.appId = appId;
            this.name = name;
            this.cover = cover;
        }
    }
    interface Source {
        String hostUuid();
        List<Entry> games() throws WolfControlClient.ControlException;
        byte[] cover(String appId) throws WolfControlClient.ControlException, IOException;
    }

    static boolean sync(Source source, File directory) throws IOException, WolfControlClient.ControlException {
        return sync(source, directory, Long.MAX_VALUE, System::currentTimeMillis);
    }

    /** Returns true when something visible changed: the catalog or any cover file. */
    static boolean sync(Source source, File directory, long deadline, LongSupplier clock) throws IOException, WolfControlClient.ControlException {
        checkAlive(deadline, clock);
        List<Entry> entries = source.games();
        checkAlive(deadline, clock);
        File catalog = MoonlightCatalog.localFile(directory, "catalog.json");
        JSONObject old = null;
        if (catalog.isFile() && catalog.length() <= MoonlightCatalog.MAX_CATALOG_BYTES) {
            try (FileInputStream in = new FileInputStream(catalog); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > MoonlightCatalog.MAX_CATALOG_BYTES) throw new IOException("Catalog too large");
                    out.write(buffer, 0, count);
                }
                old = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            } catch (Exception ignored) { old = null; }
        }
        boolean coversWritten = false;
        try {
            String hostKey = UUID.fromString(source.hostUuid()).toString().replace("-", "").substring(0, 12);
            String hostName = old == null ? "Wolf" : old.optString("host_name", "Wolf");
            MoonlightCatalog.label(hostName);
            JSONArray hosts = old == null ? null : old.optJSONArray("hosts");
            JSONArray games = new JSONArray();
            Set<String> names = new HashSet<>();
            Set<String> referenced = new HashSet<>();
            List<MoonlightCatalog.Game> validated = new ArrayList<>();
            if (entries == null || entries.size() > MoonlightCatalog.MAX_GAMES) throw new IOException("Invalid game count");
            for (Entry entry : entries) {
                String name = MoonlightCatalog.label(entry.name);
                WolfSession.validateId(entry.appId);
                if (!names.add(name)) continue;
                String image = null;
                if (entry.cover != null) {
                    if (!entry.cover.equals(entry.appId + ".png") && !entry.cover.equals(entry.appId + ".jpg")) throw new IOException("Invalid cover");
                    String filename = "wolf-" + hostKey + "-" + entry.cover;
                    File file = MoonlightCatalog.localFile(directory, filename);
                    boolean png = entry.cover.endsWith(".png");
                    if (file.isFile() && !validImageFile(file, png) && !file.delete()) throw new IOException("Cannot remove invalid cover");
                    if (!file.isFile()) {
                        checkAlive(deadline, clock);
                        try {
                            byte[] bytes = source.cover(entry.appId);
                            checkAlive(deadline, clock);
                            if (validImage(bytes, png)) { writeAtomic(file, bytes); coversWritten = true; }
                            else file.delete();
                        } catch (WolfControlClient.ControlException error) {
                            if (!"not_found".equals(error.code)) throw new IOException("Cover download failed", error);
                            checkAlive(deadline, clock);
                        }
                    }
                    if (file.isFile() && validImageFile(file, png)) {
                        image = filename;
                        referenced.add(filename);
                    }
                }
                validated.add(new MoonlightCatalog.Game(name, entry.appId, image));
                JSONObject game = new JSONObject().put("name", name).put("app_id", entry.appId);
                if (image != null) game.put("image", image);
                games.put(game);
            }
            new MoonlightCatalog(hostName, source.hostUuid(), validated);
            JSONObject result = new JSONObject().put("schema_version", 1).put("host_name", hostName)
                    .put("host_uuid", source.hostUuid());
            if (hosts != null) result.put("hosts", hosts);
            result.put("games", games);
            checkAlive(deadline, clock);
            if (old != null && old.optInt("schema_version") == 1
                    && result.getString("host_uuid").equals(old.optString("host_uuid", ""))
                    && hostName.equals(old.optString("host_name", ""))
                    && same(hosts, old.optJSONArray("hosts")) && same(games, old.optJSONArray("games"))) return coversWritten;
            writeAtomic(catalog, result.toString().getBytes(StandardCharsets.UTF_8));
            File[] files = directory.listFiles();
            if (files != null) for (File file : files) {
                if (file.getName().matches("wolf-([0-9a-f]{12}-)?[0-9]+\\.(png|jpg)") && !referenced.contains(file.getName())) file.delete();
            }
            return true;
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Invalid catalog", error);
        }
    }
    /** Fails before any further write once the sync is cancelled or past its deadline. */
    private static void checkAlive(long deadline, LongSupplier clock) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Sync cancelled");
        if (clock.getAsLong() > deadline) throw new IOException("Sync deadline");
    }
    private static boolean same(JSONArray a, JSONArray b) {
        return a == null ? b == null : b != null && a.toString().equals(b.toString());
    }
    private static boolean validImageFile(File file, boolean png) throws IOException {
        if (file.length() > 4L * 1024 * 1024) return false;
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > 4 * 1024 * 1024) return false;
                out.write(buffer, 0, count);
            }
            return validImage(out.toByteArray(), png);
        }
    }
    private static boolean validImage(byte[] bytes, boolean png) {
        byte[] magic = png ? new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}
                : new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        byte[] end = png ? new byte[]{0x49, 0x45, 0x4e, 0x44, (byte) 0xae, 0x42, 0x60, (byte) 0x82}
                : new byte[]{(byte) 0xff, (byte) 0xd9};
        if (bytes == null || bytes.length < magic.length + end.length || bytes.length > 4 * 1024 * 1024) return false;
        for (int i = 0; i < magic.length; i++) if (bytes[i] != magic[i]) return false;
        for (int i = 0; i < end.length; i++) if (bytes[bytes.length - end.length + i] != end[i]) return false;
        return true;
    }
    private static void writeAtomic(File target, byte[] bytes) throws IOException {
        File temp = File.createTempFile(".wolf-", ".tmp", target.getParentFile());
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) { out.write(bytes); }
            if (!temp.renameTo(target)) throw new IOException("Cannot replace file");
        } finally { temp.delete(); }
    }
}
