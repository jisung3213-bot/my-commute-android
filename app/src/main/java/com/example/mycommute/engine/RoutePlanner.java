package com.example.mycommute.engine;
import com.example.mycommute.model.Routine;
import java.util.Collections;

public final class RoutePlanner {
 public static final class Plan {
  public final String mode,note;
  public final int durationMinutes,walkMinutes,advanceAlertMinutes;
  public final boolean available;
  Plan(String m,String n,int d,int w,int a,boolean ok){mode=m;note=n;durationMinutes=d;walkMinutes=w;advanceAlertMinutes=a;available=ok;}
 }
 // Until a provider is connected, weather is UNKNOWN rather than assumed clear.
 public static Plan select(Routine r){
  return select(r,new WeatherRouteRanker.Conditions(false,false,false));
 }
 public static Plan select(Routine r,WeatherRouteRanker.Conditions weather){
  String source=weather.known ? "기상정보 반영: " : "기상정보 미연동 · 날씨 상태 미확인. ";
  if(r.estimatedMinutes<1||r.normalWalkMinutes<0||r.normalWalkMinutes>r.estimatedMinutes)
   return new Plan(r.transportMode,"이동시간을 확인하세요",0,0,0,false);
  WeatherRouteRanker.Candidate route=new WeatherRouteRanker.Candidate(
   "user-estimate",r.transportMode,r.estimatedMinutes,r.normalWalkMinutes,0,0,r.transportMode.equals("자전거"));
  WeatherRouteRanker.Candidate chosen=WeatherRouteRanker.select(Collections.singletonList(route),weather);
  if(chosen==null||(weather.known&&(weather.rain||weather.coldAlert)&&r.transportMode.equals("혼합")))
   return new Plan("대체 경로 필요",source+"안전한 대체 경로를 확인해야 합니다. 출발 알림은 예약하지 않습니다.",0,0,0,false);
  return new Plan(chosen.mode,source+"사용자 입력 계획 · 실제 후보/교통 연동 전",chosen.durationMinutes,chosen.walkingMinutes,r.alertLeadMinutes,true);
 }
 private RoutePlanner(){}
}
