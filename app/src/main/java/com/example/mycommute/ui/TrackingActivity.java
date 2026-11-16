package com.example.mycommute.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.mycommute.R;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.engine.BoardingFeasibilityEngine;
import com.example.mycommute.engine.RoutePlanner;
import com.example.mycommute.location.TrackingService;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.network.LiveArrivalClient;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Foreground-only live commute dashboard. External information is always labeled. */
public class TrackingActivity extends AppCompatActivity {
    private Routine routine;
    private CommuteLiveCoordinator live;
    private boolean listening;
    private double latestLat=Double.NaN,latestLon=Double.NaN;
    private long gpsTime;
    private float accuracy=Float.NaN;
    private final BroadcastReceiver locations=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            latestLat=intent.getDoubleExtra("lat",Double.NaN);
            latestLon=intent.getDoubleExtra("lon",Double.NaN);
            gpsTime=intent.getLongExtra("timestamp",0);
            accuracy=intent.getFloatExtra("accuracyMeters",Float.NaN);
            render();
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(!Session.signedIn(this)){
            startActivity(new Intent(this,LoginActivity.class));finish();return;
        }
        String requested=getIntent().getStringExtra("routineId");
        if(requested!=null&&
                !RoutineStore.prefs(this).getBoolean("tracking_active",false))
            RoutineStore.select(this,requested);
        setContentView(R.layout.activity_tracking);Navigation.bind(this);
        findViewById(R.id.liveStartTracking).setOnClickListener(v->startTracking());
        findViewById(R.id.liveStopTracking).setOnClickListener(v->stopTracking());
        findViewById(R.id.liveRefresh).setOnClickListener(v->{
            if(live!=null)live.refreshNow();
            render();
        });
        findViewById(R.id.liveOpenMap).setOnClickListener(v->
                startActivity(new Intent(this,MapActivity.class)));
    }

    @Override protected void onResume(){
        super.onResume();
        routine=RoutineStore.read(this);
        if(Build.VERSION.SDK_INT>=33)
            registerReceiver(locations,new IntentFilter(TrackingService.ACTION_LOCATION),
                    Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(locations,new IntentFilter(TrackingService.ACTION_LOCATION));
        listening=true;
        gpsTime=RoutineStore.prefs(this).getLong("lastFixTime",0);
        latestLat=RoutineStore.prefs(this).getFloat("lastLat",Float.NaN);
        latestLon=RoutineStore.prefs(this).getFloat("lastLon",Float.NaN);
        accuracy=RoutineStore.prefs(this).getFloat("lastAccuracy",Float.NaN);
        live=new CommuteLiveCoordinator(this,routine,Session.accountId(this),this::render);
        live.start();
        render();
    }

    @Override protected void onPause(){
        if(live!=null){live.stop();live=null;}
        if(listening){unregisterReceiver(locations);listening=false;}
        super.onPause();
    }

    private void stopTracking(){
        stopService(new Intent(this,TrackingService.class));
        RoutineStore.prefs(this).edit().putBoolean("tracking_active",false).apply();
        toast("위치 추적을 종료했어");
        render();
    }

    private void startTracking(){
        if(routine==null||!routine.isConfigured()){
            toast("먼저 커스텀에서 출퇴근 루틴을 등록해");return;
        }
        if(!routine.apiBased&&!RoutePlanner.select(routine).available){
            toast("경로를 다시 설정해야 해");return;
        }
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED&&
                ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},308);
            return;
        }
        try{
            ContextCompat.startForegroundService(this,new Intent(this,TrackingService.class));
            toast("위치 추적을 시작했어");
        }catch(Exception e){toast("위치 추적 실패: "+e.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==308&&grants.length>0&&
                (grants[0]==PackageManager.PERMISSION_GRANTED||
                 grants.length>1&&grants[1]==PackageManager.PERMISSION_GRANTED))startTracking();
    }

    private String clock(long timestamp){
        SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.KOREA);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return f.format(new Date(timestamp));
    }
    private TextView label(int id){return findViewById(id);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}

    private void render(){
        if(live==null||routine==null||isFinishing()||isDestroyed())return;
        long now=System.currentTimeMillis();
        boolean tracking=RoutineStore.prefs(this).getBoolean("tracking_active",false);
        boolean window=live.activeWindow();
        label(R.id.liveRouteTitle).setText(routine.isConfigured()
                ?routine.startName+" → "+routine.endName
                :"저장된 출퇴근 경로가 없어");
        label(R.id.liveSyncStatus).setText((window?"활성 이동 시간대":"예정된 이동 시간대 외")
                +" · "+live.routeStatus());
        findViewById(R.id.liveStartTracking).setEnabled(!tracking);
        findViewById(R.id.liveStopTracking).setEnabled(tracking);

        TransitRoute route=live.route();
        TransitArrival vehicle=live.arrival();
        long target=live.targetArrivalMillis();
        RouteSchematicView schematic=findViewById(R.id.liveSchematic);
        schematic.setRoute(route);
        renderDeparture(now,target,route,vehicle);
        renderWeather();
        renderRoute(route);
        renderVehicle(now,route,vehicle,tracking);
        String gpsState=tracking?"GPS 추적 중":"GPS 추적 중지됨";
        if(gpsTime>0){
            String detail=Float.isFinite(accuracy)
                    ?String.format(Locale.KOREA," · 정확도 약 %.0fm",accuracy):" · 정확도 미확인";
            gpsState+=" · 마지막 측정 "+clock(gpsTime)+detail;
        }
        label(R.id.livePosition).setText(gpsState);
    }

    private void renderDeparture(long now,long target,TransitRoute route,TransitArrival vehicle){
        if(!routine.isConfigured()||target<=0){
            label(R.id.liveDepartureHeadline).setText("루틴을 먼저 설정해");
            label(R.id.liveDepartureDetail).setText("출발지·도착지와 반복 요일을 등록해야 해");
            label(R.id.liveArrivalPlan).setText("");
            return;
        }
        if(!live.activeWindow()){
            label(R.id.liveDepartureHeadline).setText("다음 이동: "+clock(target));
            label(R.id.liveDepartureDetail).setText("출퇴근 시간대가 되면 최신 정보를 조회해");
            label(R.id.liveArrivalPlan).setText("목표 도착 "+clock(target)+" · 여유 "+routine.marginMinutes+"분");
            return;
        }
        if(route==null){
            label(R.id.liveDepartureHeadline).setText("출발시각 계산 대기");
            label(R.id.liveDepartureDetail).setText("경로 조회 결과가 없어서 출발시각을 확정할 수 없어");
            label(R.id.liveArrivalPlan).setText("목표 도착 "+clock(target));
            return;
        }
        long departure=target-(routine.marginMinutes*60L+route.totalSeconds)*1000L;
        long seconds=(departure-now)/1000L;
        label(R.id.liveDepartureHeadline).setText(
                seconds>60?"약 "+((seconds+59)/60)+"분 후 출발":
                seconds>=-60?"계획상 지금 출발":"계획 출발시각 경과");
        label(R.id.liveDepartureDetail).setText("계획상 출발 권장 "+clock(departure)
                +" · TMAP 검색 당시 이동 "+TransitRoute.minutes(route.totalSeconds)+"분");
        label(R.id.liveArrivalPlan).setText("목표 도착 "+clock(target)
                +" · 계획 도착 "+clock(departure+route.totalSeconds*1000L)
                +" · 차량 실시간 반영 전");
    }

    private void renderWeather(){
        if(live.weather()==null){
            label(R.id.liveWeather).setText(live.weatherStatus());
            label(R.id.liveAlerts).setText("날씨 미확인 · 기상특보·운행 장애 데이터 미연동");
            return;
        }
        label(R.id.liveWeather).setText(live.weatherStatus()
                +(routine.weatherEnabled?"":"\n경로 추천 가중치 반영 OFF"));
        boolean rain=live.weather().conditions.rain;
        boolean cold=live.weather().conditions.lowTemperature;
        label(R.id.liveAlerts).setText((rain?"이동 시간대 강수 예상 · 도보 주의\n":"")
                +(cold?"영하 기온 예상 · 보온 주의\n":"")
                +"실제 기상특보·운행 장애는 아직 확인 불가");
    }

    private void renderRoute(TransitRoute route){
        if(route==null){
            label(R.id.liveRouteSteps).setText(live.routeStatus());
            return;
        }
        StringBuilder steps=new StringBuilder();
        int max=Math.min(route.legs.size(),8);
        for(int i=0;i<max;i++){
            TransitRoute.Leg leg=route.legs.get(i);
            if(steps.length()>0)steps.append('\n');
            steps.append(i+1).append(". ").append(leg.title());
        }
        if(route.legs.size()>max)steps.append("\n그 외 ").append(route.legs.size()-max).append("개 구간");
        label(R.id.liveRouteSteps).setText(steps+" \n※ 경로 약도는 지형·거리 비례가 아니야");
    }

    private void renderVehicle(long now,TransitRoute route,TransitArrival arrival,boolean tracking){
        if(route==null){
            label(R.id.liveNextVehicle).setText("첫 차량 조회 대기");
            label(R.id.liveBoarding).setText("유효한 경로가 없어 도착정보를 확인할 수 없어");
            return;
        }
        TransitRoute.Leg leg=LiveArrivalClient.firstBoarding(route);
        if(leg==null){
            label(R.id.liveNextVehicle).setText("도보 중심 경로");
            label(R.id.liveBoarding).setText("첫 버스·지하철 탑승 구간이 없어");
            return;
        }
        if(arrival==null||!arrival.available){
            label(R.id.liveNextVehicle).setText(leg.startName+" · "+leg.routeName);
            label(R.id.liveBoarding).setText(live.arrivalStatus());
            return;
        }
        long age=now-arrival.fetchedAtMillis;
        int remaining=arrival.seconds-(int)Math.max(0,age/1000L);
        if(age>120_000L||age< -30_000L||remaining<=0){
            label(R.id.liveNextVehicle).setText("차량 도착정보 만료");
            label(R.id.liveBoarding).setText("다음 자동 갱신을 기다려 · 이전 정보를 현재값으로 사용하지 않아");
            return;
        }
        label(R.id.liveNextVehicle).setText(
                leg.startName+" · "+arrival.line+" · 약 "+((remaining+59)/60)+"분 후");
        if(!tracking){
            label(R.id.liveBoarding).setText("현재 위치 기반 판단은 GPS 추적 시작 후 제공돼");
            return;
        }
        BoardingFeasibilityEngine.Assessment estimate=BoardingFeasibilityEngine.assess(
                now,latestLat,latestLon,gpsTime,accuracy,
                leg.startLat,leg.startLon,arrival.fetchedAtMillis,arrival.seconds);
        label(R.id.liveBoarding).setText(estimate.describe()
                +(estimate.hasEstimate()?"\n※ 직선거리 기반 보행 추정, 실제 보행 경로 아님":""));
    }
}
