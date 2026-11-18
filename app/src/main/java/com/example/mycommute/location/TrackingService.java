package com.example.mycommute.location;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.example.mycommute.alarms.NotificationHelper;
import com.example.mycommute.alarms.TravelAlertMonitor;
import com.example.mycommute.data.TransitRouteCache;
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

/** User-started foreground location. Never auto-starts from background alarms or reboot. */
public class TrackingService extends Service {
    public static final String ACTION_LOCATION="com.example.mycommute.LOCATION_UPDATED";
    public static final String ACTION_STOP="com.example.mycommute.STOP_TRACKING";
    public static final String ACTION_STATE="com.example.mycommute.TRACKING_STATE";
    private FusedLocationProviderClient provider;
    private android.content.SharedPreferences trackingPrefs;
    private LocationCallback callback;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TravelAlertMonitor monitor;
    private Routine monitoredRoutine;
    private TransitRoute monitoredRoute;
    private boolean networkActive,walkLoading,arrivalLoading,autoFinished;
    private long lastArrivalPoll,lastWalkPoll;
    private int lastArrivalLeg=-1;
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(!networkActive)return;
            pollLiveData();
            handler.postDelayed(this,15_000L);
        }
    };

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
            LiveArrivalClient.fetchLeg(monitoredRoute.legs.get(boarding),result->{
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
            TmapWalkingClient.estimateSeconds(lat,lon,leg.startLat,leg.startLon,
                    new TmapWalkingClient.DurationCallback(){
                @Override public void success(int seconds){
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
                        if(target>0&&duration>0&&current<plannedDeparture+5*60_000L)
                            com.example.mycommute.alarms.ReminderScheduler.updateFromPosition(
                                    TrackingService.this,monitoredRoutine,monitoredRoute,
                                    expected,seconds);
                    }
                }
                @Override public void error(String reason){walkLoading=false;}
            });
        }
    }

    private void beginMonitoring(){
        if(networkActive)return;
        monitoredRoutine=RoutineStore.read(this);
        if(!monitoredRoutine.isConfigured())return;
        TransitRouteCache.Snapshot snapshot=TransitRouteCache.load(this,monitoredRoutine);
        if(snapshot==null)return;
        monitoredRoute=snapshot.routes.get(snapshot.selected);
        monitor=new TravelAlertMonitor(this,monitoredRoutine,()->{
            autoFinished=true;
            stopSelf();
        });
        monitor.setRoute(monitoredRoute);
        lastArrivalPoll=lastWalkPoll=0;
        lastArrivalLeg=-1;
        networkActive=true;
        handler.post(ticker);
    }

    @Override public void onCreate(){
        super.onCreate();provider=LocationServices.getFusedLocationProviderClient(this);
        trackingPrefs=RoutineStore.prefs(this);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())) {stopSelf();return START_NOT_STICKY;}
        if(!Session.signedIn(this)||!(allowed(Manifest.permission.ACCESS_FINE_LOCATION)||allowed(Manifest.permission.ACCESS_COARSE_LOCATION))) {
            stopSelf();return START_NOT_STICKY;
        }
        try {
            startForeground(420,NotificationHelper.trackingBuilder(this).build());
            if(callback==null){trackingPrefs.edit().putLong("trip_started",System.currentTimeMillis()).apply();
                com.example.mycommute.data.AlarmLogRepository.write(this,"trip_start","이동 시작","사용자가 위치 추적을 시작했습니다",false,null);}
            trackingPrefs.edit().putBoolean("tracking_active",true).apply();
            beginMonitoring();
            if(callback==null) {
                LocationRequest request=new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,15000)
                        .setMinUpdateIntervalMillis(5000).build();
                callback=new LocationCallback() {
                    @Override public void onLocationResult(LocationResult result) {
                        if(result.getLastLocation()==null)return;
                        android.location.Location fix=result.getLastLocation();
                        double lat=fix.getLatitude(),lon=fix.getLongitude();
                        long now=System.currentTimeMillis(),time=fix.getTime();
                        if(time<=0||time>now+30000L)time=now;
                        float accuracy=fix.hasAccuracy()?fix.getAccuracy():Float.NaN;
                        trackingPrefs.edit().putFloat("lastLat",(float)lat)
                                .putFloat("lastLon",(float)lon).putLong("lastFixTime",time)
                                .putFloat("lastAccuracy",accuracy).apply();
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
                provider.requestLocationUpdates(request,callback,getMainLooper());
            }
        }catch(SecurityException|IllegalStateException e){stopSelf();return START_NOT_STICKY;}
        return START_NOT_STICKY;
    }
    private boolean allowed(String permission) {return ContextCompat.checkSelfPermission(this,permission)==PackageManager.PERMISSION_GRANTED;}
    @Override public void onDestroy() {
        networkActive=false;
        handler.removeCallbacks(ticker);
        if(monitoredRoutine!=null) {
            com.example.mycommute.alarms.ReminderScheduler.clearDynamic(
                    this,monitoredRoutine.id);
            com.example.mycommute.alarms.ReminderScheduler.scheduleCurrent(this);
        }
        monitor=null;monitoredRoute=null;monitoredRoutine=null;
        if(callback!=null)provider.removeLocationUpdates(callback);
        trackingPrefs.edit().putBoolean("tracking_active",false)
                .putLong("trip_ended",System.currentTimeMillis()).apply();
        Intent changed=new Intent(ACTION_STATE);
        changed.setPackage(getPackageName());
        changed.putExtra("finished",autoFinished);
        sendBroadcast(changed);
        com.example.mycommute.data.AlarmLogRepository.write(this,"trip_end","이동 종료","위치 추적이 종료되었습니다",false,null);
        super.onDestroy();
    }
    @Nullable @Override public IBinder onBind(Intent intent){return null;}
}
