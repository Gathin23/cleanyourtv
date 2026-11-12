package com.gathin.homedashboard;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Three reusable, read-only Home Assistant state cards; old solar setups migrate in memory. */
final class SensorCard {
    static final String[] MODES={"auto","percentage","power"};
    static final String[] COLOURS={"#70D8B0","#FFD580","#AED0FF"};
    final String label,entity,mode,colour;
    final int decimals;
    final boolean enabled;

    SensorCard(JSONObject o,int slot){
        entity=o.optString("entity").trim();
        enabled=o.optBoolean("enabled",!entity.isEmpty());
        if(!entity.isEmpty())Config.entity(entity);
        if(enabled&&entity.isEmpty())throw new IllegalArgumentException("Choose an entity for this card");
        label=o.optString("label","Card "+(slot+1)).trim();
        if(label.isEmpty()||label.length()>40||label.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("Use a label of 1 to 40 characters");
        mode=o.optString("mode","auto");
        if(!java.util.Arrays.asList(MODES).contains(mode))throw new IllegalArgumentException("Choose Auto, Percentage or Power");
        decimals=o.optInt("decimals",2);
        if(decimals<0||decimals>3)throw new IllegalArgumentException("Use 0 to 3 decimal places");
        colour=Config.glowColor(o.optString("colour",COLOURS[slot]));
    }
    JSONObject json(){
        try{return new JSONObject().put("label",label).put("entity",entity).put("mode",mode)
                .put("decimals",decimals).put("colour",colour).put("enabled",enabled);}
        catch(org.json.JSONException e){throw new IllegalStateException(e);}
    }
    static List<SensorCard> read(JSONObject config){
        JSONArray list=config.optJSONArray("sensorCards");
        if(config.has("sensorCards")&&list==null)throw new IllegalArgumentException("sensorCards must be an array");
        if(list!=null&&list.length()>3)throw new IllegalArgumentException("Up to three sensor cards");
        List<SensorCard> out=new ArrayList<>();
        for(int i=0;i<3;i++){
            JSONObject item;
            if(list!=null){
                item=i<list.length()?list.optJSONObject(i):new JSONObject();
                if(item==null)throw new IllegalArgumentException("Invalid sensor card");
            }else{
                String[] keys={"batteryEntity","solarEntity","loadEntity"};
                String[] labels={"BATTERY SOC","SOLAR NOW","LOAD NOW"};
                try{item=new JSONObject().put("entity",config.optString(keys[i]).trim()).put("label",labels[i])
                        .put("mode",i==0?"percentage":"power").put("decimals",i==0?0:2);}
                catch(org.json.JSONException e){throw new IllegalStateException(e);}
            }
            out.add(new SensorCard(item,i));
        }
        return out;
    }
    static JSONArray json(List<SensorCard> cards){JSONArray out=new JSONArray();for(SensorCard card:cards)out.put(card.json());return out;}
}
