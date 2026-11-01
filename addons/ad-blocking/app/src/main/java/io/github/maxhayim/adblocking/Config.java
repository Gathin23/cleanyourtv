package io.github.maxhayim.adblocking;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Which blocker, its address and login, in the app's own storage (backups are off). They arrive typed on the TV,
 * or as config.json pushed by install.sh, which is read once and deleted.
 */
final class Config {
    static final String PUSHED = "config.json";

    final Blocker.Kind kind;
    final String url;
    /** AdGuard Home only. */
    final String user;
    /** Pi-hole v6: the web or app password. Pi-hole v5: the API token. AdGuard Home: the password. */
    final String password;

    Config(Blocker.Kind kind, String url, String user, String password) {
        this.kind = kind;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** A Pi-hole may have no password at all; AdGuard Home always has a login. */
    boolean complete() {
        return !url.isEmpty() && (kind == Blocker.Kind.PIHOLE || !user.isEmpty());
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("server", Context.MODE_PRIVATE);
    }

    static Config load(Context c) {
        SharedPreferences p = prefs(c);
        Blocker.Kind k;
        try {
            k = Blocker.Kind.valueOf(p.getString("kind", "PIHOLE"));
        } catch (IllegalArgumentException e) {
            k = Blocker.Kind.PIHOLE;
        }
        return new Config(k, p.getString("url", ""), p.getString("user", ""), p.getString("password", ""));
    }

    static void save(Context c, String key, String value) {
        if ("url".equals(key)) value = Blocker.normalizeUrl(value);
        prefs(c).edit().putString(key, value == null ? "" : value.trim()).apply();
    }

    /** Takes a pushed config.json ({"kind": "pihole"|"adguard", "url", "user", "password"}), and deletes it. */
    static boolean importPushed(Context c) {
        File dir = c.getExternalFilesDir(null);
        File f = dir == null ? null : new File(dir, PUSHED);
        if (f == null || !f.isFile()) return false;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (o.has("kind")) save(c, "kind", "adguard".equalsIgnoreCase(o.optString("kind")) ? "ADGUARD" : "PIHOLE");
            for (String k : new String[]{"url", "user", "password"}) if (o.has(k)) save(c, k, o.optString(k));
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
