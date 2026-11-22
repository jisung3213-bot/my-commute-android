package com.example.mycommute.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.mycommute.data.AlarmLogRepository;
import com.example.mycommute.data.AlarmSettingsStore;
import com.example.mycommute.data.ClockPreference;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.engine.PlanSchedule;
import com.example.mycommute.engine.CommuteWindow;
import com.example.mycommute.model.Routine;
import java.util.Calendar;
import java.util.function.Supplier;

/** Scheduled events must remain local, never requiring a live API on wakeup. */
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null||!Session.signedIn(c)||!
                Session.accountId(c).equals(intent.getStringExtra("account")))return;
        String id=intent.getStringExtra("routineId");
        String type=intent.getStringExtra("type");
        if(id==null||type==null)return;
        Routine r=RoutineStore.read(c,id);
        if(!r.isConfigured()||!AlarmSettingsStore.enabled(c,type)||
                !ReminderScheduler.selected(c,id))return;
        int duration=ReminderScheduler.effectiveDurationMinutes(c,r);
        if(duration<1)return;
        ReminderScheduler.rescheduleEvent(c,id,type);
        long now=System.currentTimeMillis();
        long departure=nextRelevantDeparture(r,now,duration);
        if(departure<=0)return;
        long target=departure+((long)duration+r.marginMinutes)*60_000L;
        boolean checkRequired=AutoCommute.enabled(c);
        if(checkRequired&&!AutoCommute.startVerified(c,r,target))return;
        boolean beforeDeparture=AlarmSettingsStore.PREPARE.equals(type)||AlarmSettingsStore.WEATHER.equals(type);
        int lead=AlarmSettingsStore.prepareMinutes(c);
        long expected=departure-(beforeDeparture?lead*60_000L:0);
        // If delayed significantly or a previously scheduled event was rescheduled,
        // never fire stale events as though current.
        if(now<expected-90_000L||now-expected>10*60_000L)return;
        if(checkRequired&&beforeDeparture){
            if(now>=departure)return;
            lead=(int)((departure-now+59_999L)/60_000L);
        }
        deliver(c,r,target,departure,type,lead,now,()->goAsync()::finish);
    }

    /** If a preparation alarm was held while checking location, send what is still due. */
    static void onStartVerified(Context c,Routine r,long target,long departure){
        long now=System.currentTimeMillis();
        int lead=AlarmSettingsStore.prepareMinutes(c);
        if(now>departure+1000L||now<departure-lead*60_000L)return;
        boolean prepare=AlarmSettingsStore.enabled(c,AlarmSettingsStore.PREPARE);
        String type=now>=departure?AlarmSettingsStore.DEPARTURE
                :prepare?AlarmSettingsStore.PREPARE:AlarmSettingsStore.WEATHER;
        if(!AlarmSettingsStore.enabled(c,type))return;
        int remaining=(int)((departure-now+59_999L)/60_000L);
        deliver(c,r,target,departure,type,remaining,now,()->null);
    }

    private static void deliver(Context c,Routine r,long target,long departure,String type,
            int lead,long now,Supplier<Runnable> logged){
        String id=r.id;
        String trip=AlertLedger.tripKey(target);
        if(AlertLedger.finished(c,id,trip))return;
        AlertCopy.Message notice;
        String forecast=forecast(c,id,now);
        if(AlarmSettingsStore.WEATHER.equals(type)){
            if(forecast.isEmpty()||AlarmSettingsStore.enabled(c,AlarmSettingsStore.PREPARE))return;
            notice=AlertCopy.weather(forecast);
        }else if(AlarmSettingsStore.PREPARE.equals(type)){
            notice=AlertCopy.prepare(lead,ClockPreference.time(c,departure),r.name,forecast);
        }else{
            notice=AlertCopy.departure(ClockPreference.time(c,r.arrivalTime),r.name);
        }
        if(!AlertLedger.tryClaim(c,id,trip,type,"scheduled",now,Long.MAX_VALUE))return;
        boolean requested=NotificationHelper.show(c,notice.title,notice.body,
                (id+"|"+type+"|"+trip).hashCode(),id);
        AlarmLogRepository.write(c,type+":"+id,requested?notice.title:"알림 차단됨",
                requested?notice.body:"기기 알림 권한 확인 필요 · "+notice.body,true,logged.get());
    }

    private static String forecast(Context c,String id,long now){
        if(!AlarmSettingsStore.enabled(c,AlarmSettingsStore.WEATHER))return "";
        long at=RoutineStore.prefs(c).getLong("alert_weather_at_"+id,0);
        return at>0&&now>=at&&now-at<=60*60_000L
                ?RoutineStore.prefs(c).getString("alert_weather_text_"+id,""):"";
    }

    /** Finds closest current day's departure; only used to validate fired intent. */
    private static long nextRelevantDeparture(Routine r,long now,int duration){
        Calendar base=CommuteWindow.seoulCalendar();
        base.setTimeInMillis(now);
        long best=-1;
        long gap=Long.MAX_VALUE;
        for(int offset=-1;offset<=1;offset++){
            Calendar day=(Calendar)base.clone();
            day.add(Calendar.DAY_OF_YEAR,offset);
            if(!r.runsOn(day.get(Calendar.DAY_OF_WEEK)))continue;
            long candidate=PlanSchedule.departureOn(day,r.arrivalTime,duration,
                    r.marginMinutes).getTimeInMillis();
            long delta=Math.abs(candidate-now);
            if(delta<gap){gap=delta;best=candidate;}
        }
        return best;
    }
}
