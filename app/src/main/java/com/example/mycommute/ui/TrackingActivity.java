package com.example.mycommute.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.mycommute.R;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.engine.BoardingFeasibilityEngine;
import com.example.mycommute.engine.LiveDepartureAdvisor;
import com.example.mycommute.engine.StagePositionMatcher;
import com.example.mycommute.engine.RoutePlanner;
import com.example.mycommute.location.TrackingService;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.network.LiveArrivalClient;
import com.example.mycommute.network.KmaWeatherClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import android.location.Location;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Foreground-only live commute dashboard. External information is always labeled. */
public class TrackingActivity extends AppCompatActivity {
    private Routine routine;
    private CommuteLiveCoordinator live;
    private boolean listening;
    private boolean expandedRouteDetails;
    private CancellationTokenSource locationToken;
    private double latestLat=Double.NaN,latestLon=Double.NaN;
    private long gpsTime;
    private float accuracy=Float.NaN;
    private final BroadcastReceiver locations=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            latestLat=intent.getDoubleExtra("lat",Double.NaN);
            latestLon=intent.getDoubleExtra("lon",Double.NaN);
            gpsTime=intent.getLongExtra("timestamp",0);
            accuracy=intent.getFloatExtra("accuracyMeters",Float.NaN);
            if(live!=null)live.onPosition(latestLat,latestLon,gpsTime,accuracy,
                    RoutineStore.prefs(TrackingActivity.this).getBoolean("tracking_active",false));
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
        findViewById(R.id.liveRouteSteps).setOnClickListener(v->{
            expandedRouteDetails=!expandedRouteDetails;
            render();
        });
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
        live.onPosition(latestLat,latestLon,gpsTime,accuracy,
                RoutineStore.prefs(this).getBoolean("tracking_active",false));
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
                ==PackageManager.PERMISSION_GRANTED||
           ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)
                ==PackageManager.PERMISSION_GRANTED) {
            readForegroundLocation();
        }else if(!RoutineStore.prefs(this).getBoolean("foreground_location_asked",false)){
            RoutineStore.prefs(this).edit().putBoolean("foreground_location_asked",true).apply();
            ActivityCompat.requestPermissions(this,new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},309);
        }
        render();
    }

    @Override protected void onPause(){
        if(locationToken!=null){locationToken.cancel();locationToken=null;}
        if(live!=null){live.stop();live=null;}
        if(listening){unregisterReceiver(locations);listening=false;}
        super.onPause();
    }

    /**
     * Single foreground location observation for preview; never starts the
     * persistent GPS service and never runs after the screen is paused.
     */
    private void readForegroundLocation(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED&&
           ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED){
            label(R.id.livePosition).setText("현재 위치 권한 없음 · 위치 추적과 별도로 위치 권한이 필요해");
            return;
        }
        if(locationToken!=null)locationToken.cancel();
        final CancellationTokenSource token=new CancellationTokenSource();
        locationToken=token;
        try{
            LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,token.getToken())
                    .addOnSuccessListener(this,fix->{
                        if(locationToken!=token||live==null||fix==null)return;
                        latestLat=fix.getLatitude();latestLon=fix.getLongitude();
                        gpsTime=fix.getTime();
                        accuracy=fix.hasAccuracy()?fix.getAccuracy():Float.NaN;
                        live.onPosition(latestLat,latestLon,gpsTime,accuracy,
                                RoutineStore.prefs(this).getBoolean("tracking_active",false));
                        render();
                    })
                    .addOnFailureListener(this,error->{
                        if(locationToken!=token||live==null)return;
                        label(R.id.livePosition).setText("현재 GPS 측정 실패 · "+error.getClass().getSimpleName());
                    });
        }catch(SecurityException error){
            label(R.id.livePosition).setText("현재 위치 권한이 필요해");
        }
    }

    private void stopTracking(){
        stopService(new Intent(this,TrackingService.class));
        RoutineStore.prefs(this).edit().putBoolean("tracking_active",false).apply();
        toast("위치 추적을 종료했어");
        if(live!=null)live.onPosition(latestLat,latestLon,gpsTime,accuracy,false);
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
            readForegroundLocation();
            toast("위치 추적을 시작했어");
        }catch(Exception e){toast("위치 추적 실패: "+e.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        boolean granted=grants.length>0&&
                (grants[0]==PackageManager.PERMISSION_GRANTED||
                 grants.length>1&&grants[1]==PackageManager.PERMISSION_GRANTED);
        if(code==309&&granted)readForegroundLocation();
        if(code==308&&granted){
            readForegroundLocation();
            startTracking();
        }
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
        StagePositionMatcher.Match stage=live.stage();
        schematic.setCurrentLeg(stage!=null&&stage.known()?stage.legIndex:-1);
        label(R.id.liveCurrentStage).setText(
                stage==null?"현재 단계: 위치 추적을 켜면 경로 주변 위치를 추정해":
                        "현재 단계: "+stage.description);
        renderDeparture(now,target,route,vehicle);
        renderWeather();
        renderRoute(route);
        renderVehicle(now,route,vehicle,tracking);
        String gpsState=tracking?"GPS 추적 중":"GPS 추적 중지됨";
        if(gpsTime>0){
            gpsState=tracking?"GPS 추적 중":"현재 위치 1회 확인 · 추적은 꺼짐";
            String detail=Float.isFinite(accuracy)
                    ?String.format(Locale.KOREA," · 정확도 약 %.0fm",accuracy):" · 정확도 미확인";
            long elapsed=Math.max(0,(now-gpsTime)/1000L);
            gpsState+=" · 마지막 측정 "+clock(gpsTime)+" ("+elapsed+"초 전)"+detail;
            if(elapsed>90)gpsState+=" · 오래된 위치: 현재 단계는 판단 보류";
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
            label(R.id.liveDepartureDetail).setText("이동 시간대가 되면 경로·차량 정보를 조회해");
            label(R.id.liveArrivalPlan).setText("목표 도착 "+clock(target)+" · 여유 "+routine.marginMinutes+"분");
            return;
        }
        if(route==null){
            label(R.id.liveDepartureHeadline).setText("출발시각 계산 대기");
            label(R.id.liveDepartureDetail).setText("새 대중교통 경로를 확인하지 못해 권장시각을 계산할 수 없어");
            label(R.id.liveArrivalPlan).setText("목표 도착 "+clock(target));
            return;
        }
        // The saved route includes the *planned* total time. Real-time data is
        // incorporated only into first-boarding reachability, not entire-trip ETA.
        int walk=live.walkSeconds();
        LiveDepartureAdvisor.Advice advice=LiveDepartureAdvisor.compute(
                now,target,routine.marginMinutes,route,walk,vehicle);
        boolean transfer=live.boardingLegIndex()>LiveArrivalClient.firstBoardingIndex(route);
        String headline=transfer?"환승 단계 · 다음 차량 확인":advice.headline;
        label(R.id.liveDepartureHeadline).setText(headline);
        label(R.id.liveDepartureDetail).setText("계획 기준 출발 권장 "+clock(advice.planDepartureMillis)
                +" · 총 예상 이동 "+TransitRoute.minutes(route.totalSeconds)+"분"
                +(advice.vehicleVerified&&!transfer?"\n"+advice.note:"\n실시간 첫 차량 정보는 아래에서 확인해"));
        label(R.id.liveArrivalPlan).setText("목표 도착 "+clock(target)
                +" · 지금부터 계획 이동 시 "+clock(advice.planArrivalIfLeavingNowMillis)
                +" 도착 추정 (실제 운행·대기시간 미반영)");
    }

    private void renderWeather(){
        KmaWeatherClient.Report report=live.weather();
        if(report==null){
            label(R.id.liveWeather).setText("기온 미확인");
            label(R.id.liveWeatherDetails).setText("날씨 정보 조회 대기");
            label(R.id.liveWeatherTime).setText(live.weatherStatus());
        }else{
            label(R.id.liveWeather).setText(report.temperatureText());
            label(R.id.liveWeatherDetails).setText(report.conditionText());
            label(R.id.liveWeatherTime).setText("기상청 "+report.forecastAt+" 예보");
        }
        java.util.List<String> notices=new java.util.ArrayList<>();
        if(report==null)notices.add("기상정보를 확인하지 못했어");
        else {
            if(report.conditions.rain)notices.add("강수 예상 · 도보 이동에 주의");
            if(report.conditions.lowTemperature)notices.add("영하 기온 예상 · 보온에 주의");
        }
        TransitArrival vehicle=live.arrival();
        if(vehicle!=null&&vehicle.statusOnly){
            if(vehicle.hasFreshStatus(System.currentTimeMillis()))
                notices.add("열차 도착예정시간 미제공 · 최신 상태만 확인");
            else notices.add("열차 상태정보 만료 · 새 조회 대기");
        }else if(vehicle!=null&&!vehicle.available){
            if(vehicle.message.contains("HTTP 403")||
                    vehicle.message.contains("HTTP 401"))
                notices.add("버스·지하철 도착정보 접근 오류");
            else notices.add("실시간 차량 도착정보 미확인");
        }else if(vehicle!=null&&vehicle.available
                &&vehicle.currentSeconds(System.currentTimeMillis())<=0){
            notices.add("차량 도착정보 만료 · 다음 조회 대기");
        }
        if(live.route()==null)
            notices.add("상세 경로 미보유 · 루틴에서 경로 재검색 필요");
        if(notices.isEmpty())notices.add("연결된 예보·교통정보에서 추가 주의사항이 확인되지 않았어");
        label(R.id.liveAlerts).setText(android.text.TextUtils.join("\n",notices));
    }

    private void renderRoute(TransitRoute route){
        if(route==null){
            label(R.id.liveRouteSteps).setText(live.routeStatus());return;
        }
        String overview="전체 "+route.legs.size()+"구간 · 도보 "+
                TransitRoute.minutes(route.walkSeconds)+"분 · 환승 "+route.transfers+"회";
        if(!expandedRouteDetails){
            label(R.id.liveRouteSteps).setText(overview+" · 구간 목록 펼치기");
            return;
        }
        StagePositionMatcher.Match stage=live.stage();
        int focused=stage!=null&&stage.known()?stage.legIndex:-1;
        StringBuilder steps=new StringBuilder(overview);
        int max=Math.min(route.legs.size(),16);
        for(int i=0;i<max;i++){
            TransitRoute.Leg leg=route.legs.get(i);
            steps.append("\n").append(i==focused?"▶ ":"   ").append(i+1)
                    .append(". ").append(leg.title());
        }
        if(route.legs.size()>max)steps.append("\n그 외 ").append(route.legs.size()-max).append("개 구간");
        steps.append("\n구간 목록 접기");
        label(R.id.liveRouteSteps).setText(steps.toString());
    }

    private void renderVehicle(long now,TransitRoute route,TransitArrival arrival,boolean tracking){
        TextView diagnostic=label(R.id.liveArrivalDiagnostic);
        diagnostic.setVisibility(View.GONE);
        if(route==null){
            label(R.id.liveNextVehicle).setText("차량 조회 대기");
            label(R.id.liveBoarding).setText("조회 가능한 대중교통 경로가 없어");return;
        }
        int index=live.boardingLegIndex();
        if(index<0||index>=route.legs.size()){
            label(R.id.liveNextVehicle).setText("버스·지하철 구간 없음");
            label(R.id.liveBoarding).setText("도보 중심 경로야");return;
        }
        TransitRoute.Leg leg=route.legs.get(index);
        if(arrival!=null&&arrival.statusOnly){
            label(R.id.liveNextVehicle).setText(leg.startName+" · "+leg.routeName);
            if(arrival.hasFreshStatus(now)){
                label(R.id.liveBoarding).setText(
                        arrival.direction+"\n조회 당시 "+arrival.statusText
                        +" · "+clock(arrival.statusObservedMillis)+" 기준"
                        +"\n도착예정시간 미제공 · 현재 상태·탑승 가능 여부는 확인되지 않아");
            }else{
                label(R.id.liveBoarding).setText(
                        "열차 상태정보가 오래됐어 · 최신 조회 대기"
                        +"\n마지막 기록 "+clock(arrival.statusObservedMillis));
            }
            return;
        }
        if(arrival==null||!arrival.available){
            label(R.id.liveNextVehicle).setText(leg.startName+" · "+leg.routeName);
            if(arrival==null){
                label(R.id.liveBoarding).setText("실시간 도착정보 조회 중");
            }else{
                label(R.id.liveBoarding).setText(arrival.message.contains("HTTP 403")
                        ?"실시간 도착정보 접근 거부 · 아래 연결 진단 확인"
                        :"도착예정시간을 확인할 수 없어");
                diagnostic.setVisibility(View.VISIBLE);
                diagnostic.setText("연결 진단 · "+arrival.message);
            }
            return;
        }
        int remaining=arrival.currentSeconds(now);
        if(remaining<=0){
            label(R.id.liveNextVehicle).setText("차량 도착정보 만료");
            label(R.id.liveBoarding).setText("현재 차량이 지나갔거나 데이터가 오래됐어 · 자동 재조회 중");
            return;
        }
        String primary=leg.startName+" · "+arrival.line+" · 약 "+((remaining+59)/60)+"분 후";
        int second=arrival.nextRemainingSeconds(now);
        if(second>remaining)primary+="\n다음 차량 약 "+((second+59)/60)+"분 후";
        label(R.id.liveNextVehicle).setText(primary);
        int walk=live.walkSeconds();
        if(walk>=0){
            int slack=remaining-walk-120;
            String message=slack>=180?"승차지 접근 시간 여유 추정":
                    slack>=0?"탑승이 촉박할 수 있어":
                            "현재 도착예정 차량 탑승이 어려울 수 있어";
            label(R.id.liveBoarding).setText(message+"\n승차지까지 "+
                    ((walk+59)/60)+"분 · 여유 "+(slack>=0?"+":"")+
                    ((slack>=0?slack+59:slack-59)/60)+"분\n"+live.walkingStatus()
                    +(tracking?"":"\nGPS 미사용 · 루틴의 계획 보행시간 기준"));
            return;
        }
        if(!tracking){
            label(R.id.liveBoarding).setText("정류장까지 보행시간 확인 불가 · 위치 추적 시작 후 재평가");
            return;
        }
        BoardingFeasibilityEngine.Assessment estimate=BoardingFeasibilityEngine.assess(
                now,latestLat,latestLon,gpsTime,accuracy,
                leg.startLat,leg.startLon,arrival.fetchedAtMillis,
                // Use the ORIGINAL provider ETA at its fetch timestamp.
                // 'remaining' has already been age-corrected and would double-decrement.
                arrival.seconds-(int)Math.max(0,(now-arrival.fetchedAtMillis)/1000L)>0
                        ?arrival.seconds:arrival.nextSeconds);
        label(R.id.liveBoarding).setText(estimate.describe()
                +(estimate.hasEstimate()?"\n※ 보행 경로 미확보 시 GPS 직선거리 기반 근사":""));
    }


}
