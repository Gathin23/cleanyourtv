package io.github.maxhayim.smarthome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The server address and token, in the app's own storage (backups are off). They arrive typed on the TV, or as
 * config.json pushed by install.sh, which is read once and deleted.
 */
final class Config {
    static final String PUSHED = "config.json";

    final String url;
    final String token;

    Config(String url, String token) {
        this.url = url;
        this.token = token;
    }

    boolean complete() {
        return !url.isEmpty() && !token.isEmpty();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("server", Context.MODE_PRIVATE);
    }

    static Config load(Context c) {
        SharedPreferences p = prefs(c);
        return new Config(p.getString("url", ""), p.getString("token", ""));
    }

    static void saveUrl(Context c, String url) {
        prefs(c).edit().putString("url", Ha.normalizeUrl(url)).apply();
    }

    static void saveToken(Context c, String token) {
        prefs(c).edit().putString("token", token == null ? "" : token.trim()).apply();
    }

    /** Takes a pushed config.json ({"url": ..., "token": ...}) if there is one, and deletes it. True when taken. */
    static boolean importPushed(Context c) {
        File dir = c.getExternalFilesDir(null);
        File f = dir == null ? null : new File(dir, PUSHED);
        if (f == null || !f.isFile()) return false;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (o.has("url")) saveUrl(c, o.optString("url"));
            if (o.has("token")) saveToken(c, o.optString("token"));
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
