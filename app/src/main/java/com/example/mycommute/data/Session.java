package com.example.mycommute.data;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public final class Session {
    private Session() { }
    public static boolean firebaseConfigured(Context context) { return !FirebaseApp.getApps(context).isEmpty(); }
    public static boolean signedIn(Context context) {
        return firebaseConfigured(context) && FirebaseAuth.getInstance().getCurrentUser()!=null;
    }
    public static String accountId(Context context) {
        if (firebaseConfigured(context) && FirebaseAuth.getInstance().getCurrentUser()!=null)
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        return "";
    }
    public static void signOut(Context context) {
        if (firebaseConfigured(context)) FirebaseAuth.getInstance().signOut();
    }
}
