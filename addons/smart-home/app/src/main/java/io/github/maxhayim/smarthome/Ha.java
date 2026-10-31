package io.github.maxhayim.smarthome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Home Assistant's REST API (https://developers.home-assistant.io/docs/api/rest): what GET /api/states returns,
 * turned into the rows this app shows, and the service calls its buttons make. No Android types and no network,
 * so the JVM tests cover it against the documented responses.
 */
final class Ha {
    /** At most this many rows, so a large installation can't make the screen slow. */
    static final int MAX_ENTITIES = 80;

    enum Kind { SCENE, TOGGLE, CLIMATE, SENSOR, OPENING, LOCK }

    static final class Entity {
        final String id;
        final Kind kind;
        final String name;
        final String state;
        /** Lights: 0 to 100, or -1. */
        final int brightness;
        final double current;
        final double target;
        final String unit;

        Entity(String id, Kind kind, String name, String state, int brightness, double current, double target, String unit) {
            this.id = id;
            this.kind = kind;
            this.name = name;
            this.state = state;
            this.brightness = brightness;
            this.current = current;
            this.target = target;
            this.unit = unit;
        }

        String domain() {
            int dot = id.indexOf('.');
            return dot < 0 ? "" : id.substring(0, dot);
        }

        boolean available() {
            return !"unavailable".equals(state) && !"unknown".equals(state);
        }

        /** What the row shows on the right. */
        String value() {
            if (!available()) return "Unavailable";
            switch (kind) {
                case SCENE:
                    return "Run";
                case TOGGLE:
                    boolean on = "on".equals(state);
                    return on && brightness >= 0 ? "On · " + brightness + " %" : on ? "On" : "Off";
                case CLIMATE:
                    String now = Double.isNaN(current) ? "" : number(current) + unit;
                    String set = Double.isNaN(target) ? "" : number(target) + unit;
                    return now.isEmpty() ? set : set.isEmpty() ? now : now + " → " + set;
                case SENSOR:
                    try {
                        return number(Double.parseDouble(state)) + (unit.isEmpty() ? "" : " " + unit).replace(" °", "°");
                    } catch (NumberFormatException e) {
                        return state;
                    }
                case OPENING:
                    return "on".equals(state) ? "Open" : "Closed";
                case LOCK:
                    return "locked".equals(state) ? "Locked" : "unlocked".equals(state) ? "Unlocked" : capital(state);
                default:
                    return state;
            }
        }
    }

    /** One service call: POST path with body. */
    static final class Call {
        final String path;
        final String body;

        Call(String path, String body) {
            this.path = path;
            this.body = body;
        }
    }

    private Ha() {
    }

