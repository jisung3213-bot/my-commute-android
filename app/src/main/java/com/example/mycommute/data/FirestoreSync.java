package com.example.mycommute.data;

import android.content.Context;
import android.util.Log;
import com.example.mycommute.model.Routine;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Firestore only holds user-authored profiles/preferences, never provider route geometry. */
public final class FirestoreSync {
    public interface Result {void onLoaded(Routine routine);void onFailure(String reason);}
    public interface ProfilesResult {void onLoaded(List<Routine> routines);void onFailure(String reason);}
    public interface SettingsResult {void onLoaded(JSONObject settings);void onFailure(String reason);}
    private FirestoreSync(){}

    private static FirebaseUser currentUser() {
        try{return FirebaseAuth.getInstance().getCurrentUser();}
        catch(Exception ignored){return null;}
    }

    public static void uploadIfSignedIn(Routine r) {
        FirebaseUser user=currentUser();
        if(user==null)return;
        try {
            Map<String,Object> data=new HashMap<>();
            data.put("payload",r.toJson().toString());
            data.put("updatedAt",com.google.firebase.firestore.FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .collection("routines").document(r.id).set(data,SetOptions.merge())
                    .addOnFailureListener(e->Log.w("CommuteSync","Routine sync failed: "+e.getClass().getSimpleName()));
        }catch(Exception e){Log.w("CommuteSync","Routine serialization failed",e);}
    }

    public static void uploadPreferences(Context context){
        FirebaseUser user=currentUser();
        if(user==null)return;
        try{
            Map<String,Object> data=new HashMap<>();
            data.put("payload",RoutineStore.exportPreferences(context).toString());
            data.put("updatedAt",com.google.firebase.firestore.FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .collection("settings").document("commute").set(data,SetOptions.merge())
                    .addOnFailureListener(e->Log.w("CommuteSync","Defaults sync failed",e));
        }catch(Exception ignored){}
    }

    public static void fetchAll(ProfilesResult result){
        FirebaseUser user=currentUser();
        if(user==null){result.onFailure("계정 없음");return;}
        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .collection("routines").get()
                .addOnSuccessListener(snapshot->{
                    List<Routine> profiles=new ArrayList<>();
                    for(com.google.firebase.firestore.QueryDocumentSnapshot doc:snapshot){
                        try{
                            String payload=doc.getString("payload");
                            if(payload==null)continue;
                            Routine r=Routine.fromJson(new JSONObject(payload));
                            r.id=doc.getId();
                            if(r.isConfigured()&&r.id.length()<=90)profiles.add(r);
                        }catch(Exception e){Log.w("CommuteSync","Skipped invalid preset",e);}
                    }
                    result.onLoaded(profiles);
                }).addOnFailureListener(e->result.onFailure(e.getMessage()));
    }

    public static void fetchPreferences(SettingsResult result){
        FirebaseUser user=currentUser();
        if(user==null){result.onFailure("계정 없음");return;}
        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .collection("settings").document("commute").get()
                .addOnSuccessListener(snapshot->{
                    try{
                        String payload=snapshot.getString("payload");
                        result.onLoaded(payload==null?new JSONObject():new JSONObject(payload));
                    }catch(Exception e){result.onFailure("기본 경로 설정 형식 오류");}
                }).addOnFailureListener(e->result.onFailure(e.getMessage()));
    }

    /** Compatibility with previous two-slot cloud API. */
    public static void fetch(String id,Result result) {
        FirebaseUser user=currentUser();
        if(user==null){result.onFailure("계정 없음");return;}
        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .collection("routines").document(id).get()
                .addOnSuccessListener(doc->{
                    String json=doc.getString("payload");
                    if(json==null){result.onFailure("저장된 루틴 없음");return;}
                    try{
                        Routine r=Routine.fromJson(new JSONObject(json));r.id=id;
                        result.onLoaded(r);
                    }catch(Exception e){result.onFailure("데이터 형식 오류");}
                }).addOnFailureListener(e->result.onFailure(e.getMessage()));
    }
}
