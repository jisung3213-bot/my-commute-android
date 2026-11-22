package com.example.mycommute.engine;

import com.example.mycommute.model.TransitArrival;

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
    public static Advice compute(long now,long depart,int durationMinutes,int walkToStopSeconds,
                                 TransitArrival arrival) {
        long ifNow=now+durationMinutes*60_000L;
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
                    headline="예정 차량 탑승 어려움";
                    note="도보 소요시간 부족 · 다음 차량 확인 필요";
                }else if(delta<=0){
                    headline="지금 출발 권장";
                    note="차량 접근 가능성 추정";
                }else{
                    note="차량 접근시간 참고";
                }
            }
        }
        return new Advice(depart,ifNow,slack,verified,headline,note);
    }
}
