package com.example.mycommute.engine;

import com.example.mycommute.model.Routine;
import java.util.Calendar;
import java.util.TimeZone;

/** Local Seoul schedule for current/next commute; not a live ETA. */
public final class CommuteWindow {
    private static final TimeZone SEOUL=TimeZone.getTimeZone("Asia/Seoul");
    public static final long BEFORE_MS=150L*60_000L;
    public static final long AFTER_MS=75L*60_000L;
    private CommuteWindow(){}

    /** Closest scheduled target, including a recently passed target. */
    public static long nearestTarget(Routine routine,long now) {
        if(routine==null||routine.arrivalTime==null||
                !routine.arrivalTime.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))return -1;
        String[] parts=routine.arrivalTime.split(":");
        Calendar base=Calendar.getInstance(SEOUL);
        base.setTimeInMillis(now);
        base.set(Calendar.HOUR_OF_DAY,Integer.parseInt(parts[0]));
        base.set(Calendar.MINUTE,Integer.parseInt(parts[1]));
        base.set(Calendar.SECOND,0);
        base.set(Calendar.MILLISECOND,0);
        long nearest=-1,distance=Long.MAX_VALUE;
        for(int day=-1;day<=7;day++) {
            Calendar candidate=(Calendar)base.clone();
            candidate.add(Calendar.DAY_OF_YEAR,day);
            if(!routine.runsOn(candidate.get(Calendar.DAY_OF_WEEK)))continue;
            long ts=candidate.getTimeInMillis();
            long d=Math.abs(ts-now);
            if(d<distance){nearest=ts;distance=d;}
        }
        return nearest;
    }

    public static boolean active(Routine r,long now) {
        long target=nearestTarget(r,now);
        return target>0&&now>=target-BEFORE_MS&&now<=target+AFTER_MS;
    }
}
