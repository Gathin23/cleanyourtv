package io.github.deepakola.familyrenewals;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class RenewalStore {
    static final String PREFS = "family_renewals_private";
    private static final String ENTRIES = "entries_json";
    private static final String COUNTDOWN = "home_countdown_enabled";

    private RenewalStore() {}

    static List<RenewalEntry> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(ENTRIES, "[]");
        List<RenewalEntry> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                RenewalEntry e = new RenewalEntry();
                e.id = text(o, "id");
                e.member = text(o, "member");
                e.category = text(o, "category");
                e.service = text(o, "service");
                e.reference = text(o, "reference");
                e.amount = text(o, "amount");
                e.lastPaid = text(o, "lastPaid");
                e.dueDate = text(o, "dueDate");
                e.notes = text(o, "notes");
                if (!e.id.isEmpty() && !e.member.isEmpty() && !e.service.isEmpty()
                        && DateRules.parse(e.dueDate) != null) {
                    result.add(e);
                }
            }
        } catch (JSONException | RuntimeException ignored) {
            // A damaged preference is treated as an empty list; the TV home remains unaffected.
        }
        return result;
    }

    private static String text(JSONObject o, String key) {
        String value = o.optString(key, "");
        return "null".equals(value) ? "" : value;
    }

    static boolean save(Context context, List<RenewalEntry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (RenewalEntry e : entries) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("member", e.member);
                o.put("category", e.category);
                o.put("service", e.service);
                o.put("reference", e.reference);
                o.put("amount", e.amount);
                o.put("lastPaid", e.lastPaid);
                o.put("dueDate", e.dueDate);
                o.put("notes", e.notes);
                array.put(o);
            }
        } catch (JSONException ex) {
            return false;
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(ENTRIES, array.toString()).commit();
    }

    static boolean countdownEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(COUNTDOWN, false);
    }

    static boolean setCountdownEnabled(Context context, boolean enabled) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(COUNTDOWN, enabled).commit();
    }
}
