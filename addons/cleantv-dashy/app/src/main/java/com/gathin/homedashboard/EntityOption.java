package com.gathin.homedashboard;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Discovery retains only entity identifiers and names, never the returned states/attributes. */
final class EntityOption {
    final String id,name;
    EntityOption(String id,String name){this.id=id;this.name=name;}
    @Override public String toString(){return name.isEmpty()||name.equals(id)?id:name+"\n"+id;}
    static List<EntityOption> parse(JSONArray states){
        List<EntityOption> out=new ArrayList<>();
        for(int i=0;i<states.length();i++){
            JSONObject state=states.optJSONObject(i);if(state==null)continue;
            String id=state.optString("entity_id");
            try{Config.entity(id);}catch(IllegalArgumentException e){continue;}
            JSONObject attributes=state.optJSONObject("attributes");
            String name=attributes==null?"":attributes.optString("friendly_name").replaceAll("[\\p{Cntrl}]"," ").trim();
            if(name.length()>100)name=name.substring(0,100);
            out.add(new EntityOption(id,name));
        }
        out.sort(Comparator.comparing(option->option.id));return out;
    }
}
