package com.gathin.homedashboard;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

final class Config {
    final String haUrl, token, battery, solar, load, frigateUrl, frigateUser, frigatePassword;
    final List<String> cameras = new ArrayList<>(), feeds = new ArrayList<>();
    final List<SensorCard> cards;
    final int cameraSeconds;
    final boolean activityFirst;
    final boolean activityGlow;
    final String activityGlowColor;
    final String mqttUrl,topicPrefix;
    Config(JSONObject o) {
        haUrl = base(o.optString("haUrl"));
        token = o.optString("haToken").trim();
        battery = optionalEntity(o.optString("batteryEntity"));
        solar = optionalEntity(o.optString("solarEntity"));
        String loadId=o.optString("loadEntity").trim();
        load=loadId.isEmpty()?"":entity(loadId);
        cards=SensorCard.read(o);
        frigateUrl = base(o.optString("frigateUrl"));
        frigateUser = o.optString("frigateUser");
        frigatePassword = o.optString("frigatePassword");
        cameraSeconds = Math.max(5, Math.min(300, o.optInt("cameraSeconds", 15)));
        activityFirst=o.optBoolean("activityFirst",false);
        activityGlow=o.optBoolean("activityGlow",true);activityGlowColor=glowColor(o.optString("activityGlowColor","#FF3B30"));
        mqttUrl=o.optString("mqttUrl").trim();topicPrefix=o.optString("mqttTopicPrefix","frigate");
        if(!topicPrefix.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*"))throw new IllegalArgumentException("Invalid MQTT topic prefix");
        if(!mqttUrl.isEmpty()){
            try{URI broker=new URI(mqttUrl);if(!"mqtt".equals(broker.getScheme())||broker.getHost()==null||broker.getPort()>65535||broker.getFragment()!=null||broker.getQuery()!=null||!(broker.getPath()==null||broker.getPath().isEmpty()))throw new IllegalArgumentException();}
            catch(Exception e){throw new IllegalArgumentException("MQTT broker address required");}
        }
        read(o.optJSONArray("cameras"), cameras, 24);
        for (String camera : cameras) if (!camera.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid camera name");
        read(o.optJSONArray("feeds"), feeds, 10);
        for (String feed : feeds) base(feed);
        if (token.isEmpty() || token.contains("\n") || token.contains("\r")) throw new IllegalArgumentException("Home Assistant token required");
    }
    static String base(String s) {
        try {
            URI uri = new URI(s);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("HTTPS address required");
            return s.replaceAll("/+$", "");
        } catch (java.net.URISyntaxException e) { throw new IllegalArgumentException("Invalid address"); }
    }
    static String entity(String s) {
        if (s.length()>255||!s.matches("[a-z][a-z0-9_]*\\.[a-z0-9_]+")) throw new IllegalArgumentException("Use an entity ID such as sensor.room_temperature");
        return s;
    }
    private static String optionalEntity(String s){return s.trim().isEmpty()?"":entity(s.trim());}
    static String glowColor(String value){
        if(!value.matches("#[A-Fa-f0-9]{6}"))throw new IllegalArgumentException("Use a six-digit hex colour");
        return value.toUpperCase(java.util.Locale.ROOT);
    }
    private static void read(JSONArray a, List<String> into, int limit) {
        if (a == null) return;
        for (int i = 0; i < Math.min(a.length(), limit); i++) {
            String value = a.optString(i).trim();
            if (!value.isEmpty()) into.add(value);
        }
    }
    static File file(Context c) { return new File(c.getFilesDir(), "config.json"); }
    static Config load(Context c) throws Exception {
        JSONObject json = new JSONObject(new String(Files.readAllBytes(file(c).toPath()), StandardCharsets.UTF_8));
        android.content.SharedPreferences prefs = c.getSharedPreferences("choices", Context.MODE_PRIVATE);
        for (String key : new String[]{"cameras", "feeds", "sensorCards"}) {
            if (prefs.contains(key)) json.put(key, new JSONArray(prefs.getString(key, "[]")));
        }
        if (prefs.contains("cameraSeconds")) json.put("cameraSeconds", prefs.getInt("cameraSeconds", 15));
        if (prefs.contains("activityFirst")) json.put("activityFirst",prefs.getBoolean("activityFirst",false));
        if (prefs.contains("activityGlow")) json.put("activityGlow",prefs.getBoolean("activityGlow",true));
        if (prefs.contains("activityGlowColor")) json.put("activityGlowColor",prefs.getString("activityGlowColor","#FF3B30"));
        return new Config(json);
    }
}
