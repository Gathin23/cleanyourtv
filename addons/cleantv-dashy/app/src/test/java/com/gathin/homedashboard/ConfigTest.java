package com.gathin.homedashboard;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigTest {
    private JSONObject base()throws Exception{return new JSONObject().put("haUrl","https://ha.example.test/").put("haToken","example-token").put("batteryEntity","sensor.battery").put("solarEntity","sensor.pv").put("frigateUrl","https://cameras.example.test/");}
    @Test public void emptyCameraListMeansAll()throws Exception{assertTrue(new Config(base()).cameras.isEmpty());}
    @Test public void selectedCamerasArePreserved()throws Exception{assertEquals("front_door",new Config(base().put("cameras",new JSONArray().put("front_door"))).cameras.get(0));}
    @Test(expected=IllegalArgumentException.class)public void httpCannotCarryTheToken()throws Exception{new Config(base().put("haUrl","http://ha.example.test"));}
    @Test(expected=IllegalArgumentException.class)public void embeddedCredentialsAreRejected()throws Exception{new Config(base().put("feeds",new JSONArray().put("https://user:pass@news.example.test/feed")));}
    @Test(expected=IllegalArgumentException.class)public void unexpectedSensorPathIsRejected()throws Exception{new Config(base().put("batteryEntity","sensor.x/../../other"));}
    @Test public void rotationHasBounds()throws Exception{assertEquals(5,new Config(base().put("cameraSeconds",0)).cameraSeconds);assertEquals(300,new Config(base().put("cameraSeconds",999)).cameraSeconds);}
    @Test public void loadSensorIsOptional()throws Exception{assertEquals("",new Config(base()).load);}
    @Test public void personFocusIsOffByDefault()throws Exception{assertFalse(new Config(base()).activityFirst);}
    @Test public void activityGlowDefaultsToRed()throws Exception{Config c=new Config(base());assertTrue(c.activityGlow);assertEquals("#FF3B30",c.activityGlowColor);}
    @Test public void customGlowColourIsNormalized()throws Exception{assertEquals("#ABCDEF",new Config(base().put("activityGlowColor","#abcdef")).activityGlowColor);}
    @Test public void glowCanBeDisabled()throws Exception{assertFalse(new Config(base().put("activityGlow",false)).activityGlow);}
    @Test(expected=IllegalArgumentException.class)public void malformedGlowColourIsRejected()throws Exception{new Config(base().put("activityGlowColor","red;"));}
    @Test public void brokerAndFocusAreConfigurable()throws Exception{Config c=new Config(base().put("mqttUrl","mqtt://viewer:password@broker.example.test:1883").put("activityFirst",true));assertTrue(c.activityFirst);assertEquals("frigate",c.topicPrefix);}
    @Test(expected=IllegalArgumentException.class)public void mqttWildcardsAreRejectedInPrefix()throws Exception{new Config(base().put("mqttTopicPrefix","frigate/#"));}
    @Test(expected=IllegalArgumentException.class)public void httpIsNotAMqttBroker()throws Exception{new Config(base().put("mqttUrl","https://broker.example.test"));}
    @Test public void loadSensorIsPreserved()throws Exception{assertEquals("sensor.house_load",new Config(base().put("loadEntity","sensor.house_load")).load);}
    @Test(expected=IllegalArgumentException.class)public void unexpectedLoadPathIsRejected()throws Exception{new Config(base().put("loadEntity","sensor.load/../../other"));}
}
