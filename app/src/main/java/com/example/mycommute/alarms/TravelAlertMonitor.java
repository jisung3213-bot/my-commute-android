package com.example.mycommute.alarms;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import com.example.mycommute.data.AlarmLogRepository;
import com.example.mycommute.data.AlarmSettingsStore;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.engine.CommuteWindow;
import com.example.mycommute.engine.AlertThresholds;
import com.example.mycommute.engine.StagePositionMatcher;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;

/**
 * Foreground-service trip alert evaluator. Does not infer actual boarding from GPS
 * proximity, nor invent a vehicle ETA, stop sequence, or pedestrian duration.
 * All methods are called on the Android main thread.
 */
public final class TravelAlertMonitor {
    public interface Finished {void complete();}
    private final Context context;
    private final Routine routine;
    private final Finished finishedCallback;
    private TransitRoute route;
    private TransitArrival arrival;
    private int arrivalLeg=-1;
    private int walkingSeconds=-1;
    private long walkObservedAt;
    private double lat=Double.NaN,lon=Double.NaN;
    private long positionAt;
    private float accuracy=Float.NaN;
    private int knownLeg=-1;
    private boolean wasOnRoute,wasFarFromDestination;
    private long deviatedSince,nearFinishSince,nearTransferSince,nearExitSince;
    private int candidateTransfer=-1,candidateExit=-1;
    private boolean completed,delayActive,deviationActive;
    private final long startedAt;

    public TravelAlertMonitor(Context c,Routine r,Finished onFinish){
        context=c.getApplicationContext();
        routine=r;
        finishedCallback=onFinish;
        startedAt=System.currentTimeMillis();
    }

    public void setRoute(TransitRoute value){
        if(route!=value){
            route=value;knownLeg=-1;
            deviatedSince=nearFinishSince=nearTransferSince=nearExitSince=0;
            candidateTransfer=candidateExit=-1;
            wasOnRoute=false;wasFarFromDestination=false;
            delayActive=deviationActive=false;
        }
    }
    public int boardingLeg(){
        if(route==null)return -1;
        for(int i=Math.max(0,knownLeg);i<route.legs.size();i++){
            String mode=route.legs.get(i).mode;
            if("BUS".equals(mode)||"SUBWAY".equals(mode))return i;
        }
        return -1;
    }
    public void setArrival(TransitArrival value,int index){
        arrival=value;arrivalLeg=index;
        evaluateVehicle(System.currentTimeMillis());
    }
    public void setWalkingSeconds(int value,long at){
        walkingSeconds=value;walkObservedAt=at;
        evaluateVehicle(System.currentTimeMillis());
    }
    public void onPosition(double newLat,double newLon,long at,float meters){
        if(completed||route==null||route.legs.isEmpty())return;
        long now=System.currentTimeMillis();
        if(!validGps(newLat,newLon,at,meters,now))return;
        lat=newLat;lon=newLon;positionAt=at;accuracy=meters;
        if(!withinWindow(now))return;
        StagePositionMatcher.Match stage=StagePositionMatcher.locate(
                route,newLat,newLon,at,meters,now);
        if(stage.known()){
            wasOnRoute=true;
            if(stage.legIndex>=knownLeg)knownLeg=stage.legIndex;
        }
        inspectDeviation(now);
        inspectTransfer(now);
        inspectExit(now);
        inspectArrivalAtDestination(now);
        inspectDelay(now);
        evaluateVehicle(now);
    }
    public boolean validPositionNow(long now){
        return validGps(lat,lon,positionAt,accuracy,now);
    }
    public double positionLat(){return lat;}
    public double positionLon(){return lon;}

    private boolean withinWindow(long now){
        long target=CommuteWindow.nearestTarget(routine,now);
        return target>0&&now>=target-4*60*60_000L&&now<=target+4*60*60_000L;
    }
    private String trip(long now){
        long target=CommuteWindow.nearestTarget(routine,now);
        return target>0?AlertLedger.tripKey(target):"none";
    }

