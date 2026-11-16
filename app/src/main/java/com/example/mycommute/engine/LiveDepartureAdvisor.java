package com.example.mycommute.engine;

import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;

/** Distinguishes a TMAP plan estimate from the first live vehicle's reachability. */
public final class LiveDepartureAdvisor {
    public static final class Advice {
        public final long planDepartureMillis;
        public final long planArrivalIfLeavingNowMillis;
        public final int firstBoardingSlackSeconds;
        public final boolean vehicleVerified;
        public final String headline, note;
        Advice(long planDeparture,long nowArrival,int slack,boolean verified,
               String headline,String note){
            this.planDepartureMillis=planDeparture;this.planArrivalIfLeavingNowMillis=nowArrival;
            this.firstBoardingSlackSeconds=slack;this.vehicleVerified=verified;
            this.headline=headline;this.note=note;
        }
    }
    private LiveDepartureAdvisor(){}
    public static Advice compute(long now,long targetMillis,int marginMinutes,
                                 TransitRoute route,int walkToStopSeconds,
                                 TransitArrival arrival) {
        if(route==null||route.totalSeconds<=0||targetMillis<=0)
            return new Advice(-1,-1,Integer.MIN_VALUE,false,"경로 확인 대기","TMAP 계획이 필요해");
        long depart=targetMillis-marginMinutes*60000L-route.totalSeconds*1000L;
        long ifNow=now+route.totalSeconds*1000L;
        long delta=depart-now;
        String headline=delta>60_000L?"약 "+((delta+59999L)/60000L)+"분 뒤 출발":
                delta>= -60_000L?"계획상 지금 출발":"계획 출발시각 경과";
        String note="계획 기준 · 실시간 차량은 전체 이동시간에 미반영";
        int slack=Integer.MIN_VALUE;
        boolean verified=false;
        if(arrival!=null&&arrival.available&&walkToStopSeconds>=0){
            int remaining=arrival.currentSeconds(now);
            if(remaining>0){
                verified=true;
                slack=remaining-walkToStopSeconds-120;
                if(slack<0){
                    headline="예정 차량 탑승이 어려울 수 있어";
                    note="첫 차량까지 보행시간과 탑승 여유가 부족해 · 다음 차량 확인 필요";
                }else if(delta<=0){
                    headline="지금 출발 권장 (계획 기준)";
                    note="현재 조회된 첫 차량까지 접근 가능성 추정 · 도착 보장 아님";
                }else{
                    note="첫 차량 접근 가능성 추정 · 계획 출발시각과 다를 수 있어";
                }
            }
        }
        return new Advice(depart,ifNow,slack,verified,headline,note);
    }
}
