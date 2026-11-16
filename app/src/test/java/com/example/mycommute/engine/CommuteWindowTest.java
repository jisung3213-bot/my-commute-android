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
}
