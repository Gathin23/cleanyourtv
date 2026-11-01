package io.github.nickld.frigateglance;

import java.util.List;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

/** One message from frigate/reviews: a review item starting (new), changing (update) or ending (end). */
public final class Review {
    private static final Set<String> TYPES = Set.of("new", "update", "end");
    private static final String VERIFIED = "-verified";

    public final String type, id, camera, severity;
    public final List<String> objects, zones, detectionIds;
    /** Names Frigate gave objects (a face, a known plate), from data.sub_labels. */
    public final List<String> subLabels;

    private Review(String type, JSONObject after) {
        this.type = type;
        id = after.optString("id", "");
        camera = after.optString("camera", "");
        severity = after.optString("severity", "");
        JSONObject data = after.optJSONObject("data");
        objects = labels(Json.strings(data == null ? null : data.optJSONArray("objects")));
        subLabels = Json.strings(data == null ? null : data.optJSONArray("sub_labels"));
        zones = Json.strings(data == null ? null : data.optJSONArray("zones"));
        detectionIds = Json.strings(data == null ? null : data.optJSONArray("detections"));
    }

    private Review(String type, String id, String camera, List<String> objects, List<String> zones, String eventId) {
        this.type = type;
        this.id = id;
        this.camera = camera;
        this.severity = "alert";
        this.objects = objects;
        this.zones = zones;
        this.detectionIds = List.of(eventId);
        this.subLabels = List.of();
    }

    /**
     * An object Frigate named (sub_labels) is listed as "<label>-verified": it counts, and reads, as its label.
     */
    private static List<String> labels(List<String> objects) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String o : objects) out.add(o.endsWith(VERIFIED) ? o.substring(0, o.length() - VERIFIED.length()) : o);
        return List.copyOf(out);
    }

    /**
     * Frigate's debug replay ("_replay_<camera>") publishes tracked-object events but no review items, so for
     * those cameras each event stands in for an alert of its own: new opens, update refreshes, end counts down.
     * Null for any other camera, whose cards come from real reviews only.
     */
    public static Review fromReplayEvent(String payload) {
        try {
            JSONObject o = new JSONObject(payload);
            String type = o.optString("type", "");
            JSONObject a = o.optJSONObject("after");
            if (!TYPES.contains(type) || a == null) return null;
            String camera = a.optString("camera", "");
            String id = a.optString("id", "");
            String label = a.optString("label", "");
            if (!camera.startsWith(ReviewFilter.REPLAY_PREFIX) || id.isEmpty() || label.isEmpty()) return null;
            return new Review(type, "replay-" + id, camera, List.of(label), Json.strings(a.optJSONArray("entered_zones")), id);
        } catch (JSONException e) {
            return null;
        }
    }

    /** The parsed message, or null when it is not a review message this add-on understands. */
    public static Review parse(String payload) {
        try {
            JSONObject o = new JSONObject(payload);
            String type = o.optString("type", "");
            JSONObject after = o.optJSONObject("after");
            if (!TYPES.contains(type) || after == null) return null;
            Review r = new Review(type, after);
            return r.id.isEmpty() || r.camera.isEmpty() || r.severity.isEmpty() ? null : r;
        } catch (JSONException e) {
            return null;
        }
    }
}
