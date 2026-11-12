package com.gathin.homedashboard;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Active person alerts already qualified by Frigate's zone rules. All methods are serialized. */
final class PersonFocus {
    private final Map<String,String> reviews=new LinkedHashMap<>();
    private final Set<String> ended=new LinkedHashSet<>();
    private String shown;
    private long next;
    synchronized void message(String payload){
        try{
            JSONObject message=new JSONObject(payload);String type=message.optString("type");
            if(!List.of("new","update","end").contains(type))return;
            JSONObject after=message.optJSONObject("after");if(after==null)return;
            apply(after,"end".equals(type));
        }catch(Exception ignored){}
    }
    synchronized void seed(JSONArray rows){
        for(int i=0;i<Math.min(rows.length(),100);i++){
            JSONObject row=rows.optJSONObject(i);
            if(row!=null&&row.isNull("end_time")&&!reviews.containsKey(row.optString("id")))apply(row,false);
        }
    }
    private void apply(JSONObject row,boolean end){
        String id=row.optString("id"),camera=row.optString("camera");
        if(id.isEmpty()||id.length()>160||!camera.matches("[A-Za-z0-9_-]+"))return;
        if(end){reviews.remove(id);ended.add(id);while(ended.size()>256)ended.remove(ended.iterator().next());return;}
        if(ended.contains(id))return;
        boolean person=false;JSONObject data=row.optJSONObject("data");JSONArray objects=data==null?null:data.optJSONArray("objects");
        if(objects!=null)for(int i=0;i<objects.length();i++)if(List.of("person","person-verified").contains(objects.optString(i)))person=true;
        if(!person||!"alert".equals(row.optString("severity"))){reviews.remove(id);return;}
        if(reviews.size()<128||reviews.containsKey(id))reviews.put(id,camera);
    }
    synchronized String choose(List<String> allowed,long now){
        List<String> active=new ArrayList<>();
        for(String camera:reviews.values())if(allowed.contains(camera)&&!active.contains(camera))active.add(camera);
        if(active.isEmpty()){shown=null;return null;}
        if(!active.contains(shown)){shown=active.get(0);next=now+8000;}
        else if(active.size()>1&&now>=next){shown=active.get((active.indexOf(shown)+1)%active.size());next=now+8000;}
        return shown;
    }
    synchronized void clear(){reviews.clear();ended.clear();shown=null;next=0;}
}
