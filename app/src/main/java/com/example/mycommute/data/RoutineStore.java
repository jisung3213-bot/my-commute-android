package com.example.mycommute.data;
import android.content.Context;
import android.content.SharedPreferences;
import com.example.mycommute.model.Routine;
import org.json.JSONObject;
public final class RoutineStore {
 public static final String[] IDS={"outbound","return"};
 public static SharedPreferences prefs(Context c){return c.getSharedPreferences("commute_local_"+Session.accountId(c),Context.MODE_PRIVATE);}
 public static String selected(Context c){return prefs(c).getString("selected","outbound");}
 public static void select(Context c,String id){if(!id.equals("outbound")&&!id.equals("return"))return;prefs(c).edit().putString("selected",id).apply();}
 public static Routine read(Context c){return read(c,selected(c));}
 public static Routine read(Context c,String id){
  Routine r;try{r=Routine.fromJson(new JSONObject(prefs(c).getString("routine_"+id,"{}")));}catch(Exception e){r=new Routine();}r.id=id;return r;
 }
 public static void save(Context c,Routine r){saveLocal(c,r);FirestoreSync.uploadIfSignedIn(r);}
 public static void saveLocal(Context c,Routine r){try{prefs(c).edit().putString("routine_"+r.id,r.toJson().toString()).apply();}catch(Exception e){throw new IllegalArgumentException("루틴 저장 실패",e);}}
 private RoutineStore(){}
}
