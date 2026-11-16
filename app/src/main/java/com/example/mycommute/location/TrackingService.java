package com.example.mycommute.location;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.example.mycommute.alarms.NotificationHelper;
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
    private FusedLocationProviderClient provider;
    private android.content.SharedPreferences trackingPrefs;
    private LocationCallback callback;
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
        if(callback!=null)provider.removeLocationUpdates(callback);
        trackingPrefs.edit().putBoolean("tracking_active",false).putLong("trip_ended",System.currentTimeMillis()).apply();
        com.example.mycommute.data.AlarmLogRepository.write(this,"trip_end","이동 종료","위치 추적이 종료되었습니다",false,null);
        super.onDestroy();
    }
    @Nullable @Override public IBinder onBind(Intent intent){return null;}
}
