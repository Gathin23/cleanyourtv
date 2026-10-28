package io.github.maxhayim.smarthome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
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
    /** At most this many fuel-price sensors in the glance header. */
    static final int MAX_PRICES = 6;

    enum Kind { WEATHER, PRICE, SCENE, TOGGLE, CLIMATE, SENSOR, OPENING, LOCK }

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
                case WEATHER:
                    return Double.isNaN(current) ? capital(state) : number(current) + "°";
                case PRICE:
                    try {
                        return number(Double.parseDouble(state)) + (unit.isEmpty() ? "" : " " + unit);
                    } catch (NumberFormatException e) {
                        return state;
                    }
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

    /**
     * What the glance header (and the optional Home line) shows: one weather entity and up to
     * {@link #MAX_PRICES} fuel prices. {@link #icon} is the closed vocabulary the launcher draws.
     */
    static final class Glance {
        final Entity weather;
        final List<Entity> prices;
        /** One line for Home, for example "21° · E10 1.69 €/L · Diesel 1.55 €/L". Empty when nothing to show. */
        final String text;
        /** clear, cloudy, rain, snow, storm, fog, night, or empty. */
        final String icon;

        Glance(Entity weather, List<Entity> prices) {
            this.weather = weather;
            this.prices = Collections.unmodifiableList(prices);
            this.icon = weather == null || !weather.available() ? "" : weatherIcon(weather.state);
            this.text = line(weather, prices);
        }

        boolean empty() {
            return text.isEmpty();
        }

        private static String line(Entity weather, List<Entity> prices) {
            StringBuilder b = new StringBuilder();
            if (weather != null && weather.available() && !Double.isNaN(weather.current)) {
                b.append(number(weather.current)).append('°');
            }
            for (Entity p : prices) {
                if (!p.available()) continue;
                if (b.length() > 0) b.append(" · ");
                b.append(shortName(p.name));
                try {
                    b.append(' ').append(number(Double.parseDouble(p.state)));
                } catch (NumberFormatException e) {
                    b.append(' ').append(p.state);
                }
                if (p.unit != null && !p.unit.isEmpty()) b.append(' ').append(p.unit);
            }
            return b.toString();
        }

        /** First word of the friendly name, capped so the Home line stays short. */
        private static String shortName(String name) {
            String n = name == null ? "" : name.trim();
            int space = n.indexOf(' ');
            if (space > 0) n = n.substring(0, space);
            return n.length() > 12 ? n.substring(0, 12) : n;
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

    /**
     * Weather and fuel prices for the glance header. With {@code ids} empty: the first available weather
     * entity and up to {@link #MAX_PRICES} price sensors. With ids set: those entities, in that order.
     */
    static Glance glance(List<Entity> all, List<String> ids) {
        if (ids != null && !ids.isEmpty()) {
            Entity weather = null;
            List<Entity> prices = new ArrayList<>();
            for (String id : ids) {
                Entity e = find(all, id);
                if (e == null) continue;
                if (e.kind == Kind.WEATHER && weather == null) weather = e;
                else if (e.kind == Kind.PRICE && prices.size() < MAX_PRICES) prices.add(e);
            }
            return new Glance(weather, prices);
        }
        Entity weather = null;
        List<Entity> prices = new ArrayList<>();
        for (Entity e : all) {
            if (e.kind == Kind.WEATHER && weather == null && e.available()) weather = e;
            else if (e.kind == Kind.PRICE && prices.size() < MAX_PRICES) prices.add(e);
        }
        if (weather == null) {
            for (Entity e : all) {
                if (e.kind == Kind.WEATHER) {
                    weather = e;
                    break;
                }
            }
        }
        return new Glance(weather, prices);
    }

    /** Rows for the remote list: weather and prices live in the glance header only. */
    static boolean inList(Entity e) {
        return e.kind != Kind.WEATHER && e.kind != Kind.PRICE;
    }

    /**
     * Closed vocabulary for the Home line and the in-app icon: clear, cloudy, rain, snow, storm, fog, night.
     * Unknown Home Assistant states map to cloudy.
     */
    static String weatherIcon(String state) {
        if (state == null) return "cloudy";
        switch (state) {
            case "sunny":
            case "clear":
                return "clear";
            case "clear-night":
                return "night";
            case "cloudy":
            case "partlycloudy":
            case "windy":
            case "windy-variant":
                return "cloudy";
            case "fog":
                return "fog";
            case "rainy":
            case "pouring":
            case "hail":
                return "rain";
            case "snowy":
            case "snowy-rainy":
                return "snow";
            case "lightning":
            case "lightning-rainy":
                return "storm";
            default:
                return "cloudy";
        }
    }

    static boolean priceUnit(String unit) {
        if (unit == null) return false;
        String u = unit.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        return u.equals("€") || u.equals("EUR") || u.equals("€/L") || u.equals("EUR/L")
                || u.equals("€/LITRE") || u.equals("EUR/LITRE");
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
            case "weather":
                return new Entity(id, Kind.WEATHER, name, state, -1, a.optDouble("temperature", Double.NaN),
                        Double.NaN, "°");
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
                if (priceUnit(unit)) {
                    return new Entity(id, Kind.PRICE, name, state, -1, Double.NaN, Double.NaN, unit);
                }
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
        if (e.kind == Kind.CLIMATE && !Double.isNaN(e.target)) {
            return call("climate", "set_temperature", e.id, "\"temperature\":" + number(e.target + direction * 0.5));
        }
        return null;
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

    private static Entity find(List<Entity> all, String id) {
        for (Entity e : all) if (e.id.equals(id)) return e;
        return null;
    }
}
