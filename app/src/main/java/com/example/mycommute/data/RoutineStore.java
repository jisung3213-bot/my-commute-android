package com.example.mycommute.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.mycommute.model.Routine;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Account-isolated, local-first named commute profiles. Legacy slots remain readable. */
public final class RoutineStore {
    public static final String OUTBOUND="outbound",RETURN="return";
    public static final String[] IDS={OUTBOUND,RETURN};
    private RoutineStore(){}

    public static SharedPreferences prefs(Context c){
        return c.getSharedPreferences("commute_local_"+Session.accountId(c),Context.MODE_PRIVATE);
    }

    public static String directionOf(String id){
        return id!=null&&(id.equals(RETURN)||id.startsWith(RETURN+"_"))?RETURN:OUTBOUND;
    }
    public static String activeDirection(Context c){
        String direction=prefs(c).getString("active_direction","");
        if(RETURN.equals(direction)||OUTBOUND.equals(direction))return direction;
        return directionOf(prefs(c).getString("selected",OUTBOUND));
    }

    public static List<Routine> list(Context c,String direction){
        String group=RETURN.equals(direction)?RETURN:OUTBOUND;
        Set<String> ids=new LinkedHashSet<>();
        // Upgrade from the original single inbound/outbound slot without deleting data.
        Routine legacy=read(c,group);
        if(legacy.isConfigured())ids.add(group);
        String raw=prefs(c).getString("preset_index_"+group,"[]");
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++){
                String id=a.optString(i,"");
                if(directionOf(id).equals(group)&&!id.isEmpty())ids.add(id);
            }
        }catch(Exception ignored){}
        List<Routine> out=new ArrayList<>();
        for(String id:ids){
            Routine r=read(c,id);
            if(r.isConfigured()||!r.name.trim().isEmpty())out.add(r);
        }
        return out;
    }

    public static Routine newDraft(String direction) {
        String group=RETURN.equals(direction)?RETURN:OUTBOUND;
        Routine r=new Routine();
        r.id=group+"_"+UUID.randomUUID().toString().replace("-","");
        r.name=group.equals(OUTBOUND)?"새 출근길":"새 퇴근길";
        r.arrivalTime=group.equals(OUTBOUND)?"09:00":"18:00";
        r.days="2,3,4,5,6";
        r.apiBased=true;
        return r;
    }

    public static String defaultId(Context c,String direction){
        String group=RETURN.equals(direction)?RETURN:OUTBOUND;
        String wanted=prefs(c).getString("default_profile_"+group,"");
        for(Routine r:list(c,group))if(r.id.equals(wanted))return wanted;
        List<Routine> list=list(c,group);
        return list.isEmpty()?group:list.get(0).id;
    }

    public static String selected(Context c){
        String group=activeDirection(c);
        String wanted=prefs(c).getString("selected_profile_"+group,"");
        for(Routine r:list(c,group))if(r.id.equals(wanted))return wanted;
        return defaultId(c,group);
    }

    /** Switching direction shows that direction's default, unless a named route is selected next. */
    public static void selectDirection(Context c,String direction) {
        if(!OUTBOUND.equals(direction)&&!RETURN.equals(direction))return;
        String defaultId=defaultId(c,direction);
        prefs(c).edit().putString("active_direction",direction)
                .putString("selected_profile_"+direction,defaultId)
                .putString("selected",defaultId).apply();
    }

    public static void select(Context c,String id){
        String direction=directionOf(id);
        boolean found=false;
        for(Routine r:list(c,direction))if(id.equals(r.id)){found=true;break;}
        if(!found){
            if(OUTBOUND.equals(id)||RETURN.equals(id))selectDirection(c,id);
            return;
        }
        prefs(c).edit().putString("active_direction",direction)
                .putString("selected_profile_"+direction,id)
                .putString("selected",id).apply();
    }

    public static void setDefault(Context c,String id){
        String direction=directionOf(id);
        boolean found=false;
        for(Routine r:list(c,direction))if(id.equals(r.id)){found=true;break;}
        if(!found)throw new IllegalArgumentException("먼저 경로를 저장해야 기본값으로 지정할 수 있습니다.");
        prefs(c).edit().putString("default_profile_"+direction,id).apply();
        FirestoreSync.uploadPreferences(c);
    }

    public static void restoreDefaultsOnLogin(Context c){
        String direction=activeDirection(c);
        selectDirection(c,direction);
    }

    public static Routine read(Context c){return read(c,selected(c));}
    public static Routine read(Context c,String id){
        Routine r;
        try{r=Routine.fromJson(new JSONObject(prefs(c).getString("routine_"+id,"{}")));}
        catch(Exception e){r=new Routine();}
        r.id=id;
        return r;
    }

    public static void save(Context c,Routine r){
        saveLocal(c,r);
        select(c,r.id);
        FirestoreSync.uploadIfSignedIn(r);
        FirestoreSync.uploadPreferences(c);
    }

    public static void saveLocal(Context c,Routine r){
        if(r==null||r.id==null||r.id.trim().isEmpty())
            throw new IllegalArgumentException("루틴 ID 필요");
        try {
            SharedPreferences p=prefs(c);
            String direction=directionOf(r.id);
            Set<String> ids=new LinkedHashSet<>();
            JSONArray old=new JSONArray(p.getString("preset_index_"+direction,"[]"));
            for(int i=0;i<old.length();i++){
                String id=old.optString(i,"");
                if(!id.isEmpty()&&direction.equals(directionOf(id)))ids.add(id);
            }
            ids.add(r.id);
            JSONArray updated=new JSONArray();
            for(String id:ids)updated.put(id);
            SharedPreferences.Editor edit=p.edit().putString("routine_"+r.id,r.toJson().toString())
                    .putString("preset_index_"+direction,updated.toString());
            if(p.getString("default_profile_"+direction,"").isEmpty())
                edit.putString("default_profile_"+direction,r.id);
            edit.apply();
        }catch(Exception e){throw new IllegalArgumentException("루틴 저장 실패",e);}
    }

    /** Cloud-synced preference identifiers; never includes TMAP provider route data. */
    public static JSONObject exportPreferences(Context c){
        JSONObject o=new JSONObject();
        try{
            o.put("outbound",defaultId(c,OUTBOUND));
            o.put("return",defaultId(c,RETURN));
            o.put("alarmSettings",AlarmSettingsStore.exportJson(c));
        }catch(Exception ignored){}
        return o;
    }
    public static void importPreferences(Context c,JSONObject o){
        if(o==null)return;
        AlarmSettingsStore.importJson(c,o.optJSONObject("alarmSettings"));
        for(String group:IDS){
            String id=o.optString(group,"");
            for(Routine r:list(c,group))if(id.equals(r.id))
                prefs(c).edit().putString("default_profile_"+group,id).apply();
        }
    }
}
