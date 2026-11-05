package io.github.nickld.frigateglance;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Where the config lives on the TV. A config.json pushed over ADB lands in the app's external files folder
 * (or arrives through ConfigProvider); importPushed() validates it, keeps it in private storage and deletes
 * the pushed copy, so the passwords do not stay in a folder the shell can read. The settings screen's
 * choices are kept as overrides until the next pushed file replaces them.
 */
public final class ConfigStore {
    static final String TAG = "FrigateGlance";
    private static final String PRIVATE_NAME = "config.json";
    private static final String KEY_OVERRIDES = "overrides";
    private static final String KEY_PIN = "certSha256";

    private final Context context;
    private final SharedPreferences prefs;

    public ConfigStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** The config with the settings screen's overrides, or null when none has been imported yet. */
    public GlanceConfig load() {
        File f = new File(context.getFilesDir(), PRIVATE_NAME);
        if (!f.isFile()) return null;
        try {
            GlanceConfig base = GlanceConfig.parse(read(f));
            try {
                return base.withOverrides(new JSONObject(prefs.getString(KEY_OVERRIDES, "{}")));
            } catch (IllegalArgumentException | JSONException e) {
                Log.w(TAG, "settings ignored: " + e.getMessage());
                return base;
            }
        } catch (IOException | IllegalArgumentException e) {
            Log.w(TAG, "config unreadable: " + e.getMessage());
            return null;
        }
    }

    /** Imports a pushed config.json, if there is one. True when one was imported. */
    public boolean importPushed() {
        File dir = context.getExternalFilesDir(null);
        File pushed = dir == null ? null : new File(dir, PRIVATE_NAME);
        if (pushed == null || !pushed.isFile()) return false;
        try {
            return importJson(read(pushed));
        } catch (IOException e) {
            Log.w(TAG, "pushed config unreadable: " + e.getMessage());
            return false;
        } finally {
            if (!pushed.delete()) Log.w(TAG, "could not delete the pushed config");
        }
    }

    /** Validates and stores a config file's text; clears the settings overrides. */
    public boolean importJson(String json) {
        try {
            GlanceConfig.parse(json);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "pushed config rejected: " + e.getMessage());
            return false;
        }
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), PRIVATE_NAME))) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "could not store config: " + e.getMessage());
            return false;
        }
        prefs.edit().remove(KEY_OVERRIDES).remove(KEY_PIN).apply();
        Log.i(TAG, "config imported");
        return true;
    }

    /** Saves one setting from the settings screen. */
    public void saveSetting(String key, Object value) {
        try {
            JSONObject o = new JSONObject(prefs.getString(KEY_OVERRIDES, "{}"));
            o.put(key, value);
            prefs.edit().putString(KEY_OVERRIDES, o.toString()).apply();
        } catch (JSONException e) {
            Log.w(TAG, "setting not saved: " + e.getMessage());
        }
    }

    public String certPin() {
        return prefs.getString(KEY_PIN, "");
    }

    public void saveCertPin(String sha256) {
        prefs.edit().putString(KEY_PIN, sha256).apply();
    }

    private static String read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
