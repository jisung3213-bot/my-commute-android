package com.example.mycommute.data;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public final class Session {
    private Session() { }
    public static boolean firebaseConfigured(Context context) { return !FirebaseApp.getApps(context).isEmpty(); }
    public static boolean signedIn(Context context) {
        if (context.getSharedPreferences("demo_session",Context.MODE_PRIVATE).getBoolean("demo",false)) return true;
        return firebaseConfigured(context) && FirebaseAuth.getInstance().getCurrentUser()!=null;
    }
    public static String accountId(Context context) {
        if(context.getSharedPreferences("demo_session",Context.MODE_PRIVATE).getBoolean("demo",false))return "demo";
        if (firebaseConfigured(context) && FirebaseAuth.getInstance().getCurrentUser()!=null)
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        return "demo";
    }
    public static void signInDemo(Context context) {
        if(firebaseConfigured(context)) FirebaseAuth.getInstance().signOut();
        context.getSharedPreferences("demo_session",Context.MODE_PRIVATE).edit().putBoolean("demo",true).apply();
    }
    public static void signOut(Context context) {
        if (firebaseConfigured(context)) FirebaseAuth.getInstance().signOut();
        context.getSharedPreferences("demo_session",Context.MODE_PRIVATE).edit().remove("demo").apply();
    }
}
