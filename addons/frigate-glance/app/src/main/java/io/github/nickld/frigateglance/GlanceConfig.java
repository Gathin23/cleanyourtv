package io.github.nickld.frigateglance;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The add-on's settings: the pushed config.json (keys as in Peek's config.json where they mean the same
 * thing), with the settings screen's choices laid over it. Immutable; parse() validates and clamps.
 */
public final class GlanceConfig {
    static final Set<String> CORNERS = Set.of("top-right", "top-left", "bottom-right", "bottom-left");
    static final Set<String> SEVERITIES = Set.of("alerts", "alerts_and_detections");
    /** Keys people press out of habit to control playback; never caught. */
    static final Set<String> FORBIDDEN_KEYS = Set.of("KEYCODE_BACK", "KEYCODE_DPAD_CENTER", "KEYCODE_ENTER", "KEYCODE_HOME");

    public final String mqttUrl, frigateUrl, frigateUser, frigatePassword, topicPrefix, rtspUser, rtspPassword;
    public final String corner, severity, dismissKey, snoozeKey;
    /** Camera name to display name; empty means every camera. */
    public final Map<String, String> cameras;
    /** Camera name to the go2rtc stream for live video, when it should not be Frigate's live stream. */
    public final Map<String, String> streams;
    /** Cameras switched off on the settings screen. */
    public final Set<String> camerasOff;
    /** Cameras Frigate gains later (not in "cameras") open cards too. Only matters while "cameras" lists some. */
    public final boolean newCameras;
    /** Object labels that may open a card; empty means every label. */
    public final Set<String> labels;
    public final int dismissSeconds, snoozeMinutes;
    /** Card size in percent of the standard card (picture, text and bar together). */
    public final int cardScale;
    public final boolean liveVideo, boxes;
    /** A third card key opens the camera full screen; off by default. */
    public final boolean fullscreen;
    public final String fullscreenKey;
    /** Keys that act only when held (KeyRules.HOLD_MS), against accidental presses while moving around an app. */
    public final boolean dismissHold, snoozeHold, fullscreenHold;

    private final JSONObject source;

    private GlanceConfig(JSONObject o) {
        source = o;
        mqttUrl = mqtt(required(o, "mqtt"));
        frigateUrl = stripSlash(required(o, "frigateUrl"));
        frigateUser = o.optString("frigateUser", "");
        frigatePassword = o.optString("frigatePassword", "");
        topicPrefix = o.optString("topicPrefix", "frigate").isEmpty() ? "frigate" : o.optString("topicPrefix", "frigate");
        rtspUser = o.optString("rtspUser", "");
        rtspPassword = o.optString("rtspPassword", "");
        corner = oneOf(o.optString("corner", "top-right"), CORNERS, "top-right");
        severity = oneOf(o.optString("severity", "alerts"), SEVERITIES, "alerts");
        dismissSeconds = clamp(o.optInt("dismissSeconds", 8), 1, 120);
        snoozeMinutes = clamp(o.optInt("snoozeMinutes", 60), 5, 720);
        cardScale = clamp(o.optInt("cardScale", 100), 50, 200);
        liveVideo = o.optBoolean("liveVideo", true);
        boxes = o.optBoolean("boxes", true);
        dismissKey = key(o, "dismissKey", "KEYCODE_DPAD_LEFT");
        snoozeKey = key(o, "snoozeKey", "KEYCODE_DPAD_RIGHT");
        if (dismissKey.equals(snoozeKey)) throw new IllegalArgumentException("dismissKey and snoozeKey are the same key");
        fullscreen = o.optBoolean("fullscreen", false);
        dismissHold = o.optBoolean("dismissHold", false);
        snoozeHold = o.optBoolean("snoozeHold", false);
        fullscreenHold = o.optBoolean("fullscreenHold", false);
        fullscreenKey = key(o, "fullscreenKey", "KEYCODE_DPAD_UP");
        if (fullscreen && (fullscreenKey.equals(dismissKey) || fullscreenKey.equals(snoozeKey))) {
            throw new IllegalArgumentException("fullscreenKey is the same key as dismissKey or snoozeKey");
        }
        cameras = cameras(o.optJSONObject("cameras"));
        streams = cameras(o.optJSONObject("streams"));
        camerasOff = labels(o.optJSONArray("camerasOff"));
        newCameras = o.optBoolean("newCameras", false);
        labels = labels(o.optJSONArray("labels"));
    }

