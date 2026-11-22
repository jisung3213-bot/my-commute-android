package com.example.mycommute.data;

import android.content.Context;
import com.example.mycommute.alarms.ReminderScheduler;
import com.example.mycommute.alarms.AutoCommute;
import com.example.mycommute.engine.RouteSignature;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.network.TmapTransitClient;
import com.example.mycommute.network.TmapWaypointRouteClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Refreshes a saved route by matching the user's choice to a fresh provider response. */
public final class PinnedRouteRefresh {
    public interface Callback { void finished(boolean found); }
    private static final long RETRY_MS=15*60_000L;
    private static final Map<String,List<Callback>> requests=new HashMap<>();

    private PinnedRouteRefresh() {}

    public static boolean canRefresh(Routine routine) {
        return routine.isConfigured()&&routine.apiBased&&routine.validCoordinates()
                &&!routine.selectedRouteSignature.isEmpty();
    }

    // Activity and JobService callers use the Android main thread.
    public static boolean request(Context context,Routine routine,long neededUntil,Callback callback) {
        if(!canRefresh(routine)||!Session.signedIn(context)||!TmapTransitClient.enabled())return false;
        TransitRouteCache.Snapshot cached=TransitRouteCache.load(context,routine);
        if(cached!=null&&cached.timestamp+TransitRouteCache.TTL>neededUntil)return false;
        Context app=context.getApplicationContext();
        String account=Session.accountId(app);
        String signature=routine.selectedRouteSignature;
        String fingerprint=TransitRouteCache.fingerprint(routine);
        String key=account+"/"+routine.id+"/"+signature+"/"+fingerprint;
        List<Callback> pending=requests.get(key);
        if(pending!=null){pending.add(callback);return true;}
        String attemptKey="route_refresh_"+routine.id+"_"+signature;
        long now=System.currentTimeMillis();
        long last=RoutineStore.prefs(app).getLong(attemptKey,0);
        if(last<=now&&now-last<RETRY_MS)return false;
        RoutineStore.prefs(app).edit().putLong(attemptKey,now).apply();
        List<Callback> callbacks=new ArrayList<>();
        callbacks.add(callback);
        requests.put(key,callbacks);
        TmapWaypointRouteClient.searchForRefresh(routine,new TmapWaypointRouteClient.Callback(){
            @Override public void success(List<TransitRoute> routes,int calls){
                boolean saved=false;
                if(account.equals(Session.accountId(app))){
                    Routine latest=RoutineStore.read(app,routine.id);
                    if(signature.equals(latest.selectedRouteSignature)
                            &&fingerprint.equals(TransitRouteCache.fingerprint(latest))){
                        int match=RouteSignature.match(routes,signature);
                        if(match>=0){
                            saved=TransitRouteCache.save(app,latest,Collections.singletonList(routes.get(match)),0);
                            if(saved&&ReminderScheduler.selected(app,latest.id)){
                                ReminderScheduler.schedule(app,latest);
                                WeatherDepartureJob.schedule(app);
                                AutoCommute.resume(app,false);
                            }
                        }
                    }
                }
                notifyFinished(key,saved);
            }
            @Override public void error(String message){notifyFinished(key,false);}
        });
        return true;
    }

    private static void notifyFinished(String key,boolean saved){
        List<Callback> callbacks=requests.remove(key);
        for(Callback pending:callbacks)pending.finished(saved);
    }
}
