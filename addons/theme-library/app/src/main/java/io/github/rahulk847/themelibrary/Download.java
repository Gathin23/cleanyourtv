package io.github.rahulk847.themelibrary;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads from the CleanYourTV repository on GitHub, under the rules in {@link Store}: the list of theme folders
 * (GitHub's contents API), the README with their descriptions, and one theme's files. Runs on a worker thread.
 */
final class Download {
    private static final String AGENT = "ThemeLibrary-AndroidTV/0.2";
    private static final int LIST_LIMIT = 1024 * 1024;

    private Download() {
    }

    /** The theme folder names in the repository, in GitHub's (alphabetical) order. */
    static List<String> folders() throws IOException {
        List<String> out = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(text(Store.API + "?ref=" + Store.BRANCH, LIST_LIMIT));
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                String name = o.optString("name");
                if ("dir".equals(o.optString("type")) && Store.safeName(name)) out.add(name);
            }
        } catch (JSONException e) {
            throw new IOException("GitHub sent a list this app cannot read");
        }
        return out;
    }

    /** themes/README.md, for the descriptions; null when it cannot be had (the list still works without it). */
    static String readme() {
        try {
            return text(Store.README, LIST_LIMIT);
        } catch (IOException e) {
            return null;
        }
    }

    /** Downloads the theme in repo folder {@code folder} into {@code into}, emptied first. */
    static void theme(String folder, File into) throws IOException {
        if (!Store.safeName(folder)) throw new IOException("unexpected folder name");
        ThemeFiles.deleteTree(into);
        if (!into.mkdirs()) throw new IOException("cannot create " + into);
        files(folder, false, into, 0);
        if (!ThemeFiles.hasTheme(into)) throw new IOException(folder + " has no " + ThemeFiles.CONFIG);
    }

    private static long files(String folder, boolean wallpapers, File into, long total) throws IOException {
        JSONArray items;
        try {
            items = new JSONArray(text(Store.folderUrl(folder, wallpapers), LIST_LIMIT));
        } catch (JSONException e) {
            throw new IOException("GitHub sent a list this app cannot read");
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("name");
            String type = o.optString("type");
            if (!wallpapers && "dir".equals(type) && ThemeFiles.WALLPAPERS.equals(name)) {
                total = files(folder, true, into, total);
                continue;
            }
            if (!"file".equals(type)) continue;
            String rel = Store.keep(name, wallpapers);
            if (rel == null) continue;
            long size = o.optLong("size", -1);
            if (size < 0 || size > Store.MAX_FILE) throw new IOException(name + " is larger than this app accepts");
            total += size;
            if (total > Store.MAX_THEME) throw new IOException("the theme is larger than this app accepts");
            String url = o.optString("download_url", null);
            if (!Store.trustedDownload(url, folder)) throw new IOException(name + " does not come from " + Store.OWNER_REPO);
            File target = new File(into, rel);
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent);
            try (OutputStream out = new FileOutputStream(target)) {
                fetch(url, Store.MAX_FILE, out, false);
            }
            if (target.length() != size) throw new IOException(name + " arrived incomplete");
        }
        return total;
    }

    private static String text(String url, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        fetch(url, limit, out, url.startsWith("https://api.github.com/"));
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void fetch(String url, long limit, OutputStream out, boolean api) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", AGENT);
            if (api) c.setRequestProperty("Accept", "application/vnd.github+json");
            int code = c.getResponseCode();
            if (code == 403 || code == 429) {
                throw new IOException("GitHub's hourly limit for this network is used up; try again in an hour");
            }
            if (code != 200) throw new IOException("GitHub answered " + code);
            long n = 0;
            byte[] buf = new byte[64 * 1024];
            try (InputStream in = c.getInputStream()) {
                for (int r; (r = in.read(buf)) > 0; ) {
                    n += r;
                    if (n > limit) throw new IOException("a download was larger than expected");
                    out.write(buf, 0, r);
                }
            }
        } finally {
            c.disconnect();
        }
    }
}
