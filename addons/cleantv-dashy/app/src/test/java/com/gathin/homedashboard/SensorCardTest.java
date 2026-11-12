package com.gathin.homedashboard;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SensorCardTest {
    private JSONObject temperature()throws Exception{return new JSONObject().put("entity","sensor.temperature").put("label","Room temperature");}
    @Test public void migratesExistingSolarCardsWithoutChangingFormatting()throws Exception{
        java.util.List<SensorCard> cards=SensorCard.read(new JSONObject().put("batteryEntity","sensor.battery").put("solarEntity","sensor.pv").put("loadEntity","sensor.load"));
        assertEquals("sensor.battery",cards.get(0).entity);assertEquals("BATTERY SOC",cards.get(0).label);assertEquals("percentage",cards.get(0).mode);assertEquals(0,cards.get(0).decimals);
        assertEquals("power",cards.get(1).mode);assertEquals(2,cards.get(1).decimals);assertEquals("sensor.load",cards.get(2).entity);
    }
    @Test public void missingLegacyCardsStayHidden()throws Exception{for(SensorCard c:SensorCard.read(new JSONObject()))assertFalse(c.enabled);}
    @Test public void explicitCardsNeedNoSolarEntities()throws Exception{
        Config c=new Config(new JSONObject().put("haUrl","https://ha.example.test").put("haToken","example-token").put("frigateUrl","https://cameras.example.test").put("sensorCards",new JSONArray().put(temperature())));
        assertEquals("sensor.temperature",c.cards.get(0).entity);assertEquals("auto",c.cards.get(0).mode);assertFalse(c.cards.get(1).enabled);assertEquals("",c.solar);
    }
    @Test public void explicitEmptyListHidesEvenLegacyCards()throws Exception{assertFalse(SensorCard.read(new JSONObject().put("batteryEntity","sensor.battery").put("sensorCards",new JSONArray())).get(0).enabled);}
    @Test public void editableFieldsRoundTrip()throws Exception{
        SensorCard card=new SensorCard(temperature().put("colour","#abcdef").put("decimals",1).put("enabled",false),1);
        SensorCard restored=SensorCard.read(new JSONObject().put("sensorCards",new JSONArray().put(card.json()))).get(0);
        assertEquals("#ABCDEF",restored.colour);assertEquals(card.label,restored.label);assertEquals(card.entity,restored.entity);assertEquals(1,restored.decimals);assertFalse(restored.enabled);
    }
    @Test public void acceptsEntitiesOutsideSensorDomain()throws Exception{assertEquals("binary_sensor.front_door",new SensorCard(temperature().put("entity","binary_sensor.front_door"),0).entity);assertEquals("weather.home",Config.entity("weather.home"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsEntityPathInjection()throws Exception{new SensorCard(temperature().put("entity","sensor.x/../../config"),0);}
    @Test(expected=IllegalArgumentException.class)public void rejectsEntityQueryInjection()throws Exception{new SensorCard(temperature().put("entity","sensor.x?foo=bar"),0);}
    @Test(expected=IllegalArgumentException.class)public void enabledCardNeedsEntity()throws Exception{new SensorCard(new JSONObject().put("enabled",true),0);}
    @Test(expected=IllegalArgumentException.class)public void rejectsTooManyCards()throws Exception{SensorCard.read(new JSONObject().put("sensorCards",new JSONArray().put(temperature()).put(temperature()).put(temperature()).put(temperature())));}
    @Test(expected=IllegalArgumentException.class)public void rejectsNonArray()throws Exception{SensorCard.read(new JSONObject().put("sensorCards","not an array"));}
    @Test(expected=IllegalArgumentException.class)public void rejectsMalformedEntries()throws Exception{SensorCard.read(new JSONObject().put("sensorCards",new JSONArray().put("not an object")));}
    @Test(expected=IllegalArgumentException.class)public void rejectsInvalidMode()throws Exception{new SensorCard(temperature().put("mode","control"),0);}
    @Test(expected=IllegalArgumentException.class)public void rejectsUnboundedPrecision()throws Exception{new SensorCard(temperature().put("decimals",999),0);}
    @Test(expected=IllegalArgumentException.class)public void rejectsControlCharactersInLabels()throws Exception{new SensorCard(temperature().put("label","Room\nname"),0);}
    @Test(expected=IllegalArgumentException.class)public void rejectsInvalidColour()throws Exception{new SensorCard(temperature().put("colour","red"),0);}
}