    private void evaluateVehicle(long now){
        if(completed||!withinWindow(now)||route==null||arrival==null||
                !arrival.available||now-arrival.fetchedAtMillis>120_000L||
                arrivalLeg<0||arrivalLeg!=boardingLeg())return;
        int seconds=arrival.currentSeconds(now);
        if(seconds<=0)return;
        TransitRoute.Leg leg=route.legs.get(arrivalLeg);
        // When the two conditions coincide, the actionable walking warning wins.
        if(validPositionNow(now)&&walkingSeconds>=0&&
                now-walkObservedAt<=5*60_000L){
            int slack=seconds-walkingSeconds-120;
            if(AlertThresholds.walkDue(seconds,walkingSeconds,120,
                    AlarmSettingsStore.walkSlackMinutes(context))){
                send(AlarmSettingsStore.WALK,String.valueOf(arrivalLeg),
                        "지금 승차지로 이동하세요",leg.startName+" · "+(slack>=0
                                ?"탑승 여유 약 "+((slack+59)/60)+"분":"탑승 시간이 부족할 수 있어요"),
                        now,Long.MAX_VALUE);
                return;
            }
        }
        if(AlertThresholds.vehicleDue(seconds,AlarmSettingsStore.vehicleMinutes(context))){
            send(AlarmSettingsStore.VEHICLE,String.valueOf(arrivalLeg),
                    "탑승할 차량이 곧 도착해요",leg.startName+" · 약 "+((seconds+59)/60)+"분 후 도착",
                    now,Long.MAX_VALUE);
        }
    }

    private void inspectDelay(long now){
        if(knownLeg<0)return;
        long target=CommuteWindow.nearestTarget(routine,now);
        if(target<=0)return;
        long remaining=0;
        for(int i=knownLeg;i<route.legs.size();i++)
            remaining+=Math.max(0,route.legs.get(i).seconds);
        if(remaining<=0)return;
        long projected=now+remaining*1000;
        boolean delayed=AlertThresholds.delayed(projected,target,
                AlarmSettingsStore.delayMinutes(context));
        if(delayed&&!delayActive)
            send(AlarmSettingsStore.DELAY,"trip","도착이 늦어질 수 있어요",
                    "목표 시각보다 약 "+((projected-target+59_999)/60_000)+"분 지연 예상",
                    now,5*60_000L);
        delayActive=delayed;
    }

    private void inspectDeviation(long now){
        if(!wasOnRoute||now-startedAt<60_000L)return;
        double meters=distanceToPath(route,lat,lon);
        if(!Double.isFinite(meters))return;
        if(meters>150+accuracy){
            if(deviatedSince==0)deviatedSince=now;
            if(AlertThresholds.offRoute(meters,accuracy,now-deviatedSince)
                    &&!deviationActive){
                send(AlarmSettingsStore.DEVIATION,"route","이동 경로를 확인하세요",
                        "등록한 경로에서 벗어난 것으로 보여요",now,5*60_000L);
                deviationActive=true;
            }
        }else{
            deviatedSince=0;
            deviationActive=false;
        }
    }

    private void inspectTransfer(long now){
        if(!wasOnRoute||knownLeg<0)return;
        for(int i=1;i<route.legs.size();i++){
            TransitRoute.Leg previous=route.legs.get(i-1),next=route.legs.get(i);
            if(!("BUS".equals(next.mode)||"SUBWAY".equals(next.mode)))continue;
            boolean previousTransit=false;
            for(int j=0;j<i;j++){
                String m=route.legs.get(j).mode;
                if("BUS".equals(m)||"SUBWAY".equals(m))previousTransit=true;
            }
            if(!previousTransit||knownLeg<Math.max(0,i-2)||knownLeg>i||
                    !valid(next.startLat,next.startLon))continue;
            if(meters(lat,lon,next.startLat,next.startLon)<100){
                if(candidateTransfer!=i){candidateTransfer=i;nearTransferSince=now;}
                if(now-nearTransferSince>=15_000L)
                    send(AlarmSettingsStore.TRANSFER,String.valueOf(i),
                            "환승할 곳에 가까워졌어요",next.startName+" · "+next.routeName+" 환승 준비",now,Long.MAX_VALUE);
                return;
            }
        }
        nearTransferSince=0;candidateTransfer=-1;
    }

