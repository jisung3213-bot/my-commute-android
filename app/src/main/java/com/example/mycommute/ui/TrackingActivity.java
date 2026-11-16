package com.example.mycommute.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.mycommute.R;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.data.TransitRouteMemory;
import com.example.mycommute.network.LiveArrivalClient;
import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.engine.RoutePlanner;
import com.example.mycommute.engine.BoardingFeasibilityEngine;
import com.example.mycommute.engine.TripTimeline;
import com.example.mycommute.location.TrackingService;
import com.example.mycommute.model.Routine;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrackingActivity extends AppCompatActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){renderClock();renderBoardingAssessment();handler.postDelayed(this,60000);}};
    private final BroadcastReceiver updates=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {updatePosition(intent.getDoubleExtra("lat",0),intent.getDoubleExtra("lon",0),intent.getLongExtra("timestamp",0),intent.getFloatExtra("accuracyMeters",Float.NaN));}
    };
    private boolean listening=false;
    private boolean queryingArrival;
    private int arrivalGeneration;
    private long lastArrivalQueryTime;
    private TransitArrival lastArrival;
    private TransitRoute lastArrivalRoute;
    private double latestLat=Double.NaN,latestLon=Double.NaN;
    private long latestGpsTime;
    private float latestAccuracy=Float.NaN;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(!Session.signedIn(this)) {startActivity(new Intent(this,LoginActivity.class));finish();return;}
        String requested=getIntent().getStringExtra("routineId");
        if(requested!=null&&!RoutineStore.prefs(this).getBoolean("tracking_active",false))RoutineStore.select(this,requested);
        setContentView(R.layout.activity_tracking);Navigation.bind(this);
        findViewById(R.id.btnStartTracking).setOnClickListener(v -> startTracking());
        findViewById(R.id.btnStopTracking).setOnClickListener(v -> {
            Intent stop=new Intent(this,TrackingService.class);stop.setAction(TrackingService.ACTION_STOP);
            stopService(stop);RoutineStore.prefs(this).edit().putBoolean("tracking_active",false).apply();
            Toast.makeText(this,"위치 추적을 종료했어",Toast.LENGTH_SHORT).show();renderClock();renderBoardingAssessment();
        });
        findViewById(R.id.btnRefreshTrackingArrival).setOnClickListener(v->loadLiveArrival());
        renderTimeline();
    }
    @Override protected void onResume() {
        super.onResume();renderTimeline();
        if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,new IntentFilter(TrackingService.ACTION_LOCATION),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(updates,new IntentFilter(TrackingService.ACTION_LOCATION));
        listening=true;handler.removeCallbacks(ticker);handler.post(ticker);
        findViewById(R.id.btnRefreshTrackingArrival).setEnabled(true);
        long last=RoutineStore.prefs(this).getLong("lastFixTime",0);
        if(last>0)updatePosition(RoutineStore.prefs(this).getFloat("lastLat",0),
                RoutineStore.prefs(this).getFloat("lastLon",0),last,
                RoutineStore.prefs(this).getFloat("lastAccuracy",Float.NaN));
        else renderBoardingAssessment();
    }
    @Override protected void onPause() {
        if(listening){unregisterReceiver(updates);listening=false;}
        handler.removeCallbacks(ticker);
        arrivalGeneration++;
        queryingArrival=false;
        findViewById(R.id.btnRefreshTrackingArrival).setEnabled(true);
        super.onPause();
    }
    private void loadLiveArrival() {
        if(queryingArrival)return;
        Routine r=RoutineStore.read(this);
        TransitRoute route=TransitRouteMemory.selected(Session.accountId(this),r);
        if(route==null){
            ((TextView)findViewById(R.id.trackingLiveArrival)).setText(
                    "선택한 경로의 임시 조회 정보가 없어. 커스텀 화면에서 경로를 다시 검색·선택해");
            return;
        }
        long now=System.currentTimeMillis();
        if(now-lastArrivalQueryTime<30000L){
            Toast.makeText(this,"30초 후 다시 조회할 수 있어",Toast.LENGTH_SHORT).show();
            return;
        }
        lastArrivalQueryTime=now;
        lastArrival=null;lastArrivalRoute=null;
        renderBoardingAssessment();
        queryingArrival=true;
        final int token=++arrivalGeneration;
        findViewById(R.id.btnRefreshTrackingArrival).setEnabled(false);
        ((TextView)findViewById(R.id.trackingLiveArrival)).setText("첫 탑승 실시간 도착정보 조회 중...");
        LiveArrivalClient.fetch(route,result->{
            if(isFinishing()||isDestroyed()||token!=arrivalGeneration)return;
            queryingArrival=false;
            lastArrival=result;
            lastArrivalRoute=route;
            findViewById(R.id.btnRefreshTrackingArrival).setEnabled(true);
            ((TextView)findViewById(R.id.trackingLiveArrival)).setText(
                    (result.available?"첫 탑승 · ":"확인 불가 · ")+result.description());
            renderBoardingAssessment();
        });
    }

    private void renderBoardingAssessment() {
        TextView label=findViewById(R.id.trackingBoardingEstimate);
        Routine r=RoutineStore.read(this);
        TransitRoute route=TransitRouteMemory.selected(Session.accountId(this),r);
        if(route==null){
            lastArrival=null;lastArrivalRoute=null;
            label.setText("탑승 판단 보류 · 커스텀 화면에서 경로를 검색하고 선택해야 해");
            return;
        }
        if(!RoutineStore.prefs(this).getBoolean("tracking_active",false)){
            label.setText("탑승 판단 보류 · 최신 GPS 판단을 위해 위치 추적을 시작해");
            return;
        }
        TransitRoute.Leg stop=LiveArrivalClient.firstBoarding(route);
        if(stop==null){
            label.setText("탑승 판단 보류 · 이 경로에 버스·지하철 승차 구간이 없어");
            return;
        }
        if(lastArrival==null||lastArrivalRoute!=route||!lastArrival.available){
            label.setText("탑승 판단 보류 · 첫 탑승 차량의 실시간 도착정보를 조회해");
            return;
        }
        BoardingFeasibilityEngine.Assessment assessment=BoardingFeasibilityEngine.assess(
                System.currentTimeMillis(),
                latestLat,latestLon,latestGpsTime,latestAccuracy,
                stop.startLat,stop.startLon,
                lastArrival.fetchedAtMillis,lastArrival.seconds);
        label.setText(assessment.describe()
                +(assessment.hasEstimate()
                    ?"\n※ 실제 보행 경로 아님: 직선거리×우회계수 1.45, 도보 1.15m/s, 탑승 여유 1~2분 기준"
                    :""));
    }

    private void startTracking() {
        if(!RoutineStore.read(this).isConfigured()) {Toast.makeText(this,"먼저 루틴을 등록해",Toast.LENGTH_LONG).show();return;}
        Routine candidate=RoutineStore.read(this);
        if(!candidate.apiBased&&!RoutePlanner.select(candidate).available){
            Toast.makeText(this,"대체 경로를 설정해",Toast.LENGTH_LONG).show();return;
        }
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION},308);return;
        }
        try {ContextCompat.startForegroundService(this,new Intent(this,TrackingService.class));renderClock();}
        catch(Exception e){Toast.makeText(this,"위치 추적을 시작할 수 없어: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==308&&grants.length>0&&(grants[0]==PackageManager.PERMISSION_GRANTED||(grants.length>1&&grants[1]==PackageManager.PERMISSION_GRANTED)))startTracking();
    }
    private void updatePosition(double lat,double lon,long timestamp,float accuracy) {
        if(timestamp==0)return;
        latestLat=lat;latestLon=lon;latestGpsTime=timestamp;latestAccuracy=accuracy;
        String time=new SimpleDateFormat("HH:mm:ss",Locale.KOREA).format(new Date(timestamp));
        ((TextView)findViewById(R.id.trackingLocation)).setText(String.format(Locale.KOREA,
                "GPS 위치: %.5f, %.5f · 최근 갱신 %s · 정확도 %s",
                lat,lon,time,Float.isFinite(accuracy)?String.format(Locale.KOREA,"약 %.0fm",accuracy):"미확인"));
        renderBoardingAssessment();
    }
    private void renderClock() {
        Routine r=RoutineStore.read(this);
        RoutePlanner.Plan plan=RoutePlanner.select(r);
        ((TextView)findViewById(R.id.trackingStatus)).setText(RoutineStore.prefs(this).getBoolean("tracking_active",false)
                ? "GPS 추적 실행 중 · 첫 탑승 도착정보는 별도 조회" : "GPS 추적 중지됨 · 시작 버튼을 누르면 활성화돼");
        if(!r.isConfigured()) {
            ((TextView)findViewById(R.id.trackingEta)).setText("루틴 미등록");return;
        }
        if(!plan.available){
            TransitRoute preview=TransitRouteMemory.selected(Session.accountId(this),r);
            ((TextView)findViewById(R.id.trackingEta)).setText("전체 실제 도착예정시간 미확인");
            ((TextView)findViewById(R.id.trackingProgress)).setText(r.apiBased
                    ? (preview!=null?"TMAP 검색 당시 전체 "+TransitRoute.minutes(preview.totalSeconds)
                            +"분 · 실시간 보정 전":"저장된 장소로 경로를 다시 검색해야 해")
                    : "대체 경로가 필요해");
            ((ProgressBar)findViewById(R.id.trackingBar)).setProgress(0);
            return;
        }
        boolean active=RoutineStore.prefs(this).getBoolean("tracking_active",false);
        long started=RoutineStore.prefs(this).getLong("trip_started",0);
        int elapsed=active&&started>0?(int)Math.max(0,(System.currentTimeMillis()-started)/60000):0;
        int remaining=Math.max(0,plan.durationMinutes-elapsed);
        String eta=TripTimeline.expectedArrival(r,plan.durationMinutes);
        if(active&&started>0)eta=new SimpleDateFormat("HH:mm",Locale.KOREA).format(new Date(started+plan.durationMinutes*60000L));
        ((TextView)findViewById(R.id.trackingEta)).setText(eta+" (계획 추정)");
        ((TextView)findViewById(R.id.trackingProgress)).setText((elapsed<0?"출발까지 "+(-elapsed)+"분":
                (active?"실제 시작 후 "+elapsed+"분 · 계획상 잔여 "+remaining+"분":"이동 시작 전 · "+r.plannedDeparture(plan.durationMinutes)+" 출발 권장"))+" / "+plan.mode);
        ((ProgressBar)findViewById(R.id.trackingBar)).setProgress(Math.max(0,Math.min(100,
                (int)(100.0*Math.max(0,elapsed)/Math.max(1,plan.durationMinutes)))));
    }
    private void renderTimeline() {
        LinearLayout container=findViewById(R.id.trackingTimeline);container.removeAllViews();
        Routine r=RoutineStore.read(this);
        if(!r.isConfigured())return;
        List<String> timeline;
        if(r.apiBased){
            TransitRoute current=TransitRouteMemory.selected(Session.accountId(this),r);
            timeline=new java.util.ArrayList<>();
            if(current!=null){
                for(TransitRoute.Leg leg:current.legs)timeline.add(leg.title());
            }else timeline.add("임시 경로가 없어. 커스텀 화면에서 다시 검색해");
        }else timeline=TripTimeline.steps(r);
        for(int i=0;i<timeline.size();i++) {
            TextView row=new TextView(this);row.setText((i+1)+". "+timeline.get(i));row.setTextSize(16);
            row.setPadding(20,18,20,18);row.setTextColor(getColor(R.color.text_primary));
            row.setBackgroundResource(R.drawable.bg_card);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=12;container.addView(row,p);
        }
    }
}
