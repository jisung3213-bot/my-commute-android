package com.example.mycommute.alarms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import com.example.mycommute.data.AlarmSettingsStore;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.data.TransitRouteCache;
import com.example.mycommute.engine.PlanSchedule;
import com.example.mycommute.engine.RoutePlanner;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitRoute;
import java.util.Calendar;
import java.util.List;

/** Per-named-routine two-stage scheduled alerts. No internet calls in alarm receivers. */
public final class ReminderScheduler {
    private static final String[] SCHEDULED={
            AlarmSettingsStore.PREPARE,AlarmSettingsStore.DEPARTURE,
            AlarmSettingsStore.WEATHER};
    private ReminderScheduler(){}

    public static void scheduleCurrent(Context c){
        if(!Session.signedIn(c))return;
        for(String direction:RoutineStore.IDS){
            for(Routine routine:RoutineStore.list(c,direction))cancel(c,routine);
            Routine selected=selectedForDirection(c,direction);
            if(selected!=null)schedule(c,selected);
        }
    }

    private static Routine selectedForDirection(Context c,String direction){
        String id=RoutineStore.prefs(c).getString("selected_profile_"+direction,"");
        for(Routine routine:RoutineStore.list(c,direction))
            if(routine.id.equals(id))return routine;
        String fallback=RoutineStore.defaultId(c,direction);
        for(Routine routine:RoutineStore.list(c,direction))
            if(routine.id.equals(fallback))return routine;
        return null;
    }

    public static boolean selected(Context c,String id){
        for(String direction:RoutineStore.IDS){
            Routine r=selectedForDirection(c,direction);
            if(r!=null&&r.id.equals(id))return true;
        }
        return false;
    }

    public static void cancelCurrent(Context c){
        for(String direction:RoutineStore.IDS)
            for(Routine routine:RoutineStore.list(c,direction))cancel(c,routine);
    }

    public static void cancel(Context c,Routine r){
        if(r==null||r.id==null)return;
        AlarmManager manager=c.getSystemService(AlarmManager.class);
        if(manager==null)return;
        for(String event:SCHEDULED)manager.cancel(pending(c,r.id,event,null));
        // Clear legacy single-event pending alarm used before this version.
        Intent legacy=new Intent(c,ReminderReceiver.class);
        legacy.setData(new Uri.Builder().scheme("commute").authority("alarm")
                .appendPath(Session.accountId(c)).appendPath(r.id).build());
        manager.cancel(PendingIntent.getBroadcast(c,0,legacy,
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
    }

    public static void rescheduleOne(Context c,String id){
        if(id!=null&&!id.isEmpty())schedule(c,RoutineStore.read(c,id));
    }
    /** Reschedules only the event that just fired; preserves other same-time events. */
    public static void rescheduleEvent(Context c,String id,String type){
        if(id==null||type==null)return;
        Routine routine=RoutineStore.read(c,id);
        if(!routine.isConfigured())return;
        AlarmManager manager=c.getSystemService(AlarmManager.class);
        if(manager!=null)manager.cancel(pending(c,id,type,null));
        if(Session.signedIn(c)&&selected(c,id)&&AlarmSettingsStore.enabled(c,type))
            scheduleOne(c,routine,type,effectiveDurationMinutes(c,routine));
    }

    public static void schedule(Context c,Routine routine){
        cancel(c,routine);
        if(!Session.signedIn(c)||!AlarmSettingsStore.overall(c)
                ||routine==null||!routine.isConfigured())return;
        int duration=effectiveDurationMinutes(c,routine);
        if(duration<1)return;
        for(String type:SCHEDULED)
            if(AlarmSettingsStore.enabled(c,type))
                scheduleOne(c,routine,type,duration);
    }

    private static void scheduleOne(Context c,Routine routine,String type,int duration){
        if(duration<1)return;
        int lead=(AlarmSettingsStore.PREPARE.equals(type)||
                AlarmSettingsStore.WEATHER.equals(type))
                ?AlarmSettingsStore.prepareMinutes(c):0;
        long when=PlanSchedule.nextAlert(Calendar.getInstance(),routine.arrivalTime,
                duration,routine.marginMinutes,lead,routine.days);
        if(when<=System.currentTimeMillis())return;
        AlarmManager manager=c.getSystemService(AlarmManager.class);
        if(manager==null)return;
        PendingIntent intent=pending(c,routine.id,type,null);
        try{
            if(Build.VERSION.SDK_INT<31||manager.canScheduleExactAlarms())
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,intent);
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,intent);
        }catch(SecurityException e){
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,intent);
        }
    }

    /**
     * Use a fresh, user-selected route cache for API routes; no fabricated ETA.
     * For a legacy manual route, retain the old offline estimate.
     */
    public static int durationMinutes(Context c,Routine r){
        if(r.apiBased){
            TransitRouteCache.Snapshot snapshot=TransitRouteCache.load(c,r);
            if(snapshot==null)return r.estimatedMinutes>0?r.estimatedMinutes:-1;
            if(snapshot.selected<0||snapshot.selected>=snapshot.routes.size())return -1;
            TransitRoute selected=snapshot.routes.get(snapshot.selected);
            return selected.totalSeconds>0?TransitRoute.minutes(selected.totalSeconds):-1;
        }
        RoutePlanner.Plan plan=RoutePlanner.select(r);
        return plan.available?plan.durationMinutes:-1;
    }

    /** Current-position estimate only while recent verified walking data exists. */
    public static int effectiveDurationMinutes(Context c,Routine routine){
        int duration=durationMinutes(c,routine);
        if(duration<1)return duration;
        long dynamicAt=RoutineStore.prefs(c).getLong("dynamic_plan_at_"+routine.id,0);
        int dynamic=RoutineStore.prefs(c).getInt("dynamic_plan_min_"+routine.id,-1);
        if(dynamicAt>0&&System.currentTimeMillis()-dynamicAt<=10*60_000L&&
                dynamic>0&&dynamic<720)return dynamic;
        return duration;
    }

    /** Fresh TMAP walking duration from current GPS to first boarding stop. */
    public static void updateFromPosition(Context c,Routine routine,TransitRoute route,
                                           int boardingLeg,int walkingSeconds){
        if(routine==null||route==null||boardingLeg<0||walkingSeconds<0)return;
        int oldWalk=0;
        for(int i=0;i<boardingLeg;i++)
            if("WALK".equals(route.legs.get(i).mode))oldWalk+=route.legs.get(i).seconds;
        int adjusted=Math.max(1,TransitRoute.minutes(
                Math.max(0,route.totalSeconds-oldWalk)+walkingSeconds));
        if(Math.abs(adjusted-durationMinutes(c,routine))<2)return;
        RoutineStore.prefs(c).edit().putInt("dynamic_plan_min_"+routine.id,adjusted)
                .putLong("dynamic_plan_at_"+routine.id,System.currentTimeMillis()).apply();
        schedule(c,routine);
    }

    public static void clearDynamic(Context c,String id){
        if(id==null)return;
        RoutineStore.prefs(c).edit().remove("dynamic_plan_min_"+id)
                .remove("dynamic_plan_at_"+id).apply();
    }

    private static PendingIntent pending(Context c,String routineId,String type,String trip){
        Intent intent=new Intent(c,ReminderReceiver.class);
        intent.setData(new Uri.Builder().scheme("commute").authority("scheduled")
                .appendPath(Session.accountId(c)).appendPath(routineId)
                .appendPath(type).build());
        intent.putExtra("account",Session.accountId(c));
        intent.putExtra("routineId",routineId);
        intent.putExtra("type",type);
        return PendingIntent.getBroadcast(c,0,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
