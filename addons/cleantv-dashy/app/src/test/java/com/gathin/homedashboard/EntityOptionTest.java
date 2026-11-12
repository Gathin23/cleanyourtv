package com.gathin.homedashboard;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class EntityOptionTest {
    @Test public void discoveryKeepsSortedIdsAndNames()throws Exception{
        java.util.List<EntityOption> options=EntityOption.parse(new JSONArray()
            .put(new JSONObject().put("entity_id","sensor.temperature").put("state","22.4").put("attributes",new JSONObject().put("friendly_name","Living room")))
            .put(new JSONObject().put("entity_id","binary_sensor.door")));
        assertEquals(2,options.size());assertEquals("binary_sensor.door",options.get(0).id);assertEquals("Living room\nsensor.temperature",options.get(1).toString());
    }
    @Test public void malformedIdsCannotBecomeRequests()throws Exception{assertTrue(EntityOption.parse(new JSONArray().put(new JSONObject().put("entity_id","sensor.x/../../other")).put("bad")).isEmpty());}
    @Test public void namesAreBoundedAndHaveNoControls()throws Exception{EntityOption option=EntityOption.parse(new JSONArray().put(new JSONObject().put("entity_id","sensor.room").put("attributes",new JSONObject().put("friendly_name","Room\nname")))).get(0);assertEquals("Room name",option.name);}
}
