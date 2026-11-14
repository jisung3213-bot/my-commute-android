package com.example.mycommute.engine;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class PlanScheduleTest {
 private Calendar at(int day,int hour,int minute){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));c.clear();c.set(2026,Calendar.NOVEMBER,day,hour,minute);return c;}
 @Test public void slowerRouteMovesDepartureEarlierNotArrival(){
  assertEquals("08:05",PlanSchedule.time(PlanSchedule.departureOn(at(16,0,0),"09:00",45,10)));
  assertEquals("07:55",PlanSchedule.time(PlanSchedule.departureOn(at(16,0,0),"09:00",55,10)));
 }
 @Test public void overnightTripRemainsActiveAfterMidnight(){assertTrue(PlanSchedule.active(at(17,0,10),"00:35",45,0,"3"));}
 @Test public void overnightTripStartsOnPreviousWeekday(){assertTrue(PlanSchedule.active(at(16,23,55),"00:35",45,0,"3"));}
 @Test public void wrongArrivalWeekdayNotActive(){assertFalse(PlanSchedule.active(at(17,0,10),"00:35",45,0,"2"));}
 @Test public void reminderForTomorrowMayFireToday(){assertEquals(at(16,23,35).getTimeInMillis(),PlanSchedule.nextAlert(at(16,23,0),"00:35",45,0,15,"3"));}
 @Test public void pastReminderMovesToNextSelectedWeek(){assertEquals(at(23,23,35).getTimeInMillis(),PlanSchedule.nextAlert(at(16,23,40),"00:35",45,0,15,"3"));}
 @Test public void noWeekdaysDoesNotSchedule(){assertEquals(-1,PlanSchedule.nextAlert(at(16,0,0),"09:00",45,10,15,""));}
}
