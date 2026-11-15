package com.example.mycommute.alarms;
import android.content.*;
import com.example.mycommute.data.*;
import com.example.mycommute.engine.RoutePlanner;
import com.example.mycommute.model.Routine;
public class ReminderReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
  if(!Session.signedIn(c)||!Session.accountId(c).equals(i.getStringExtra("account")))return;
  String id=i.getStringExtra("routineId");if(!"outbound".equals(id)&&!"return".equals(id))return;
  Routine r=RoutineStore.read(c,id);if(!r.isConfigured())return;
  var plan=RoutePlanner.select(r);
  if(!plan.available)return;
  String title="출발 준비 · "+r.name;
  String message=r.plannedDeparture(plan.durationMinutes)+" 출발 권장 / "+r.arrivalTime+" 도착 목표 · "+plan.note;
  boolean requested=NotificationHelper.show(c,title,message,(int)(System.currentTimeMillis()&0x7fffffff),id);
  ReminderScheduler.rescheduleOne(c,id);
  PendingResult pending=goAsync();
  AlarmLogRepository.write(c,"departure:"+id,requested?title:"알림 차단됨",requested?message:"시스템 알림/채널 설정을 확인하세요. "+message,true,pending::finish);
 }
}
