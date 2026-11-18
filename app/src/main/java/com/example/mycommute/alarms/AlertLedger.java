package com.example.mycommute.alarms;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.mycommute.data.RoutineStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** One event per user/routine/trip/type/leg; prevent spamming after network refresh. */
public final class AlertLedger {
    private AlertLedger(){}
    private static final TimeZone SEOUL=TimeZone.getTimeZone("Asia/Seoul");

    public static String tripKey(long targetTime){
        SimpleDateFormat date=new SimpleDateFormat("yyyyMMddHHmm",Locale.US);
        date.setTimeZone(SEOUL);
        return date.format(new Date(targetTime));
    }

    public static boolean tryClaim(Context context,String routineId,String trip,
                                   String type,String qualifier,long now,long cooldownMs){
        SharedPreferences pref=RoutineStore.prefs(context);
        String key="alert_seen_"+routineId+"_"+trip+"_"+type+"_"+qualifier;
        long previous=pref.getLong(key,0);
        if(previous>0&&now-previous<Math.max(0,cooldownMs))return false;
        // Exact once for normal events. A distinct qualifier indicates a distinct vehicle/leg.
        if(previous>0&&cooldownMs==Long.MAX_VALUE)return false;
        return pref.edit().putLong(key,now).commit();
    }

    public static boolean finished(Context context,String routineId,String trip){
        return RoutineStore.prefs(context).getBoolean("alert_finished_"+routineId+"_"+trip,false);
    }
    public static void finish(Context context,String routineId,String trip){
        RoutineStore.prefs(context).edit().putBoolean(
                "alert_finished_"+routineId+"_"+trip,true).apply();
    }
}
