package com.mauro.tv.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.zip.Inflater;

/**
 * Receives what the PC assistant sends and keeps the last of each for display: the report ("b64", base64 JSON, under
 * 16 KB) and the process table ("z64", base64 of zlib-compressed JSON, under 100 KB packed and 400 KB unpacked).
 * It only stores text that parses as JSON; nothing in it is ever executed.
 *
 * A finished exchange (state "result") is also added to the history, newest first, so the question and the answer
 * can always be read again. If the add-on turned USB debugging on for that question, it turns it off here.
 *
 * The manifest guards it with android.permission.DUMP: the ADB shell holds that permission, an installed app
 * cannot be granted it, so no app on the TV can forge a report or a question.
 */
public final class ReportReceiver extends BroadcastReceiver {
    static final String HISTORY = "history";
    static final String CLOSE_ADB = "close_adb_after_answer";
    private static final int KEEP = 30;

    @Override public void onReceive(Context context, Intent intent) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("journal", Context.MODE_PRIVATE);
            String b64 = intent.getStringExtra("b64");
            if (b64 != null && b64.length() <= 16000) {
                String json = new String(Base64.decode(b64, Base64.DEFAULT), "UTF-8");
                JSONObject report = new JSONObject(json);
                prefs.edit().putString("ai_report", json).apply();
                if (report.optString("state").equals("result")) {
                    remember(prefs, report);
                    if (prefs.getBoolean(CLOSE_ADB, false)) {
                        prefs.edit().putBoolean(CLOSE_ADB, false).apply();
                        AdbOffReceiver.set(context, false);
                    }
                }
            }
            String z64 = intent.getStringExtra("z64");
            if (z64 != null && z64.length() <= 100000) {
                Inflater inflater = new Inflater();
                inflater.setInput(Base64.decode(z64, Base64.DEFAULT));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                while (!inflater.finished() && out.size() <= 400000) {
                    int n = inflater.inflate(buffer);
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                    out.write(buffer, 0, n);
                }
                inflater.end();
                String json = out.toString("UTF-8");
                new JSONObject(json);
                prefs.edit().putString("details", json).apply();
            }
        } catch (Exception ignored) { }
    }

    /** Puts the report first in the history, replacing one with the same id, and keeps the newest KEEP. */
    private static void remember(SharedPreferences prefs, JSONObject report) throws Exception {
        JSONArray old = new JSONArray(prefs.getString(HISTORY, "[]"));
        JSONArray fresh = new JSONArray().put(report);
        for (int i = 0; i < old.length() && fresh.length() < KEEP; i++) {
            JSONObject o = old.optJSONObject(i);
            if (o != null && !o.optString("id").equals(report.optString("id"))) fresh.put(o);
        }
        prefs.edit().putString(HISTORY, fresh.toString()).apply();
    }
}
