package com.example.mycommute.engine;

import com.example.mycommute.model.Routine;
import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class CommuteWindowTest {
    private static final TimeZone KST=TimeZone.getTimeZone("Asia/Seoul");
    private static long at(int year,int month,int day,int hour,int min) {
        Calendar c=Calendar.getInstance(KST);
        c.set(year,month-1,day,hour,min,0);c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
    @Test public void activeBeforeArrivalAndAfterShortDelay() {
        Routine r=new Routine();r.arrivalTime="09:00";r.days="2,3,4,5,6";
        assertTrue(CommuteWindow.active(r,at(2026,11,16,7,0)));
        assertTrue(CommuteWindow.active(r,at(2026,11,16,9,30)));
        assertFalse(CommuteWindow.active(r,at(2026,11,16,12,0)));
    }
    @Test public void ignoresUnscheduledWeekend() {
        Routine r=new Routine();r.arrivalTime="09:00";r.days="2,3,4,5,6";
        assertFalse(CommuteWindow.active(r,at(2026,11,15,9,0)));
    }
    @Test public void handlesMidnightCrossing() {
        Routine r=new Routine();r.arrivalTime="00:20";r.days="3";
        assertTrue(CommuteWindow.active(r,at(2026,11,16,23,40)));
        assertTrue(CommuteWindow.active(r,at(2026,11,17,0,40)));
    }

    @Test public void automaticTrackingStartsBeforeDepartureAndHasAHardEnd() {
        Routine routine=new Routine();routine.arrivalTime="09:00";routine.days="2";
        long target=at(2026,11,16,9,0);
        assertEquals(at(2026,11,16,8,0),
                CommuteWindow.nextTrackingStart(routine,20,at(2026,11,16,7,59)));
        assertEquals(-1,CommuteWindow.trackingTarget(routine,20,at(2026,11,16,7,59)));
        assertEquals(target,CommuteWindow.trackingTarget(routine,20,at(2026,11,16,8,0)));
        assertEquals(target,CommuteWindow.trackingTarget(routine,20,at(2026,11,16,9,29)));
        assertEquals(-1,CommuteWindow.trackingTarget(routine,20,at(2026,11,16,9,30)));
    }

    @Test public void automaticTrackingUsesTheArrivalWeekdayAcrossMidnight() {
        Routine routine=new Routine();routine.arrivalTime="00:20";routine.days="3";
        assertEquals(at(2026,11,16,23,20),
                CommuteWindow.nextTrackingStart(routine,20,at(2026,11,16,23,10)));
        assertEquals(at(2026,11,17,0,20),
                CommuteWindow.trackingTarget(routine,20,at(2026,11,16,23,20)));
        assertEquals(-1,CommuteWindow.trackingTarget(routine,20,at(2026,11,17,23,20)));
    }

    @Test public void acceptsFreshAccuratePositionsThroughDepartureWithinFiveHundredMeters() {
        long departure=at(2026,11,16,8,0);
        long start=departure-30*60_000L;
        assertTrue(CommuteWindow.qualifiesAtStart(departure,start,start,20,500));
        assertTrue(CommuteWindow.qualifiesAtStart(departure,departure,departure-1000,20,499));
        assertFalse(CommuteWindow.qualifiesAtStart(departure,departure,departure,20,501));
        assertFalse(CommuteWindow.qualifiesAtStart(departure,departure+1,departure,20,499));
    }

    @Test public void doesNotAcceptStalePriorOrInaccurateLocation() {
        long departure=at(2026,11,16,8,0);
        long start=departure-30*60_000L;
        assertFalse(CommuteWindow.qualifiesAtStart(departure,start,start-1,20,10));
        assertFalse(CommuteWindow.qualifiesAtStart(departure,departure,departure-60_001,20,10));
        assertFalse(CommuteWindow.qualifiesAtStart(departure,start,start,101,10));
        assertFalse(CommuteWindow.qualifiesAtStart(departure,start,start,Float.NaN,10));
    }

    @Test public void originPreviewUsesTheSameFreshnessAndRadiusAsScheduledTracking() {
        long now=at(2026,11,16,8,0);
        assertTrue(CommuteWindow.usableStartFix(now,now-60_000,100));
        assertFalse(CommuteWindow.usableStartFix(now,now-60_001,20));
        assertFalse(CommuteWindow.usableStartFix(now,now+1,20));
        assertFalse(CommuteWindow.usableStartFix(now,now,101));
        assertTrue(CommuteWindow.withinStartRadius(500));
        assertFalse(CommuteWindow.withinStartRadius(500.1f));
        assertFalse(CommuteWindow.withinStartRadius(Float.NaN));
    }

    @Test public void nextArrivalSwapsDirectionAtTheTwoTargetTimes() {
        Routine outbound=configuredAt("09:00");
        Routine inbound=configuredAt("16:00");
        long morning=at(2026,11,16,9,0);
        long afternoon=at(2026,11,16,16,0);
        assertTrue(CommuteWindow.nextArrival(outbound,morning-1)<CommuteWindow.nextArrival(inbound,morning-1));
        assertTrue(CommuteWindow.nextArrival(inbound,morning)<CommuteWindow.nextArrival(outbound,morning));
        assertTrue(CommuteWindow.nextArrival(outbound,afternoon)<CommuteWindow.nextArrival(inbound,afternoon));
        assertEquals(Long.MAX_VALUE,CommuteWindow.nextArrival(new Routine(),morning));
    }

    private static Routine configuredAt(String time){
        Routine routine=new Routine();
        routine.apiBased=true;routine.startName="출발지";routine.endName="도착지";
        routine.days="2,3,4,5,6";routine.arrivalTime=time;
        return routine;
    }
}
