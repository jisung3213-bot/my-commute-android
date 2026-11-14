package com.example.mycommute.data;

import com.example.mycommute.model.Routine;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;

/** Firestore is optional. Local storage stays usable when offline. */
public final class FirestoreSync {
    public interface Result { void onLoaded(Routine routine); void onFailure(String reason); }
    private FirestoreSync() { }
    public static void uploadIfSignedIn(Routine r) {
        try {
            FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser(); if(u==null) return;
            Map<String,Object> data=new HashMap<>();
            data.put("payload", r.toJson().toString());
            data.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance().collection("users").document(u.getUid())
                    .collection("routines").document(r.id).set(data, SetOptions.merge())
                    .addOnFailureListener(e -> android.util.Log.w("CommuteSync","Routine sync failed: "+e.getClass().getSimpleName()));
        } catch(Exception ignored) { }
    }
    public static void fetch(String routineId,Result result) {
        try {
            FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser(); if(u==null) {result.onFailure("계정이 연결되지 않음");return;}
            FirebaseFirestore.getInstance().collection("users").document(u.getUid())
                    .collection("routines").document(routineId).get()
                    .addOnSuccessListener(doc -> {
                        String value = doc.getString("payload");
                        if(value==null) result.onFailure("서버에 저장된 루틴 없음");
                        else try { result.onLoaded(Routine.fromJson(new org.json.JSONObject(value))); }
                             catch(Exception e) { result.onFailure("서버 데이터 형식 오류"); }
                    }).addOnFailureListener(e -> result.onFailure(e.getMessage()));
        } catch(Exception e) { result.onFailure(e.getMessage()); }
    }
}
