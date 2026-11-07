package io.github.maxhayim.smarthome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The server address, token and optional glance entity ids, in the app's own storage (backups are off). They
 * arrive typed on the TV, or as config.json pushed by install.sh, which is read once and deleted.
 */
final class Config {
    static final String PUSHED = "config.json";

    final String url;
    final String token;
    /** Entity ids for the glance header, in order; empty means auto-pick weather and fuel prices. */
    final List<String> glance;
    /** List rows to show, in this order within each section; empty means every supported entity. */
    final List<String> show;
    /** List rows to leave out: entity ids, or a prefix ending in '*' such as "scene.office_*". */
    final List<String> hide;
    /** Leave out rows Home Assistant reports as unavailable (and unknown, except scenes). */
    final boolean hideUnavailable;

    Config(String url, String token, List<String> glance) {
        this(url, token, glance, null, null, false);
    }

    Config(String url, String token, List<String> glance, List<String> show, List<String> hide, boolean hideUnavailable) {
        this.url = url;
        this.token = token;
        this.glance = Collections.unmodifiableList(glance == null ? Collections.emptyList() : glance);
        this.show = Collections.unmodifiableList(show == null ? Collections.emptyList() : show);
        this.hide = Collections.unmodifiableList(hide == null ? Collections.emptyList() : hide);
        this.hideUnavailable = hideUnavailable;
    }

    boolean complete() {
        return !url.isEmpty() && !token.isEmpty();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("server", Context.MODE_PRIVATE);
    }

    static Config load(Context c) {
        SharedPreferences p = prefs(c);
        return new Config(p.getString("url", ""), p.getString("token", ""), readGlance(p.getString("glance", "")),
                readGlance(p.getString("show", "")), readGlance(p.getString("hide", "")),
                p.getBoolean("hide_unavailable", false));
    }

    private static void saveList(Context c, String key, List<String> ids) {
        prefs(c).edit().putString(key, joinGlance(ids)).apply();
    }

    static void saveUrl(Context c, String url) {
        prefs(c).edit().putString("url", Ha.normalizeUrl(url)).apply();
    }

    static void saveToken(Context c, String token) {
        prefs(c).edit().putString("token", token == null ? "" : token.trim()).apply();
    }

    static void saveGlance(Context c, List<String> ids) {
        prefs(c).edit().putString("glance", joinGlance(ids)).apply();
    }

    /**
     * Takes a pushed config.json ({"url": ..., "token": ..., "glance": [...], "show": [...], "hide": [...],
     * "hide_unavailable": true}) if there is one, and deletes it. Only the keys present change.
     */
    static boolean importPushed(Context c) {
        File dir = c.getExternalFilesDir(null);
        File f = dir == null ? null : new File(dir, PUSHED);
        if (f == null || !f.isFile()) return false;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (o.has("url")) saveUrl(c, o.optString("url"));
            if (o.has("token")) saveToken(c, o.optString("token"));
            if (o.has("glance")) saveGlance(c, parseGlance(o.opt("glance")));
            if (o.has("show")) saveList(c, "show", parseGlance(o.opt("show")));
            if (o.has("hide")) saveList(c, "hide", parseGlance(o.opt("hide")));
            if (o.has("hide_unavailable")) {
                prefs(c).edit().putBoolean("hide_unavailable", o.optBoolean("hide_unavailable", false)).apply();
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    static List<String> parseGlance(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof JSONArray) {
            JSONArray a = (JSONArray) raw;
            for (int i = 0; i < a.length(); i++) {
                String id = a.optString(i, "").trim();
                if (!id.isEmpty() && !out.contains(id)) out.add(id);
            }
        } else if (raw instanceof String) {
            out.addAll(readGlance((String) raw));
        }
        return out;
    }

    private static List<String> readGlance(String joined) {
        List<String> out = new ArrayList<>();
        if (joined == null || joined.isEmpty()) return out;
        for (String part : joined.split(",")) {
            String id = part.trim();
            if (!id.isEmpty() && !out.contains(id)) out.add(id);
        }
        return out;
    }

    private static String joinGlance(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String id : ids) {
            if (id == null || id.trim().isEmpty()) continue;
            if (b.length() > 0) b.append(',');
            b.append(id.trim());
        }
        return b.toString();
    }
}
