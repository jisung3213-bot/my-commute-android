package com.example.mycommute.engine;

import java.util.Locale;

/**
 * Conservative, on-screen FIRST boarding feasibility estimate.
 * Straight-line GPS distance is NOT a navigable pedestrian route or a guarantee.
 * Never use this estimate to schedule a background departure alarm.
 */
public final class BoardingFeasibilityEngine {
    public enum State {
        NO_POSITION, INACCURATE_POSITION, STALE_POSITION, NO_STOP,
        NO_ARRIVAL, STALE_ARRIVAL, EXPIRED_VEHICLE,
        TIME_AVAILABLE, TIME_TIGHT, TIME_INSUFFICIENT
    }

    public static final class Assessment {
        public final State state;
        public final int directMeters, walkingSeconds, vehicleSeconds, slackSeconds;
        public final String reason;
        private Assessment(State state,int distance,int walk,int vehicle,int slack,String reason) {
            this.state=state;directMeters=distance;walkingSeconds=walk;
            vehicleSeconds=vehicle;slackSeconds=slack;this.reason=reason;
        }
        public boolean hasEstimate() {
            return state==State.TIME_AVAILABLE||state==State.TIME_TIGHT||state==State.TIME_INSUFFICIENT;
        }
        public String describe() {
            if(!hasEstimate())return "탑승 판단 보류 · "+reason;
            String judgement=state==State.TIME_AVAILABLE?"첫 차량까지 시간 여유 추정":
                    state==State.TIME_TIGHT?"첫 차량 탑승이 촉박할 수 있어":
                    "첫 차량 탑승이 어려울 수 있어";
            String left=slackSeconds>=0?"약 "+((slackSeconds+59)/60)+"분 여유":
                    "약 "+((-slackSeconds+59)/60)+"분 부족";
            return String.format(Locale.KOREA,
                    "%s\n정류장까지 직선거리 약 %dm · 보행 약 %d분 · 차량 도착 약 %d분 후\n%s",
                    judgement,directMeters,(walkingSeconds+59)/60,(vehicleSeconds+59)/60,left);
        }
    }

    private static final long MAX_GPS_AGE_MS=90_000L;
    private static final long MAX_ARRIVAL_AGE_MS=120_000L;
    private static final float MAX_ACCURACY_METERS=75f;

    private BoardingFeasibilityEngine(){}

    public static Assessment assess(long nowMillis,
                                    double gpsLat,double gpsLon,long gpsTimeMillis,float gpsAccuracyMeters,
                                    double stopLat,double stopLon,
                                    long arrivalFetchedMillis,int arrivalSeconds) {
        if(!valid(gpsLat,gpsLon)||gpsTimeMillis<=0)
            return missing(State.NO_POSITION,"GPS 위치가 없어. 위치 추적을 시작해");
        if(!Float.isFinite(gpsAccuracyMeters)||gpsAccuracyMeters<0||gpsAccuracyMeters>MAX_ACCURACY_METERS)
            return missing(State.INACCURATE_POSITION,"GPS 정확도가 낮아. 위치가 안정될 때 다시 확인해");
        long gpsAge=nowMillis-gpsTimeMillis;
        if(gpsAge< -30_000L||gpsAge>MAX_GPS_AGE_MS)
            return missing(State.STALE_POSITION,"GPS 위치가 오래됐어. 최신 위치를 기다려");
        if(!valid(stopLat,stopLon))
            return missing(State.NO_STOP,"TMAP 경로에 승차 정류장 좌표가 없어");
        if(arrivalFetchedMillis<=0||arrivalSeconds<0)
            return missing(State.NO_ARRIVAL,"첫 탑승 차량의 확인된 도착정보가 없어");
        long age=nowMillis-arrivalFetchedMillis;
        if(age< -30_000L||age>MAX_ARRIVAL_AGE_MS)
            return missing(State.STALE_ARRIVAL,"도착정보가 오래됐어. 새로고침해");
        int secondsLeft=arrivalSeconds-(int)Math.max(0,age/1000L);
        if(secondsLeft<=0)
            return missing(State.EXPIRED_VEHICLE,"조회했던 차량의 도착예정시간이 지났어. 새로고침해");

        int meters=(int)Math.round(distanceMeters(gpsLat,gpsLon,stopLat,stopLon));
        // Routing is not connected: rough detour factor 1.45, nominal walk speed 1.15m/s.
        int walkSeconds=(int)Math.ceil(meters*1.45/1.15);
        // Includes a conservative station entrance / boarding buffer (60-120 sec).
        int bufferSeconds=meters<=80?60:120;
        int slack=secondsLeft-walkSeconds-bufferSeconds;
        State state=slack>=180?State.TIME_AVAILABLE:slack>=0?State.TIME_TIGHT:State.TIME_INSUFFICIENT;
        return new Assessment(state,meters,walkSeconds,secondsLeft,slack,"");
    }

    private static Assessment missing(State state,String reason) {
        return new Assessment(state,-1,-1,-1,0,reason);
    }

    private static boolean valid(double lat,double lon) {
        return Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=33&&lat<=39&&lon>=124&&lon<=132;
    }

    private static double distanceMeters(double lat1,double lon1,double lat2,double lon2) {
        double latDelta=Math.toRadians(lat2-lat1);
        double lonDelta=Math.toRadians(lon2-lon1);
        double a=Math.pow(Math.sin(latDelta/2),2)+
                Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*
                Math.pow(Math.sin(lonDelta/2),2);
        return 6371000.0*2*Math.atan2(Math.sqrt(Math.min(1,a)),Math.sqrt(Math.max(0,1-a)));
    }
}
