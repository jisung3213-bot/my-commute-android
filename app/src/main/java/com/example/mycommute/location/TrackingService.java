package com.example.mycommute.location;

import android.Manifest;
import com.example.mycommute.alarms.AlertLabMode;
import com.example.mycommute.alarms.AutoCommute;
import com.example.mycommute.alarms.ReminderScheduler;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.example.mycommute.alarms.NotificationHelper;
import com.example.mycommute.alarms.TravelAlertMonitor;
import com.example.mycommute.data.TransitRouteCache;
import com.example.mycommute.data.AlarmLogRepository;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.Routine;
import com.example.mycommute.network.LiveArrivalClient;
import com.example.mycommute.network.TmapWalkingClient;
import com.example.mycommute.engine.CommuteWindow;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/** Foreground location for a manual trip or one scheduled commute window. */
public class TrackingService extends Service {
    public static final String ACTION_LOCATION="com.example.mycommute.LOCATION_UPDATED";
    public static final String ACTION_STOP="com.example.mycommute.STOP_TRACKING";
    public static final String ACTION_STATE="com.example.mycommute.TRACKING_STATE";
    private static TrackingService active;
    private String account;
    private long automaticTarget,automaticDeparture,routeFetchedAt;
    private boolean guiding;
    private FusedLocationProviderClient provider;
    private android.content.SharedPreferences trackingPrefs;
    private LocationCallback callback;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TravelAlertMonitor monitor;
    private Routine monitoredRoutine;
    private TransitRoute monitoredRoute;
    private boolean networkActive,walkLoading,arrivalLoading,autoFinished;
    private String stopReason="";
    private long lastArrivalPoll,lastWalkPoll;
    private int lastArrivalLeg=-1;
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(!canContinue())return;
            if(guiding){
                beginMonitoring();
                pollLiveData();
            }
            long remaining=automaticTarget>0?deadline()-System.currentTimeMillis():15_000L;
            handler.postDelayed(this,Math.max(1,Math.min(15_000L,remaining)));
        }
    };

    public static boolean running(){return active!=null;}
    public static String activeRoutineId(){return active==null?null:active.monitoredRoutine.id;}
    public static boolean automatic(){return active!=null&&active.automaticTarget>0;}
    public static boolean guiding(){return active!=null&&active.guiding;}

    public static void stopAutomatic(android.content.Context context){
        if(automatic())context.stopService(new Intent(context,TrackingService.class));
    }

    public static void stopExpired(long target){
        if(automatic()&&active.automaticTarget==target)active.canContinue();
    }

    public static void stopTrip(android.content.Context context){
        if(active!=null)active.stopReason="위치 추적 종료";
        if(automatic())RoutineStore.prefs(context).edit()
                .putLong("auto_stopped_"+activeRoutineId(),active.automaticTarget).apply();
        context.stopService(new Intent(context,TrackingService.class));
    }

    private long deadline(){
        return guiding?automaticTarget+CommuteWindow.TRACKING_GRACE_MS:automaticDeparture+1;
    }

    private boolean canContinue(){
        if(AlertLabMode.enabled(this)||!account.equals(Session.accountId(this))) {
            stopSelf();return false;
        }
        long now=System.currentTimeMillis();
        if(automaticTarget>0&&(!AutoCommute.enabled(this)||now>=deadline()
                ||(!guiding&&!CommuteWindow.inStartCheck(automaticDeparture,now)))){
            stopReason=guiding?"출퇴근 자동 추적 종료":"출발지 500m 이내 위치 미확인";
            stopSelf();return false;
        }
        return true;
    }

    private void startGuidance(){
        startForeground(420,NotificationHelper.trackingBuilder(this).build());
        AlarmLogRepository.write(this,"trip_start","이동 안내 시작",
                monitoredRoutine.name,false,null);
    }

    private boolean checkStart(Location fix,long now){
        if(guiding)return true;
        float[] distance=new float[1];
        Location.distanceBetween(monitoredRoutine.startLat,monitoredRoutine.startLon,
                fix.getLatitude(),fix.getLongitude(),distance);
        if(!CommuteWindow.qualifiesAtStart(automaticDeparture,now,fix.getTime(),
                fix.hasAccuracy()?fix.getAccuracy():Float.NaN,distance[0]))return false;
        guiding=true;
        AutoCommute.verifyStart(this,monitoredRoutine,automaticTarget,automaticDeparture);
        AutoCommute.scheduleEnd(this,automaticTarget,deadline());
        startGuidance();
        broadcastState(true,"");
        return true;
    }

    private void pollLiveData(){
        if(monitor==null||monitoredRoute==null||monitoredRoutine==null)return;
        long now=System.currentTimeMillis();
        long target=CommuteWindow.nearestTarget(monitoredRoutine,now);
        if(target<=0||now<target-4*60*60_000L||now>target+4*60*60_000L)return;
        int boarding=monitor.boardingLeg();
        if(boarding<0||boarding>=monitoredRoute.legs.size())return;
        if(boarding!=lastArrivalLeg){
            lastArrivalLeg=boarding;lastArrivalPoll=0;lastWalkPoll=0;
            monitor.setWalkingSeconds(-1,0);
        }
        if(!arrivalLoading&&now-lastArrivalPoll>=60_000L){
            lastArrivalPoll=now;
            arrivalLoading=true;
            final int expected=boarding;
            final TravelAlertMonitor forMonitor=monitor;
            LiveArrivalClient.fetchLeg(monitoredRoute.legs.get(boarding),result->{
                if(monitor!=forMonitor)return;
                arrivalLoading=false;
                if(!networkActive||monitor==null||lastArrivalLeg!=expected)return;
                monitor.setArrival(result,expected);
            });
        }
        if(!walkLoading&&monitor.validPositionNow(now)&&
                now-lastWalkPoll>=5*60_000L){
            TransitRoute.Leg leg=monitoredRoute.legs.get(boarding);
            double lat=monitor.positionLat(),lon=monitor.positionLon();
            if(!Double.isFinite(leg.startLat)||!Double.isFinite(leg.startLon))return;
            // Avoid costly and meaningless pedestrian queries when far away.
            android.location.Location from=new android.location.Location("commute");
            from.setLatitude(lat);from.setLongitude(lon);
            android.location.Location to=new android.location.Location("commute");
            to.setLatitude(leg.startLat);to.setLongitude(leg.startLon);
            if(from.distanceTo(to)>1800)return;
            walkLoading=true;lastWalkPoll=now;
            final int expected=boarding;
            final TravelAlertMonitor forMonitor=monitor;
            TmapWalkingClient.estimateSeconds(lat,lon,leg.startLat,leg.startLon,
                    new TmapWalkingClient.DurationCallback(){
                @Override public void success(int seconds){
                    if(monitor!=forMonitor)return;
                    walkLoading=false;
                    if(networkActive&&monitor!=null&&lastArrivalLeg==expected)
                        monitor.setWalkingSeconds(seconds,System.currentTimeMillis());
                    if(networkActive&&monitoredRoutine!=null&&monitoredRoute!=null
                            &&lastArrivalLeg==expected
                            &&expected==LiveArrivalClient.firstBoardingIndex(monitoredRoute)){
                        long current=System.currentTimeMillis();
                        long target=CommuteWindow.nearestTarget(monitoredRoutine,current);
                        int duration=com.example.mycommute.alarms.ReminderScheduler
                                .durationMinutes(TrackingService.this,monitoredRoutine);
                        long plannedDeparture=target-(duration+monitoredRoutine.marginMinutes)*60_000L;
                        if(target>0&&duration>0&&current<plannedDeparture+5*60_000L
                                &&ReminderScheduler.selected(TrackingService.this,monitoredRoutine.id))
                            com.example.mycommute.alarms.ReminderScheduler.updateFromPosition(
                                    TrackingService.this,monitoredRoutine,monitoredRoute,
                                    expected,seconds);
                    }
                }
                @Override public void error(String reason){if(monitor==forMonitor)walkLoading=false;}
            });
        }
    }

    private void beginMonitoring(){
        long now=System.currentTimeMillis();
        if(monitoredRoute!=null&&now>=routeFetchedAt&&now-routeFetchedAt<TransitRouteCache.TTL)return;
        monitoredRoute=null;
        monitor=null;
        networkActive=false;
        arrivalLoading=false;walkLoading=false;
        TransitRouteCache.Snapshot snapshot=TransitRouteCache.load(this,monitoredRoutine);
        if(snapshot==null)return;
        routeFetchedAt=snapshot.timestamp;
        monitoredRoute=snapshot.routes.get(snapshot.selected);
        monitor=new TravelAlertMonitor(this,monitoredRoutine,()->{
            autoFinished=true;
            stopSelf();
        });
        monitor.setRoute(monitoredRoute);
        lastArrivalPoll=lastWalkPoll=0;
        lastArrivalLeg=-1;
        networkActive=true;
    }

    @Override public void onCreate(){
        super.onCreate();provider=LocationServices.getFusedLocationProviderClient(this);
        trackingPrefs=RoutineStore.prefs(this);
        account=Session.accountId(this);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(AlertLabMode.enabled(this)){
            stopReason="알림 테스트 모드 · 위치 추적 중지";
            stopSelf();return START_NOT_STICKY;
        }
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())) {
            stopReason="위치 추적 종료";
            stopTrip(this);return START_NOT_STICKY;
        }
        if(!Session.signedIn(this)){
            stopReason="로그인 만료 · 위치 추적 불가";
            stopSelf();return START_NOT_STICKY;
        }
        if(!(allowed(Manifest.permission.ACCESS_FINE_LOCATION)||allowed(Manifest.permission.ACCESS_COARSE_LOCATION))){
            stopReason="위치 접근 권한 필요";
            stopSelf();return START_NOT_STICKY;
        }
        if(active==this)return START_NOT_STICKY;
        String routineId=intent==null?null:intent.getStringExtra("routineId");
        monitoredRoutine=routineId==null?RoutineStore.read(this):RoutineStore.read(this,routineId);
        automaticTarget=intent==null?0:intent.getLongExtra("target",0);
        automaticDeparture=intent==null?0:intent.getLongExtra("departure",0);
        guiding=automaticTarget<=0||AutoCommute.startVerified(this,monitoredRoutine,automaticTarget);
        if(!monitoredRoutine.isConfigured()||!canContinue()){
            stopSelf();return START_NOT_STICKY;
        }
        try {
            if(guiding)startGuidance();
            else startForeground(420,NotificationHelper.startCheckBuilder(this).build());
            active=this;
            if(automaticTarget>0)AutoCommute.scheduleEnd(this,automaticTarget,deadline());
            trackingPrefs.edit().remove("lastFixTime").remove("lastLat").remove("lastLon")
                    .remove("lastAccuracy").apply();
            handler.post(ticker);
            if(callback==null) {
                LocationRequest request=new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,15000)
                        .setMinUpdateIntervalMillis(5000).build();
                callback=new LocationCallback() {
                    @Override public void onLocationResult(LocationResult result) {
                        if(!canContinue()||result.getLastLocation()==null)return;
                        Location fix=result.getLastLocation();
                        double lat=fix.getLatitude(),lon=fix.getLongitude();
                        long now=System.currentTimeMillis(),time=fix.getTime();
                        if(!checkStart(fix,now))return;
                        if(time<=0||time>now+30000L)time=now;
                        float accuracy=fix.hasAccuracy()?fix.getAccuracy():Float.NaN;
                        trackingPrefs.edit().putFloat("lastLat",(float)lat)
                                .putFloat("lastLon",(float)lon).putLong("lastFixTime",time)
                                .putFloat("lastAccuracy",accuracy).apply();
                        beginMonitoring();
                        if(monitor!=null){
                            monitor.onPosition(lat,lon,time,accuracy);
                            pollLiveData();
                        }
                        Intent event=new Intent(ACTION_LOCATION);event.setPackage(getPackageName());
                        event.putExtra("lat",lat);event.putExtra("lon",lon);event.putExtra("timestamp",time);
                        event.putExtra("accuracyMeters",accuracy);
                        sendBroadcast(event);
                    }
                };
                provider.requestLocationUpdates(request,callback,getMainLooper())
                        .addOnSuccessListener(v->broadcastState(true,""))
                        .addOnFailureListener(error->{
                            stopReason="GPS 위치 업데이트 등록 실패: "
                                    +(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());
                            stopSelf();
                        });
            }
        }catch(SecurityException|IllegalStateException e){
            stopReason="위치추적 서비스 시작 실패: "
                    +(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
            stopSelf();return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }
    private boolean allowed(String permission) {return ContextCompat.checkSelfPermission(this,permission)==PackageManager.PERMISSION_GRANTED;}
    private void broadcastState(boolean active,String reason){
        Intent changed=new Intent(ACTION_STATE);
        changed.setPackage(getPackageName());
        changed.putExtra("active",active);
        changed.putExtra("reason",reason);
        changed.putExtra("finished",autoFinished);
        sendBroadcast(changed);
    }
    @Override public void onDestroy() {
        networkActive=false;
        handler.removeCallbacks(ticker);
        boolean wasActive=active==this;
        boolean sameAccount=account.equals(Session.accountId(this));
        boolean nextTrip=wasActive&&sameAccount&&automaticTarget>0&&(autoFinished
                ||AutoCommute.wasStopped(this,monitoredRoutine.id,automaticTarget)
                ||System.currentTimeMillis()>=deadline());
        if(wasActive)active=null;
        if(wasActive&&sameAccount) {
            if(automaticTarget>0)AutoCommute.cancelEnd(this);
            ReminderScheduler.clearDynamic(this,monitoredRoutine.id);
            ReminderScheduler.scheduleCurrent(this);
            if(guiding)AlarmLogRepository.write(this,"trip_end","이동 안내 종료",
                    "위치 추적 종료",false,null);
        }
        monitor=null;monitoredRoute=null;monitoredRoutine=null;
        if(callback!=null)provider.removeLocationUpdates(callback);
        broadcastState(false,stopReason);
        super.onDestroy();
        if(nextTrip)handler.post(()->AutoCommute.resume(this,false));
    }
    @Nullable @Override public IBinder onBind(Intent intent){return null;}
}
