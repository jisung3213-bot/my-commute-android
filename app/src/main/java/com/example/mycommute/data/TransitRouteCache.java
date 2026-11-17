package com.example.mycommute.data;

import android.content.Context;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitRoute;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Isolated per-account profile cache. Never used after 23 hours. */
public final class TransitRouteCache {
    public static final long TTL=23L*60*60*1000;
    public static final class Snapshot {
        public final List<TransitRoute> routes;
        public final int selected;
        public final long timestamp;
        Snapshot(List<TransitRoute> r,int s,long t){routes=r;selected=s;timestamp=t;}
    }
    private TransitRouteCache(){}

    private static String fingerprint(Routine r) {
        StringBuilder b=new StringBuilder();
        b.append(r.startLat).append(',').append(r.startLon).append(';');
        for(com.example.mycommute.model.Waypoint w:r.waypoints){
            if(w.hasCoordinates())b.append(w.lat).append(',').append(w.lon).append(';');
        }
        b.append(r.endLat).append(',').append(r.endLon);
        return b.toString();
    }

    public static boolean save(Context c,Routine routine,List<TransitRoute> candidates,int index){
        if(candidates==null||candidates.isEmpty()||index<0||index>=candidates.size())return false;
        try{
            JSONObject root=new JSONObject();
            root.put("at",System.currentTimeMillis());
            root.put("fingerprint",fingerprint(routine));
            JSONArray array=new JSONArray();
            for(TransitRoute route:candidates)array.put(route.toCacheJson());
            root.put("routes",array);root.put("selected",index);
            String json=root.toString();
            if(json.length()>1_500_000)return false;
            RoutineStore.prefs(c).edit().putString("route_cache_"+routine.id,json).apply();
            return true;
        }catch(Exception e){
            android.util.Log.w("TransitCache","Cache write failed",e);
            return false;
        }
    }

    public static Snapshot load(Context c,Routine r){
        String key="route_cache_"+r.id;
        try{
            String json=RoutineStore.prefs(c).getString(key,"");
            if(json==null||json.isEmpty())return null;
            JSONObject root=new JSONObject(json);
            long time=root.optLong("at",0),now=System.currentTimeMillis();
            if(time<=0||time>now||now-time>=TTL ||
                    !fingerprint(r).equals(root.optString("fingerprint",""))) {
                clear(c,r.id);return null;
            }
            JSONArray a=root.getJSONArray("routes");
            List<TransitRoute> list=new ArrayList<>();
            for(int i=0;i<a.length();i++)list.add(TransitRoute.fromCacheJson(a.getJSONObject(i)));
            int selected=root.optInt("selected",-1);
            if(list.isEmpty()||selected<0||selected>=list.size())return null;
            return new Snapshot(Collections.unmodifiableList(list),selected,time);
        }catch(Exception e){clear(c,r.id);return null;}
    }

    public static void clear(Context c,String routineId){
        RoutineStore.prefs(c).edit().remove("route_cache_"+routineId).apply();
    }

    public static boolean hasExpired(Context c,String id){
        String text=RoutineStore.prefs(c).getString("route_cache_"+id,"");
        if(text==null||text.isEmpty())return false;
        try{
            JSONObject root=new JSONObject(text);
            long time=root.optLong("at",0);
            return time<=0||System.currentTimeMillis()-time>=TTL;
        }catch(Exception e){return true;}
    }
}