    private void inspectExit(long now){
        if(knownLeg<0)return;
        for(int i=0;i<route.legs.size();i++){
            TransitRoute.Leg leg=route.legs.get(i);
            if(!("BUS".equals(leg.mode)||"SUBWAY".equals(leg.mode)))continue;
            if(knownLeg!=i||!valid(leg.penultimateLat,leg.penultimateLon))continue;
            // Only provider-defined penultimate stop coordinates; no approximation.
            if(meters(lat,lon,leg.penultimateLat,leg.penultimateLon)<95){
                if(candidateExit!=i){candidateExit=i;nearExitSince=now;}
                if(now-nearExitSince>=15_000L)
                    send(AlarmSettingsStore.EXIT,String.valueOf(i),
                            "곧 내릴 곳이에요",leg.endName+" · 다음 정류장에서 하차하세요",now,Long.MAX_VALUE);
                return;
            }
        }
        nearExitSince=0;candidateExit=-1;
    }

    private void inspectArrivalAtDestination(long now){
        if(!valid(routine.endLat,routine.endLon))return;
        double dist=meters(lat,lon,routine.endLat,routine.endLon);
        if(dist>300)wasFarFromDestination=true;
        if(dist>100){
            nearFinishSince=0;return;
        }
        if(nearFinishSince==0)nearFinishSince=now;
        if(!AlertThresholds.nearDestination(dist,wasFarFromDestination,knownLeg,
                route.legs.size(),now-startedAt,now-nearFinishSince))return;
        String trip=trip(now);
        if("none".equals(trip)||AlertLedger.finished(context,routine.id,trip))return;
        if(AlarmSettingsStore.enabled(context,AlarmSettingsStore.FINISH))
            send(AlarmSettingsStore.FINISH,"destination",
                    "목적지 근처에 도착했어요",routine.endName+" · 이동 안내를 종료해요",
                    now,Long.MAX_VALUE);
        AlertLedger.finish(context,routine.id,trip);
        completed=true;
        finishedCallback.complete();
    }

    private void send(String type,String qualifier,String title,String body,
                      long now,long cooldown){
        if(!AlarmSettingsStore.enabled(context,type))return;
        String trip=trip(now);
        if("none".equals(trip)||AlertLedger.finished(context,routine.id,trip))return;
        if(!AlertLedger.tryClaim(context,routine.id,trip,type,qualifier,now,cooldown))return;
        boolean sent=NotificationHelper.show(context,title,body,
                (routine.id+"|"+trip+"|"+type+"|"+qualifier).hashCode(),routine.id);
        AlarmLogRepository.write(context,type+":"+routine.id,
                sent?title:"알림 차단됨",sent?body:"알림 권한 확인 필요 · "+body,
                true,null);
    }

    private static boolean validGps(double a,double b,long time,float accuracy,long now){
        return valid(a,b)&&time>0&&now-time<=90_000L&&now-time>=-30_000L&&
                Float.isFinite(accuracy)&&accuracy>=0&&accuracy<=50;
    }
    private static boolean valid(double a,double b){
        return Double.isFinite(a)&&Double.isFinite(b)&&a>=33&&a<=39&&b>=124&&b<=132;
    }
    private static double meters(double a,double b,double c,double d){
        double dy=(c-a)*111_195,dx=(d-b)*111_195*Math.cos(Math.toRadians(a));
        return Math.hypot(dx,dy);
    }
    private static double distanceToPath(TransitRoute route,double a,double b){
        double min=Double.POSITIVE_INFINITY;
        double cos=Math.cos(Math.toRadians(a));
        for(TransitRoute.Leg leg:route.legs){
            // Incomplete line geometry cannot prove the user has deviated.
            if(leg.points.size()<2&&leg.seconds>=60)return Double.NaN;
            if(leg.points.size()<2)continue;
            for(int i=1;i<leg.points.size();i++){
                TransitRoute.Point p=leg.points.get(i-1),q=leg.points.get(i);
                double ax=(p.lon-b)*111_195*cos,ay=(p.lat-a)*111_195;
                double bx=(q.lon-b)*111_195*cos,by=(q.lat-a)*111_195;
                double dx=bx-ax,dy=by-ay;
                double norm=dx*dx+dy*dy;
                double t=norm<0.01?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/norm));
                min=Math.min(min,Math.hypot(ax+t*dx,ay+t*dy));
            }
        }
        return min;
    }
}
