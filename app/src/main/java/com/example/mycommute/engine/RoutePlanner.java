package com.example.mycommute.engine;
import com.example.mycommute.model.Routine;
import java.util.Collections;
public final class RoutePlanner {
 public static final class Plan {
  public final String mode,note; public final int durationMinutes,walkMinutes,advanceAlertMinutes; public final boolean available;
  Plan(String m,String n,int d,int w,int a,boolean ok){mode=m;note=n;durationMinutes=d;walkMinutes=w;advanceAlertMinutes=a;available=ok;}
 }
 public static Plan select(Routine r,boolean rain){return select(r,rain,false);}
 public static Plan select(Routine r,boolean rain,boolean cold){
  String source=(rain||cold)?"수동 기상 시험: "+(rain?"우천 ":"")+(cold?"한파특보 ":""):"기상 미연동: 상태 미확인. ";
  if(r.estimatedMinutes<1||r.normalWalkMinutes<0||r.normalWalkMinutes>r.estimatedMinutes)return new Plan(r.transportMode,"이동시간을 확인하세요",0,0,0,false);
  var route=new WeatherRouteRanker.Candidate("user-estimate",r.transportMode,r.estimatedMinutes,r.normalWalkMinutes,0,0,r.transportMode.equals("자전거"));
  var chosen=WeatherRouteRanker.select(Collections.singletonList(route),new WeatherRouteRanker.Conditions(rain||cold,rain,cold));
  if(chosen==null||((rain||cold)&&r.transportMode.equals("혼합")))return new Plan("대체 경로 필요",source+"안전한 대체 경로를 확인해야 합니다. 출발 알림은 예약하지 않습니다.",0,0,0,false);
  return new Plan(chosen.mode,source+"사용자 입력 계획 · 실제 후보/교통 연동 전",chosen.durationMinutes,chosen.walkingMinutes,r.alertLeadMinutes,true);
 }
 private RoutePlanner(){}
}
