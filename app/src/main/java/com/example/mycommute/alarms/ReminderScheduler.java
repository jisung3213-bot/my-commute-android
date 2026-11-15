package com.example.mycommute.alarms;
import android.app.*;
import android.content.*;
import android.os.Build;
import android.net.Uri;
import com.example.mycommute.data.*;
import com.example.mycommute.engine.*;
import com.example.mycommute.model.Routine;
import java.util.Calendar;
public final class ReminderScheduler {
 public static void scheduleCurrent(Context c){
  if(!Session.signedIn(c))return;
  for(String id:RoutineStore.IDS)schedule(c,RoutineStore.read(c,id));
 }
 public static void cancelCurrent(Context c){for(String id:RoutineStore.IDS)cancel(c,RoutineStore.read(c,id));}
 public static void cancel(Context c,Routine r){
  AlarmManager m=c.getSystemService(AlarmManager.class);if(m==null)return;
  m.cancel(pending(c,r));
  // Remove legacy draft convenience alarms if such data was imported.
  for(int i=0;i<r.waypoints.size();i++){
   String type="point_"+i;Intent old=new Intent(c,ReminderReceiver.class);
   m.cancel(PendingIntent.getBroadcast(c,(type.hashCode()&0x7fffffff)+1,old,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
  }
 }
 public static void rescheduleOne(Context c,String id){schedule(c,RoutineStore.read(c,id));}
 private static void schedule(Context c,Routine r){
  cancel(c,r);if(!r.isConfigured())return;
  RoutePlanner.Plan plan=RoutePlanner.select(r);
  if(!plan.available)return;
  long when=PlanSchedule.nextAlert(Calendar.getInstance(),r.arrivalTime,plan.durationMinutes,r.marginMinutes,r.alertLeadMinutes,r.days);
  if(when<0)return;
  AlarmManager m=c.getSystemService(AlarmManager.class);if(m==null)return;
  try{
   if(Build.VERSION.SDK_INT<31||m.canScheduleExactAlarms())m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending(c,r));
   else m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending(c,r));
  }catch(SecurityException e){m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pending(c,r));}
 }
 private static PendingIntent pending(Context c,Routine r){
  Intent i=new Intent(c,ReminderReceiver.class);
  i.setData(new Uri.Builder().scheme("commute").authority("alarm").appendPath(Session.accountId(c)).appendPath(r.id).build());
  i.putExtra("account",Session.accountId(c));i.putExtra("routineId",r.id);
  return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 private ReminderScheduler(){}
}
