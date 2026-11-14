package com.example.mycommute.engine;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.Waypoint;
import java.util.*;
public final class TripTimeline {
 public static String expectedArrival(Routine r,int duration){
  if(!r.isConfigured()||duration<1)return "미정";
  Calendar c=PlanSchedule.arrivalOn(Calendar.getInstance(),r.arrivalTime);c.add(Calendar.MINUTE,-r.marginMinutes);return PlanSchedule.time(c);
 }
 public static List<String> steps(Routine r){
  List<String> steps=new ArrayList<>();steps.add(r.plannedDeparture(r.estimatedMinutes)+" · "+r.startName+" 출발 계획");
  if(!r.boardingStopId.isEmpty())steps.add(r.boardingRoute+" 탑승 · 실시간 도착 미연동");
  for(Waypoint w:r.waypoints)if(w.category.equals("경유"))steps.add("경유 · "+w.name);
  steps.add(r.arrivalTime+"까지 · "+r.endName+" 도착 목표");return steps;
 }
}