    /** The rows, sorted into sections (scenes, lights and switches, climate, sensors, doors and windows, locks). */
    static List<Entity> parse(String json) throws JSONException {
        JSONArray all = new JSONArray(json);
        List<Entity> out = new ArrayList<>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o == null) continue;
            Entity e = entity(o);
            if (e != null) out.add(e);
        }
        out.sort((a, b) -> a.kind != b.kind ? a.kind.compareTo(b.kind) : a.name.compareToIgnoreCase(b.name));
        return out.size() > MAX_ENTITIES ? new ArrayList<>(out.subList(0, MAX_ENTITIES)) : out;
    }

    static Entity entity(JSONObject o) {
        String id = o.optString("entity_id", "");
        String state = o.optString("state", "");
        JSONObject a = o.optJSONObject("attributes");
        if (a == null) a = new JSONObject();
        int dot = id.indexOf('.');
        if (dot <= 0) return null;
        String domain = id.substring(0, dot);
        String name = a.optString("friendly_name", id.substring(dot + 1).replace('_', ' '));
        String deviceClass = a.optString("device_class", "");
        String unit = a.optString("unit_of_measurement", "");
        switch (domain) {
            case "scene":
                return new Entity(id, Kind.SCENE, name, state, -1, Double.NaN, Double.NaN, "");
            case "light":
                int b = a.has("brightness") && !a.isNull("brightness") ? (int) Math.round(a.optDouble("brightness", 0) * 100 / 255) : -1;
                return new Entity(id, Kind.TOGGLE, name, state, b, Double.NaN, Double.NaN, "");
            case "switch":
            case "fan":
            case "input_boolean":
                return new Entity(id, Kind.TOGGLE, name, state, -1, Double.NaN, Double.NaN, "");
            case "climate":
                return new Entity(id, Kind.CLIMATE, name, state, -1, a.optDouble("current_temperature", Double.NaN),
                        a.optDouble("temperature", Double.NaN), "°");
            case "sensor":
                if (!deviceClass.equals("temperature") && !deviceClass.equals("humidity")) return null;
                return new Entity(id, Kind.SENSOR, name, state, -1, Double.NaN, Double.NaN, unit);
            case "binary_sensor":
                if (!deviceClass.equals("door") && !deviceClass.equals("window") && !deviceClass.equals("garage_door")
                        && !deviceClass.equals("opening")) return null;
                return new Entity(id, Kind.OPENING, name, state, -1, Double.NaN, Double.NaN, "");
            case "lock":
                return new Entity(id, Kind.LOCK, name, state, -1, Double.NaN, Double.NaN, "");
            default:
                return null;
        }
    }

    /** OK on a row; null when the row does nothing (sensors, and locks: a TV remote never unlocks a door). */
    static Call press(Entity e) {
        if (!e.available()) return null;
        switch (e.kind) {
            case SCENE:
                return call("scene", "turn_on", e.id, null);
            case TOGGLE:
                return call("homeassistant", "toggle", e.id, null);
            default:
                return null;
        }
    }

    /** Left (-1) or Right (+1) on a row: dims a light by 10 %, or moves a thermostat by half a degree. */
    static Call step(Entity e, int direction) {
        if (!e.available()) return null;
        if (e.kind == Kind.TOGGLE && e.domain().equals("light")) {
            return call("light", "turn_on", e.id, "\"brightness_step_pct\":" + (direction * 10));
        }
        if (e.kind == Kind.CLIMATE && !Double.isNaN(e.target)) return temperature(e, e.target + direction * 0.5);
        return null;
    }

    /** A thermostat's new target, in degrees. */
    static Call temperature(Entity e, double target) {
        return call("climate", "set_temperature", e.id, "\"temperature\":" + number(target));
    }

    private static Call call(String domain, String service, String entity, String extra) {
        String body = "{\"entity_id\":" + JSONObject.quote(entity) + (extra == null ? "" : "," + extra) + "}";
        return new Call("/api/services/" + domain + "/" + service, body);
    }

    static String section(Kind k) {
        switch (k) {
            case SCENE: return "Scenes";
            case TOGGLE: return "Lights and switches";
            case CLIMATE: return "Climate";
            case SENSOR: return "Sensors";
            case OPENING: return "Doors and windows";
            default: return "Locks";
        }
    }

    /** "http://192.168.1.10:8123" from what was typed: a scheme added when missing, trailing slashes dropped. */
    static String normalizeUrl(String typed) {
        if (typed == null) return "";
        String u = typed.trim();
        if (u.isEmpty()) return "";
        if (!u.contains("://")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    static boolean validUrl(String url) {
        return url.matches("https?://[^\\s/?#]+(:[0-9]{1,5})?(/[^\\s?#]*)?");
    }

    /** What a failed request means for the person in front of the TV. */
    static String problem(int code) {
        if (code == 401 || code == 403) return "Home Assistant refused the access token.";
        if (code == 404) return "That address answers, but it isn't Home Assistant's API.";
        if (code <= 0) return "Can't reach Home Assistant at that address.";
        return "Home Assistant answered with error " + code + ".";
    }

    static String number(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
