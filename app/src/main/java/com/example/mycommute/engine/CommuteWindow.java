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

    public static Calendar seoulCalendar() { return Calendar.getInstance(SEOUL); }

    public static final int TRACKING_LEAD_MINUTES=30;
    public static final long TRACKING_GRACE_MS=30L*60_000L;
    public static final int START_RADIUS_METERS=500;

    public static long plannedDeparture(Routine routine,int duration,long target) {
        return target-(duration+(long)routine.marginMinutes)*60_000L;
    }

    public static boolean inStartCheck(long departure,long now) {
        return departure>0&&now>=departure-TRACKING_LEAD_MINUTES*60_000L&&now<=departure;
    }

    public static boolean qualifiesAtStart(long departure,long now,long fixTime,
            float accuracy,float distance) {
        return inStartCheck(departure,now)&&inStartCheck(departure,fixTime)
                &&fixTime<=now&&now-fixTime<=60_000L
                &&Float.isFinite(accuracy)&&accuracy>0&&accuracy<=100
                &&Float.isFinite(distance)&&distance>=0&&distance<=START_RADIUS_METERS;
    }

    /** First target strictly after now, using the configured arrival weekdays. */
    public static long nextArrival(Routine routine,long now) {
        if(!routine.isConfigured())return Long.MAX_VALUE;
        Calendar day=seoulCalendar();
        day.setTimeInMillis(now);
        for(int offset=0;offset<=7;offset++,day.add(Calendar.DAY_OF_YEAR,1)){
            if(!routine.runsOn(day.get(Calendar.DAY_OF_WEEK)))continue;
            long candidate=PlanSchedule.arrivalOn(day,routine.arrivalTime).getTimeInMillis();
            if(candidate>now)return candidate;
        }
        return Long.MAX_VALUE;
    }

    public static long nextTrackingStart(Routine routine,int duration,long now) {
        return nextDeparture(routine,duration,TRACKING_LEAD_MINUTES,now);
    }

    public static long nextDeparture(Routine routine,int duration,int lead,long now) {
        if(duration<=0)return -1;
        Calendar calendar=seoulCalendar();
        calendar.setTimeInMillis(now);
        return PlanSchedule.nextAlert(calendar,routine.arrivalTime,duration,
                routine.marginMinutes,lead,routine.days);
    }

    /** Arrival timestamp of the tracking window that contains now. */
    public static long trackingTarget(Routine routine,int duration,long now) {
        if(duration<=0||!PlanSchedule.validTime(routine.arrivalTime))return -1;
        Calendar calendar=seoulCalendar();
        calendar.setTimeInMillis(now);
        long lead=(duration+(long)routine.marginMinutes+TRACKING_LEAD_MINUTES)*60_000L;
        for(int offset=-1;offset<=1;offset++) {
            Calendar day=(Calendar)calendar.clone();
            day.add(Calendar.DAY_OF_YEAR,offset);
            if(!routine.runsOn(day.get(Calendar.DAY_OF_WEEK)))continue;
            long target=PlanSchedule.arrivalOn(day,routine.arrivalTime).getTimeInMillis();
            if(now>=target-lead&&now<target+TRACKING_GRACE_MS)return target;
        }
        return -1;
    }

    /** Closest scheduled target, including a recently passed target. */
    public static long nearestTarget(Routine routine,long now) {
        if(routine==null||routine.arrivalTime==null||
                !routine.arrivalTime.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))return -1;
        String[] parts=routine.arrivalTime.split(":");
        Calendar base=seoulCalendar();
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
