package io.github.justinnewman.gameday;

import org.json.JSONException;
import org.json.JSONObject;

/** How a calendar's last fetches went, kept beside its cached copy. */
final class FetchStatus {
    final long lastSuccessMs;
    final long lastFailureMs;
    /** "Not a calendar link", "Link refused (403)", "No answer", "Over 5 MB"; null after a success. */
    final String error;
    /** Events in the last good copy. */
    final int entries;

    FetchStatus(long lastSuccessMs, long lastFailureMs, String error, int entries) {
        this.lastSuccessMs = lastSuccessMs;
        this.lastFailureMs = lastFailureMs;
        this.error = error;
        this.entries = entries;
    }

    String toJson() {
        try {
            JSONObject o = new JSONObject().put("success", lastSuccessMs).put("failure", lastFailureMs).put("entries", entries);
            if (error != null) o.put("error", error);
            return o.toString();
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static FetchStatus fromJson(String json) {
        try {
            JSONObject o = new JSONObject(json);
            return new FetchStatus(o.optLong("success"), o.optLong("failure"),
                    o.has("error") ? o.optString("error") : null, o.optInt("entries"));
        } catch (JSONException unreadable) {
            return null;
        }
    }
}