    /** Parses and validates a config file; IllegalArgumentException names what is wrong. */
    public static GlanceConfig parse(String json) {
        try {
            return new GlanceConfig(new JSONObject(json));
        } catch (JSONException e) {
            // Android's org.json repeats the whole input in its messages: passwords included. Say only where.
            throw new IllegalArgumentException("config is not valid JSON");
        }
    }

    /** This config with the settings screen's values laid over it; validated the same way. */
    public GlanceConfig withOverrides(JSONObject settings) {
        try {
            JSONObject merged = new JSONObject(source.toString());
            for (Iterator<String> it = settings.keys(); it.hasNext(); ) {
                String k = it.next();
                merged.put(k, settings.get(k));
            }
            return new GlanceConfig(merged);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /** Every key, normalised, so a re-parse gives the same config. */
    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("mqtt", mqttUrl);
            o.put("frigateUrl", frigateUrl);
            o.put("frigateUser", frigateUser);
            o.put("frigatePassword", frigatePassword);
            o.put("topicPrefix", topicPrefix);
            o.put("rtspUser", rtspUser);
            o.put("rtspPassword", rtspPassword);
            o.put("cameras", new JSONObject(cameras));
            o.put("streams", new JSONObject(streams));
            o.put("camerasOff", new JSONArray(camerasOff));
            o.put("newCameras", newCameras);
            o.put("labels", new JSONArray(labels));
            o.put("severity", severity);
            o.put("corner", corner);
            o.put("dismissSeconds", dismissSeconds);
            o.put("snoozeMinutes", snoozeMinutes);
            o.put("cardScale", cardScale);
            o.put("liveVideo", liveVideo);
            o.put("boxes", boxes);
            o.put("dismissKey", dismissKey);
            o.put("snoozeKey", snoozeKey);
            o.put("fullscreen", fullscreen);
            o.put("fullscreenKey", fullscreenKey);
            o.put("dismissHold", dismissHold);
            o.put("snoozeHold", snoozeHold);
            o.put("fullscreenHold", fullscreenHold);
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The host part of frigateUrl, for the RTSP address. */
    public String frigateHost() {
        String s = frigateUrl.replaceFirst("^[a-zA-Z]+://", "");
        int end = s.length();
        for (char c : new char[] {':', '/'}) {
            int i = s.indexOf(c);
            if (i >= 0 && i < end) end = i;
        }
        return s.substring(0, end);
    }

    /** mqtt://user:password@host:port with a host; the message never repeats the value (it holds a password). */
    private static String mqtt(String url) {
        try {
            java.net.URI u = new java.net.URI(url);
            if ("mqtt".equalsIgnoreCase(u.getScheme()) && u.getHost() != null) return url;
        } catch (java.net.URISyntaxException e) {
            // reported below
        }
        throw new IllegalArgumentException("\"mqtt\" must look like mqtt://user:password@host:1883, with characters"
                + " other than letters and digits in the user or password percent-encoded");
    }

    private static String required(JSONObject o, String k) {
        String v = o.optString(k, "").trim();
        if (v.isEmpty()) throw new IllegalArgumentException("config needs \"" + k + "\"");
        return v;
    }

    private static String key(JSONObject o, String k, String fallback) {
        String v = o.optString(k, fallback).trim();
        if (v.isEmpty()) v = fallback;
        if (!v.startsWith("KEYCODE_")) throw new IllegalArgumentException(k + " must be a KEYCODE_ name");
        if (FORBIDDEN_KEYS.contains(v)) throw new IllegalArgumentException(k + " may not be " + v);
        return v;
    }

    private static String oneOf(String v, Set<String> allowed, String fallback) {
        return allowed.contains(v) ? v : fallback;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static Map<String, String> cameras(JSONObject o) {
        Map<String, String> m = new LinkedHashMap<>();
        if (o != null) {
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                m.put(k, o.optString(k, k));
            }
        }
        return Collections.unmodifiableMap(m);
    }

    private static Set<String> labels(JSONArray a) {
        Set<String> s = new LinkedHashSet<>();
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                String v = a.optString(i, "").trim();
                if (!v.isEmpty()) s.add(v);
            }
        }
        return Collections.unmodifiableSet(s);
    }
}
