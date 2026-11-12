package com.gathin.homedashboard;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** HTTPS only. Never follows a redirect with an HA token or Frigate cookie. */
final class Api {
    private final Config config;
    private String cookie;
    private final java.util.Map<String,String> streams = new java.util.HashMap<>();
    Api(Config config) { this.config = config; }
    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(6000); c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(false); c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "cleanyourtv-home-dashboard/1.0");
        return c;
    }
    private byte[] read(HttpURLConnection c, int limit) throws IOException {
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            if (c.getContentLengthLong() > limit) throw new IOException("Response too large");
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) != -1;) {
                    if (out.size() + n > limit) throw new IOException("Response too large");
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }
    Metric sensor(String id, boolean battery) throws Exception {
        HttpURLConnection c = open(config.haUrl + "/api/states/" + id);
        c.setRequestProperty("Authorization", "Bearer " + config.token);
        return Metric.parse(new JSONObject(new String(read(c, 256 * 1024), StandardCharsets.UTF_8)), battery);
    }
    private void login() throws Exception {
        HttpURLConnection c = open(config.frigateUrl + "/api/login");
        try {
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] body = new JSONObject().put("user", config.frigateUser).put("password", config.frigatePassword).toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
            if (c.getResponseCode() != 200) throw new IOException("Frigate login refused");
            for (java.util.Map.Entry<String,List<String>> header : c.getHeaderFields().entrySet()) {
                if (!"Set-Cookie".equalsIgnoreCase(header.getKey())) continue;
                for (String value : header.getValue()) {
                    String first = value.split(";", 2)[0];
                    if (first.startsWith("frigate_token=")) cookie = first;
                }
            }
            if (cookie == null) throw new IOException("Frigate session missing");
        } finally { c.disconnect(); }
    }
    private synchronized byte[] frigate(String path) throws Exception {
        if (!config.frigateUser.isEmpty() && cookie == null) login();
        HttpURLConnection c = open(config.frigateUrl + path);
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        int code;
        try { code = c.getResponseCode(); } catch (IOException e) { c.disconnect(); throw e; }
        if (code == 401 && !config.frigateUser.isEmpty()) {
            c.disconnect(); cookie = null; login();
            c = open(config.frigateUrl + path); c.setRequestProperty("Cookie", cookie);
        }
        return read(c, 4 * 1024 * 1024);
    }
    List<String> cameras() throws Exception {
        JSONObject root = new JSONObject(new String(frigate("/api/config"), StandardCharsets.UTF_8));
        JSONObject cameras = root.optJSONObject("cameras");
        List<String> out = new ArrayList<>();
        if (cameras != null) for (java.util.Iterator<String> i = cameras.keys(); i.hasNext();) {
            String name = i.next();
            JSONObject camera = cameras.optJSONObject(name);
            if (name.matches("[A-Za-z0-9_-]+") && (camera == null || camera.optBoolean("enabled", true))) {
                out.add(name);
                String selected=name;
                JSONObject live=camera==null?null:camera.optJSONObject("live");
                JSONObject choices=live==null?null:live.optJSONObject("streams");
                if(choices!=null)for(java.util.Iterator<String> keys=choices.keys();keys.hasNext();){
                    String candidate=choices.optString(keys.next());
                    if(!candidate.matches("[A-Za-z0-9_.-]+"))continue;
                    if(selected.equals(name))selected=candidate;
                    if(candidate.toLowerCase(java.util.Locale.ROOT).matches(".*(sub|low).*")){selected=candidate;break;}
                }
                streams.put(name,selected);
            }
        }
        Collections.sort(out); return out;
    }
    byte[] frame(String camera) throws Exception {
        return frigate("/api/" + URLEncoder.encode(camera, "UTF-8") + "/latest.jpg?h=720");
    }
    String stream(String camera) { return streams.getOrDefault(camera,camera); }
    synchronized String sessionCookie() throws Exception {
        if(!config.frigateUser.isEmpty()&&cookie==null)login();
        return cookie;
    }
    byte[] feed(String url) throws Exception { return read(open(url), 2 * 1024 * 1024); }
    org.json.JSONArray activeReviews() throws Exception {return new org.json.JSONArray(new String(frigate("/api/review?limit=100&severity=alert"),StandardCharsets.UTF_8));}
}
