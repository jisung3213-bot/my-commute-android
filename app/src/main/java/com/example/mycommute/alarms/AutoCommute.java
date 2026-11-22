package com.example.mycommute.alarms;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import com.example.mycommute.data.PinnedRouteRefresh;
import com.example.mycommute.data.RouteRefreshJob;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.engine.CommuteWindow;
import com.example.mycommute.location.TrackingService;
import com.example.mycommute.model.Routine;

/** Wakes for the two default routines, independently of which route the user is viewing. */
public final class AutoCommute extends BroadcastReceiver {
    private static final int REFRESH_ONLY_DURATION=90;
    private static final String END_WINDOW="com.example.mycommute.END_WINDOW";

    public static boolean enabled(Context context){
        return RoutineStore.prefs(context).getBoolean("auto_commute_tracking",false);
    }

    public static void setEnabled(Context context,boolean enabled){
        RoutineStore.prefs(context).edit().putBoolean("auto_commute_tracking",enabled).apply();
        if(!enabled)TrackingService.stopAutomatic(context);
        schedule(context);
    }

    public static boolean hasLocation(Context context){
        return allowed(context,Manifest.permission.ACCESS_FINE_LOCATION)
                ||allowed(context,Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public static boolean hasBackgroundLocation(Context context){
        return Build.VERSION.SDK_INT<29||allowed(context,Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    private static boolean allowed(Context context,String permission){
        return ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED;
    }

    public static void schedule(Context context){schedule(context,true);}

    private static void schedule(Context context,boolean catchUp){
        AlarmManager manager=context.getSystemService(AlarmManager.class);
        if(manager==null||!Session.signedIn(context)||AlertLabMode.enabled(context))return;
        long now=System.currentTimeMillis();
        for(String direction:RoutineStore.IDS){
            PendingIntent alarm=alarm(context,direction);
            manager.cancel(alarm);
            Routine routine=pinned(context,direction);
            if(!routine.isConfigured())continue;
            boolean refresh=PinnedRouteRefresh.canRefresh(routine);
            int duration=ReminderScheduler.durationMinutes(context,routine);
            if(!refresh&&(!enabled(context)||duration<=0))continue;
            int planningDuration=duration>0?duration:REFRESH_ONLY_DURATION;
            long when=CommuteWindow.nextTrackingStart(routine,planningDuration,now);
            long target=CommuteWindow.trackingTarget(routine,planningDuration,now);
            if(catchUp&&target>0&&RoutineStore.prefs(context).getLong("auto_wake_"+direction,0)!=target)
                when=now+1000L;
            if(when>now)ReminderScheduler.setWakeup(manager,when,alarm);
        }
    }

    public static void cancel(Context context){
        AlarmManager manager=context.getSystemService(AlarmManager.class);
        if(manager!=null)for(String direction:RoutineStore.IDS)manager.cancel(alarm(context,direction));
        RouteRefreshJob.cancel(context);
        cancelEnd(context);
    }

    public static void restoreAfterBoot(Context context){
        RoutineStore.prefs(context).edit().remove("auto_wake_"+RoutineStore.OUTBOUND)
                .remove("auto_wake_"+RoutineStore.RETURN).apply();
    }

    public static void scheduleEnd(Context context,long target,long end){
        AlarmManager manager=context.getSystemService(AlarmManager.class);
        if(manager!=null)ReminderScheduler.setWakeup(manager,end,endAlarm(context,target));
    }

    public static void cancelEnd(Context context){
        AlarmManager manager=context.getSystemService(AlarmManager.class);
        if(manager!=null)manager.cancel(endAlarm(context,0));
    }

    private static PendingIntent endAlarm(Context context,long target){
        Intent intent=new Intent(context,AutoCommute.class).setAction(END_WINDOW)
                .putExtra("account",Session.accountId(context)).putExtra("target",target);
        return PendingIntent.getBroadcast(context,0,intent,
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||AlertLabMode.enabled(context)||!Session.signedIn(context)
                ||!Session.accountId(context).equals(intent.getStringExtra("account")))return;
        if(END_WINDOW.equals(intent.getAction())){
            TrackingService.stopExpired(intent.getLongExtra("target",0));
            return;
        }
        String direction=intent.getStringExtra("direction");
        if(!RoutineStore.OUTBOUND.equals(direction)&&!RoutineStore.RETURN.equals(direction))return;
        Routine routine=pinned(context,direction);
        int duration=ReminderScheduler.durationMinutes(context,routine);
        long target=CommuteWindow.trackingTarget(routine,duration>0?duration:REFRESH_ONLY_DURATION,
                System.currentTimeMillis());
        if(target>0){
            RoutineStore.prefs(context).edit().putLong("auto_wake_"+direction,target).apply();
            if(PinnedRouteRefresh.canRefresh(routine))
                RouteRefreshJob.enqueue(context,direction,target+CommuteWindow.TRACKING_GRACE_MS);
        }
        schedule(context,false);
        resume(context,false);
    }

    public static void resume(Context context,boolean visible){
        if(!Session.signedIn(context)||!enabled(context)||AlertLabMode.enabled(context)
                ||TrackingService.running()||!hasLocation(context)
                ||(!visible&&!hasBackgroundLocation(context)))return;
        LocationManager manager=context.getSystemService(LocationManager.class);
        if(manager==null||!LocationManagerCompat.isLocationEnabled(manager))return;
        long now=System.currentTimeMillis();
        long target=Long.MAX_VALUE;
        long departure=-1;
        Routine chosen=null;
        for(String direction:RoutineStore.IDS){
            Routine routine=pinned(context,direction);
            if(!routine.isConfigured()||!routine.validCoordinates())continue;
            int duration=ReminderScheduler.durationMinutes(context,routine);
            long candidate=CommuteWindow.trackingTarget(routine,duration,now);
            if(candidate<=0||candidate>=target||wasStopped(context,routine.id,candidate))continue;
            long planned=CommuteWindow.plannedDeparture(routine,duration,candidate);
            if(!startVerified(context,routine,candidate)&&!CommuteWindow.inStartCheck(planned,now))continue;
            chosen=routine;
            target=candidate;
            departure=planned;
        }
        if(chosen==null)return;
        try {
            ContextCompat.startForegroundService(context,new Intent(context,TrackingService.class)
                    .putExtra("routineId",chosen.id).putExtra("target",target)
                    .putExtra("departure",departure));
        }catch(SecurityException|IllegalStateException error){
            Log.w("AutoCommute","Scheduled location could not start",error);
        }
    }

    public static boolean startVerified(Context context,Routine routine,long target){
        return target>0&&startKey(routine,target).equals(RoutineStore.prefs(context)
                .getString("auto_origin_"+routine.id,""));
    }

    public static void verifyStart(Context context,Routine routine,long target,long departure){
        RoutineStore.prefs(context).edit().putString("auto_origin_"+routine.id,startKey(routine,target)).apply();
        ReminderReceiver.onStartVerified(context,routine,target,departure);
    }

    private static String startKey(Routine routine,long target){
        return target+"|"+routine.startLat+"|"+routine.startLon;
    }

    public static boolean wasStopped(Context context,String id,long target){
        return RoutineStore.prefs(context).getLong("auto_stopped_"+id,0)==target
                ||AlertLedger.finished(context,id,AlertLedger.tripKey(target));
    }

    private static Routine pinned(Context context,String direction){
        return RoutineStore.read(context,RoutineStore.defaultId(context,direction));
    }

    private static PendingIntent alarm(Context context,String direction){
        String account=Session.accountId(context);
        Intent intent=new Intent(context,AutoCommute.class)
                .setData(new Uri.Builder().scheme("commute").authority("auto")
                        .appendPath(account).appendPath(direction).build())
                .putExtra("account",account).putExtra("direction",direction);
        return PendingIntent.getBroadcast(context,0,intent,
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
