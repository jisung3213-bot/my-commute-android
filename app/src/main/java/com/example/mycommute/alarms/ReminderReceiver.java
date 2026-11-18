package com.example.mycommute.alarms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.example.mycommute.data.AlarmLogRepository;
import com.example.mycommute.data.AlarmSettingsStore;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.engine.PlanSchedule;
import com.example.mycommute.model.Routine;
import java.util.Calendar;

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
        String trip=AlertLedger.tripKey(target);
        if(AlertLedger.finished(c,id,trip))return;
        long expected=departure-((AlarmSettingsStore.PREPARE.equals(type)
                ||AlarmSettingsStore.WEATHER.equals(type))
                ?AlarmSettingsStore.prepareMinutes(c)*60_000L:0);
        // If delayed significantly or a previously scheduled event was rescheduled,
        // never fire stale events as though current.
        if(now<expected-90_000L||now-expected>10*60_000L)return;
        if(AlarmSettingsStore.WEATHER.equals(type)){
            String weather=RoutineStore.prefs(c).getString("alert_weather_text_"+id,"");
            long at=RoutineStore.prefs(c).getLong("alert_weather_at_"+id,0);
            if(weather.isEmpty()||at<=0||now-at>60*60_000L)return;
            // If preparation alert is enabled, it already includes this forecast.
            // Otherwise weather remains independently enabled.
            if(AlarmSettingsStore.enabled(c,AlarmSettingsStore.PREPARE))return;
            if(!AlertLedger.tryClaim(c,id,trip,type,"scheduled",now,Long.MAX_VALUE))return;
            boolean sent=NotificationHelper.show(c,"날씨 주의 · "+r.name,
                    weather,(id+"|"+type+"|"+trip).hashCode(),id);
            PendingResult async=goAsync();
            AlarmLogRepository.write(c,type+":"+id,sent?"날씨 주의":"알림 차단됨",
                    weather,true,async::finish);
            return;
        }
        if(!AlertLedger.tryClaim(c,id,trip,type,"scheduled",now,Long.MAX_VALUE))return;

        String title=AlarmSettingsStore.PREPARE.equals(type)
                ?"출발 준비 · "+r.name:"출발 권장 · "+r.name;
        String message=AlarmSettingsStore.PREPARE.equals(type)
                ?"약 "+AlarmSettingsStore.prepareMinutes(c)+"분 뒤 "+format(departure)+" 출발 예정"
                :format(departure)+" 출발 권장 · 목표 도착 "+r.arrivalTime;
        if(AlarmSettingsStore.PREPARE.equals(type)&&AlarmSettingsStore.enabled(c,AlarmSettingsStore.WEATHER)){
            String weather=RoutineStore.prefs(c).getString("alert_weather_text_"+id,"");
            long at=RoutineStore.prefs(c).getLong("alert_weather_at_"+id,0);
            if(at>0&&now-at<=60*60_000L&&!weather.isEmpty())
                message+=" · "+weather;
        }
        boolean requested=NotificationHelper.show(c,title,message,
                (id+"|"+type+"|"+trip).hashCode(),id);
        final String logTitle=requested?title:"알림 차단됨";
        final String logText=requested?message:"기기 알림 권한 확인 필요 · "+message;
        PendingResult pending=goAsync();
        AlarmLogRepository.write(c,type+":"+id,logTitle,logText,true,()->{
            pending.finish();
        });
    }

    /** Finds closest current day's departure; only used to validate fired intent. */
    private static long nextRelevantDeparture(Routine r,long now,int duration){
        Calendar base=Calendar.getInstance();
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
    private static String format(long millis){
        java.text.SimpleDateFormat formatter=new java.text.SimpleDateFormat("HH:mm",
                java.util.Locale.KOREA);
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Seoul"));
        return formatter.format(new java.util.Date(millis));
    }
}
