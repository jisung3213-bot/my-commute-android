package com.example.mycommute.data;

import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitRoute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Memory-only route preview. Never persisted to Room/SharedPreferences/Firestore:
 * TMAP transit data must not be used for longer than the provider permits.
 */
public final class TransitRouteMemory {
    private static final long MAX_AGE_MS=15*60*1000L;
    private static String owner="",routeKey="";
    private static long receivedAt=0;
    private static int selected=-1;
    private static List<TransitRoute> candidates=Collections.emptyList();

    private TransitRouteMemory() {}

    private static String key(Routine r) {
        return r.id+"|"+r.startLat+"|"+r.startLon+"|"+r.endLat+"|"+r.endLon;
    }

    public static synchronized void put(String uid,Routine routine,List<TransitRoute> routes) {
        owner=uid; routeKey=key(routine); receivedAt=System.currentTimeMillis();
        selected=-1;
        candidates=new ArrayList<>(routes);
    }

    public static synchronized boolean choose(String uid,Routine routine,int index) {
        if(!valid(uid,routine)||index<0||index>=candidates.size())return false;
        selected=index;
        return true;
    }

    public static synchronized TransitRoute selected(String uid,Routine routine) {
        if(!valid(uid,routine)||selected<0||selected>=candidates.size())return null;
        return candidates.get(selected);
    }

    public static synchronized void clear() {
        owner="";routeKey="";receivedAt=0;selected=-1;
        candidates=Collections.emptyList();
    }

    private static boolean valid(String uid,Routine routine) {
        if(!uid.equals(owner)||!key(routine).equals(routeKey)
                ||System.currentTimeMillis()-receivedAt>MAX_AGE_MS) {
            clear();return false;
        }
        return true;
    }
}
