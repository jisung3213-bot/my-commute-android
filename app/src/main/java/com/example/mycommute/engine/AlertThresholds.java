package com.example.mycommute.engine;

/** Deterministic checks for alert thresholds; no Android or provider dependencies. */
public final class AlertThresholds {
    private AlertThresholds(){}
    public static boolean vehicleDue(int remainingSeconds,int beforeMinutes){
        return beforeMinutes>=1&&remainingSeconds>0&&
                remainingSeconds<=beforeMinutes*60;
    }
    public static boolean walkDue(int remainingSeconds,int walkSeconds,
                                  int boardingReserveSeconds,int thresholdMinutes){
        if(remainingSeconds<=0||walkSeconds<0||boardingReserveSeconds<0||
                thresholdMinutes<0)return false;
        int slack=remainingSeconds-walkSeconds-boardingReserveSeconds;
        return slack>=0&&slack<=thresholdMinutes*60;
    }
    public static boolean delayed(long projectedArrival,long target,int minutes){
        return target>0&&projectedArrival>0&&minutes>=1&&
                projectedArrival-target>=minutes*60_000L;
    }
    public static boolean offRoute(double meters,double gpsAccuracy,
                                   long continuousMillis){
        return Double.isFinite(meters)&&Double.isFinite(gpsAccuracy)&&
                gpsAccuracy>=0&&gpsAccuracy<=50&&meters>150+gpsAccuracy&&
                continuousMillis>=30_000L;
    }
    public static boolean nearDestination(double meters,boolean previouslyFar,
                                          int latestLegIndex,int legCount,
                                          long serviceAge,long confirmationMillis){
        return Double.isFinite(meters)&&meters<=100&&previouslyFar&&
                legCount>=1&&latestLegIndex>=Math.max(0,legCount-2)&&
                serviceAge>=60_000L&&confirmationMillis>=15_000L;
    }
}
