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
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.mycommute.R;
import com.example.mycommute.alarms.AutoCommute;
import com.example.mycommute.alarms.ReminderScheduler;
import android.location.LocationManager;
import androidx.core.location.LocationManagerCompat;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.ClockPreference;
import com.example.mycommute.data.PinnedRouteRefresh;
import com.example.mycommute.data.Session;
import com.example.mycommute.engine.BoardingFeasibilityEngine;
import com.example.mycommute.engine.CommuteWindow;
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

/** Foreground-only live commute dashboard. External information is always labeled. */
public class TrackingActivity extends AppCompatActivity {
    private Routine routine;
    private CommuteLiveCoordinator live;
    private boolean listening;
    private boolean expandedRouteDetails;
    private String trackingStatus="";
    private boolean trackingStarting;
    private CancellationTokenSource locationToken;
    private final Handler positionHandler=new Handler(Looper.getMainLooper());
    private final Runnable refreshOrigin=new Runnable(){
        @Override public void run(){
            if(live==null)return;
            if(canCheckOrigin()&&!TrackingService.running())readForegroundLocation();
            positionHandler.postDelayed(this,30_000L);
        }
    };
    private double latestLat=Double.NaN,latestLon=Double.NaN;
    private long gpsTime;
    private float accuracy=Float.NaN;
    private final BroadcastReceiver locations=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(TrackingService.ACTION_STATE.equals(intent.getAction())){
                boolean active=intent.getBooleanExtra("active",false);
                trackingStarting=false;
                if(active){
                    trackingStatus=TrackingService.guiding()?"GPS 수신 대기 중":"출발지 500m 이내 위치 확인 중";
                    bindRoutine();
                }else{
                    String reason=intent.getStringExtra("reason");
                    trackingStatus=reason==null||reason.isEmpty()
                            ?"위치 추적 종료됨":reason;
                    if(intent.getBooleanExtra("finished",false)&&live!=null)live.stop();
                }
                render();
                return;
            }
            latestLat=intent.getDoubleExtra("lat",Double.NaN);
            latestLon=intent.getDoubleExtra("lon",Double.NaN);
            gpsTime=intent.getLongExtra("timestamp",0);
            accuracy=intent.getFloatExtra("accuracyMeters",Float.NaN);
            boolean tracking=TrackingService.guiding();
            if(tracking)trackingStatus="위치 수신됨 · 정확도 "+(Float.isFinite(accuracy)?Math.round(accuracy)+"m":"미확인");
            if(live!=null)live.onPosition(latestLat,latestLon,gpsTime,accuracy,tracking);
            render();
        }
    };

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(!Session.signedIn(this)){
            startActivity(new Intent(this,LoginActivity.class));finish();return;
        }
        String requested=getIntent().getStringExtra("routineId");
        if(requested!=null&&!TrackingService.running())
            RoutineStore.select(this,requested);
        setContentView(R.layout.activity_tracking);Navigation.bind(this);
        findViewById(R.id.liveStartTracking).setOnClickListener(v->startTracking());
        findViewById(R.id.liveStopTracking).setOnClickListener(v->stopTracking());
        findViewById(R.id.liveRefresh).setOnClickListener(v->{
            if(live!=null)live.refreshNow();
            if(canCheckOrigin())readForegroundLocation();
            render();
        });
        findViewById(R.id.liveOpenMap).setOnClickListener(v->
                startActivity(new Intent(this,MapActivity.class).putExtra("routineId",routine.id)));
        findViewById(R.id.liveRouteSteps).setOnClickListener(v->{
            expandedRouteDetails=!expandedRouteDetails;
            render();
        });
    }

    @Override protected void onResume(){
        super.onResume();
        if(!Session.signedIn(this))return;
        IntentFilter trackingFilter=new IntentFilter(TrackingService.ACTION_LOCATION);
        trackingFilter.addAction(TrackingService.ACTION_STATE);
        if(Build.VERSION.SDK_INT>=33)
            registerReceiver(locations,trackingFilter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(locations,trackingFilter);
        listening=true;
        gpsTime=TrackingService.guiding()?RoutineStore.prefs(this).getLong("lastFixTime",0):0;
        latestLat=RoutineStore.prefs(this).getFloat("lastLat",Float.NaN);
        latestLon=RoutineStore.prefs(this).getFloat("lastLon",Float.NaN);
        accuracy=RoutineStore.prefs(this).getFloat("lastAccuracy",Float.NaN);
        bindRoutine();
        AutoCommute.resume(this,true);
        positionHandler.post(refreshOrigin);
        if(!RoutineStore.showOriginNotice(this)&&!AutoCommute.enabled(this)&&AutoCommute.hasLocation(this)
                &&!TrackingService.running()&&live.activeWindow()){
            readForegroundLocation();
        }
        render();
    }

    private void bindRoutine(){
        String active=TrackingService.activeRoutineId();
        Routine display=active==null?RoutineStore.read(this):RoutineStore.read(this,active);
        if(live!=null&&display.id.equals(routine.id))return;
        if(routine!=null&&!display.id.equals(routine.id))gpsTime=0;
        if(live!=null)live.stop();
        routine=display;
        live=new CommuteLiveCoordinator(this,routine,Session.accountId(this),this::render);
        live.start();
        boolean waiting=TrackingService.running()&&!TrackingService.guiding();
        live.onPosition(latestLat,latestLon,waiting?0:gpsTime,accuracy,TrackingService.guiding());
    }

    @Override protected void onPause(){
        positionHandler.removeCallbacks(refreshOrigin);
        if(locationToken!=null){locationToken.cancel();locationToken=null;}
        if(live!=null){live.stop();live=null;}
        if(listening){unregisterReceiver(locations);listening=false;}
        super.onPause();
    }

    private boolean canCheckOrigin(){
        return routine!=null&&routine.isConfigured()&&routine.validCoordinates()
                &&RoutineStore.showOriginNotice(this)&&!TrackingService.guiding()
                &&AutoCommute.hasLocation(this)&&isDeviceLocationEnabled();
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
            label(R.id.livePosition).setText("위치 권한 필요");
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
                                TrackingService.guiding());
                        render();
                    })
                    .addOnFailureListener(this,error->{
                        if(locationToken!=token||live==null)return;
                        label(R.id.livePosition).setText("위치 확인 불가");
                    });
        }catch(SecurityException error){
            label(R.id.livePosition).setText("위치 권한 필요");
        }
    }

    private void stopTracking(){
        TrackingService.stopTrip(this);
        trackingStarting=false;
        trackingStatus="위치 추적 종료";
        toast("위치 추적 종료");
        if(live!=null)live.onPosition(latestLat,latestLon,gpsTime,accuracy,false);
        render();
    }

    private void startTracking(){
        if(!isDeviceLocationEnabled()){
            trackingStarting=false;
            trackingStatus="기기 위치 서비스(GPS) 꺼짐";
            render();
            toast(trackingStatus);
            return;
        }
        if(routine==null||!routine.isConfigured()){
            toast("출퇴근 경로 등록 필요");return;
        }
        if(!routine.validCoordinates()){
            toast("출발지 위치 설정 필요");return;
        }
        if(!routine.apiBased&&!RoutePlanner.select(routine).available){
            toast("경로 재설정 필요");return;
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
            trackingStarting=true;
            trackingStatus="위치 추적 서비스 시작 요청 중";
            ContextCompat.startForegroundService(this,new Intent(this,TrackingService.class)
                    .putExtra("routineId",routine.id));
            render();
        }catch(Exception e){
            trackingStarting=false;
            trackingStatus="위치 추적 시작 실패: "+(e.getMessage()==null
                    ?e.getClass().getSimpleName():e.getMessage());
            render();
            toast(trackingStatus);
        }
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        boolean granted=grants.length>0&&
                (grants[0]==PackageManager.PERMISSION_GRANTED||
                 grants.length>1&&grants[1]==PackageManager.PERMISSION_GRANTED);
        if(code==308&&granted)startTracking();
    }

    private String clock(long timestamp){
        return ClockPreference.time(this,timestamp);
    }
    private TextView label(int id){return findViewById(id);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}

    private boolean isDeviceLocationEnabled() {
        LocationManager manager = getSystemService(LocationManager.class);
        return manager != null && LocationManagerCompat.isLocationEnabled(manager);
    }

    private void render(){
        if(live==null||routine==null||isFinishing()||isDestroyed())return;
        long now=System.currentTimeMillis();
        boolean running=TrackingService.running();
        boolean tracking=TrackingService.guiding();
        String direction=RoutineStore.RETURN.equals(RoutineStore.directionOf(routine.id))?"퇴근길":"출근길";
        label(R.id.liveRouteTitle).setText(routine.isConfigured()
                ?direction+" · "+routine.name+"\n"+routine.startName+" → "+routine.endName
                :direction+" · 등록된 경로 없음");
        renderOriginNotice(now);
        findViewById(R.id.liveStartTracking).setEnabled(!running&&!trackingStarting);
        findViewById(R.id.liveStartTracking).setVisibility(running?View.GONE:View.VISIBLE);
        TextView stop=findViewById(R.id.liveStopTracking);
        stop.setVisibility(running?View.VISIBLE:View.GONE);
        stop.setText(tracking?"이번 이동 추적 종료":"출발지 확인 중지");
        TransitRoute route=live.route();
        TransitArrival vehicle=live.arrival();
        long target=live.targetArrivalMillis();
        TextView status=label(R.id.liveSyncStatus);
        if(!routine.isConfigured())
            UiNotice.show(status,UiNotice.State.EMPTY,"출퇴근 경로 등록 필요");
        else if(route==null)
            UiNotice.show(status,live.refreshingRoute()?UiNotice.State.LOADING:UiNotice.State.UNAVAILABLE,
                    live.refreshingRoute()?"상세 경로 갱신 중…"
                            :PinnedRouteRefresh.canRefresh(routine)?"상세 경로 확인 불가":"루틴에서 상세 경로 선택");
        else UiNotice.show(status,UiNotice.State.IDLE,"");
        boolean loading=route!=null&&(
                (vehicle==null&&live.boardingLegIndex()>=0)||
                (live.weather()==null&&live.weatherStatus().contains("조회 중")));
        findViewById(R.id.liveLoading).setVisibility(loading?View.VISIBLE:View.GONE);
        RouteSchematicView schematic=findViewById(R.id.liveSchematic);
        schematic.setRoute(route);
        StagePositionMatcher.Match stage=live.stage();
        schematic.setCurrentLeg(stage!=null&&stage.known()?stage.legIndex:-1);
        // A recent, accurate location matched to an unambiguous route leg.
        // GPS data may be a foreground one-shot fix; continuous tracking is optional.
        schematic.setGpsPosition(latestLat,latestLon,stage!=null&&stage.known());
        TextView stageLabel=label(R.id.liveCurrentStage);
        if(stage!=null&&stage.known()) {
            stageLabel.setVisibility(View.VISIBLE);
            stageLabel.setText("현재 구간 · "+(stage.legIndex+1)+"번째 구간");
        }else stageLabel.setVisibility(View.GONE);
        renderDeparture(now,target,route,vehicle);
        renderWeather();
        renderRoute(route);
        renderVehicle(now,route,vehicle,tracking);
        String gpsState=tracking?(TrackingService.automatic()?"출퇴근 자동 추적 중":"위치 추적 중")
                :AutoCommute.enabled(this)?"출퇴근 자동 추적 대기":"위치 추적 꺼짐";
        if(gpsTime>0&&tracking){
            long seconds=Math.max(0,(now-gpsTime)/1000);
            gpsState=seconds<=90?gpsState+" · 최근 "+seconds+"초 전":
                    "위치 갱신 대기";
        }
        if(!tracking&&!isDeviceLocationEnabled()){
            gpsState="기기 위치 서비스(GPS) 꺼짐";
        }
        if(running&&!tracking)gpsState="출발지 500m 이내 위치 확인 중";
        else if(trackingStarting&&!tracking)gpsState=trackingStatus;
        else if(!trackingStatus.isEmpty()&&!tracking)gpsState=trackingStatus;
        else if(tracking&&!trackingStatus.isEmpty()&&gpsTime<=0)gpsState=trackingStatus;
        label(R.id.livePosition).setText(gpsState);
    }

    private void renderOriginNotice(long now){
        TextView notice=label(R.id.liveOriginNotice);
        String message="";
        if(routine.isConfigured()&&routine.validCoordinates()
                &&RoutineStore.showOriginNotice(this)&&!TrackingService.guiding()){
            if(!AutoCommute.hasLocation(this))message="현재 위치 확인에 위치 권한이 필요합니다.";
            else if(CommuteWindow.usableStartFix(now,gpsTime,accuracy)&&isDeviceLocationEnabled()){
                float distance=TrackingService.distanceFromStart(routine,latestLat,latestLon);
                if(Float.isFinite(distance)&&!CommuteWindow.withinStartRadius(distance)){
                    boolean returning=RoutineStore.RETURN.equals(RoutineStore.directionOf(routine.id));
                    message=returning?"현재 퇴근 위치가 아닙니다.":"현재 출근 위치가 아닙니다.";
                }
            }
        }
        if(!message.contentEquals(notice.getText()))
            UiNotice.show(notice,message.isEmpty()?UiNotice.State.IDLE:UiNotice.State.UNAVAILABLE,message);
    }

    private void renderDeparture(long now,long target,TransitRoute route,TransitArrival vehicle){
        if(!routine.isConfigured()||target<=0){
            label(R.id.liveDepartureHeadline).setText("경로 미설정");
            label(R.id.liveDepartureDetail).setText("출퇴근 루틴 등록 필요");
            label(R.id.liveArrivalPlan).setText("");
            return;
        }
        if(!live.activeWindow()){
            int duration=ReminderScheduler.durationMinutes(this,routine);
            long depart=CommuteWindow.nextDeparture(routine,duration,0,now);
            if(depart>0){
                label(R.id.liveDepartureHeadline).setText("다음 출발 "+clock(depart));
                target=depart+(duration+routine.marginMinutes)*60_000L;
            }else label(R.id.liveDepartureHeadline).setText("예정 출발 미확인");
            label(R.id.liveDepartureDetail).setText("목표 도착 "+clock(target));
            label(R.id.liveArrivalPlan).setText("");
            return;
        }
        if(route==null){
            label(R.id.liveDepartureHeadline).setText("출발시각 미확인");
            label(R.id.liveDepartureDetail).setText("경로 갱신 필요");
            label(R.id.liveArrivalPlan).setText("목표 도착 "+clock(target));
            return;
        }
        int walk=live.walkSeconds();
        LiveDepartureAdvisor.Advice advice=LiveDepartureAdvisor.compute(
                now,target,routine.marginMinutes,route,walk,vehicle);
        boolean transfer=live.boardingLegIndex()>LiveArrivalClient.firstBoardingIndex(route);
        label(R.id.liveDepartureHeadline).setText(
                transfer?"환승 구간":advice.headline);
        label(R.id.liveDepartureDetail).setText(
                "계획 출발 "+clock(advice.planDepartureMillis)
                +" · 예상 이동 "+TransitRoute.minutes(route.totalSeconds)+"분");
        label(R.id.liveArrivalPlan).setText("목표 "+clock(target)
                +" · 지금 출발 시 "+clock(advice.planArrivalIfLeavingNowMillis)+" 예상");
    }

    private void renderWeather(){
        KmaWeatherClient.Report report=live.weather();
        if(report==null){
            label(R.id.liveWeather).setText("—");
            label(R.id.liveWeatherDetails).setText("");
            String status=live.weatherStatus();
            label(R.id.liveWeatherTime).setText(
                    status.contains("조회 중")?"날씨 확인 중…":
                    status.contains("대기")?"날씨 확인 중…":"날씨 확인 불가");
        }else{
            label(R.id.liveWeather).setText(report.temperatureText());
            label(R.id.liveWeatherDetails).setText(report.conditionText());
            label(R.id.liveWeatherTime).setText("예보 "+ClockPreference.monthDayTime(this,report.forecastAt));
        }
        java.util.List<String> notices=new java.util.ArrayList<>();
        if(report!=null){
            if(report.conditions.rain)notices.add("비 예보 · 우산 준비");
            if(report.conditions.lowTemperature)notices.add("영하 기온 · 보온 주의");
        }
        if(notices.isEmpty())
            notices.add(report==null?"기상 상황 미확인":"예보상 주의사항 없음");
        label(R.id.liveAlerts).setText(android.text.TextUtils.join("\n",notices));
    }

    private void renderRoute(TransitRoute route){
        if(route==null){
            label(R.id.liveRouteSteps).setText("경로 갱신 필요");return;
        }
        String overview="전체 "+route.legs.size()+"구간 · 도보 "+
                TransitRoute.minutes(route.walkSeconds)+"분 · 환승 "+route.transfers+"회";
        if(!expandedRouteDetails){
            label(R.id.liveRouteSteps).setText(overview+" · 상세 보기");
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
        steps.append("\n접기");
        label(R.id.liveRouteSteps).setText(steps.toString());
    }

    private void renderVehicle(long now,TransitRoute route,TransitArrival arrival,boolean tracking){
        TextView station=label(R.id.liveNextVehicle);
        TextView mode=label(R.id.liveVehicleMode);
        TextView eta=label(R.id.liveVehicleEta);
        TextView next=label(R.id.liveVehicleNext);
        TextView status=label(R.id.liveBoarding);
        TextView walkLabel=label(R.id.liveVehicleWalk);
        TextView slackLabel=label(R.id.liveVehicleSlack);
        View etaRow=findViewById(R.id.liveVehicleEtaRow);
        View etaDivider=findViewById(R.id.liveVehicleEtaDivider);
        View nextColumn=findViewById(R.id.liveVehicleNextColumn);
        View auxRow=findViewById(R.id.liveVehicleAuxRow);
        // Suppress unavailable arrival time areas entirely, not just the text.
        etaRow.setVisibility(View.GONE);
        etaDivider.setVisibility(View.GONE);
        nextColumn.setVisibility(View.GONE);
        auxRow.setVisibility(View.GONE);
        mode.setText("교통"); station.setText("정보 확인 중");
        // No fabricated ETA or placeholder when the provider did not supply one.
        eta.setText("");next.setText("");
        status.setText("도착정보 확인 중");
        walkLabel.setText("");slackLabel.setText("");
        if(route==null){
            station.setText("경로 갱신 필요");
            status.setText("경로 정보 확인 필요");
            return;
        }
        int index=live.boardingLegIndex();
        if(index<0||index>=route.legs.size()){
            mode.setText("도보");station.setText("도보 이동");
            status.setText("다음 탑승 차량 없음");
            return;
        }
        TransitRoute.Leg leg=route.legs.get(index);
        mode.setText("BUS".equals(leg.mode)?"버스":"SUBWAY".equals(leg.mode)?"지하철":"교통");
        station.setText(leg.startName+" · "+leg.routeName);
        if(arrival!=null&&arrival.statusOnly){
            if(arrival.hasFreshStatus(now)){
                status.setText(arrival.statusText);
                eta.setText("");
                next.setText("");
                if(arrival.direction!=null&&!arrival.direction.isEmpty()){
                    walkLabel.setText(arrival.direction);
                    auxRow.setVisibility(View.VISIBLE);
                }
            }else{
                status.setText("상태정보 만료 · 갱신 대기");
            }
            return;
        }
        if(arrival==null||!arrival.available){
            eta.setText("");
            status.setText(arrival==null?"도착정보 확인 중":"실시간 도착정보 없음");
            return;
        }
        int remaining=arrival.currentSeconds(now);
        if(remaining<=0){
            eta.setText("");status.setText("이전 도착 정보 만료");
            return;
        }
        int second=arrival.nextRemainingSeconds(now);
        eta.setText(remaining<60?"1분 이내":((remaining+59)/60)+"분 후");
        etaRow.setVisibility(View.VISIBLE);
        etaDivider.setVisibility(View.VISIBLE);
        if(second>remaining){
            next.setText(((second+59)/60)+"분 후");
            nextColumn.setVisibility(View.VISIBLE);
        }
        int walk=live.walkSeconds();
        if(walk>=0){
            int slack=remaining-walk-120;
            String message=slack>=180?"승차 여유 있음":
                    slack>=0?"탑승 시간 촉박":"현재 차량 탑승 어려움";
            status.setText(message+(tracking?"":" · 계획 기준"));
            walkLabel.setText("승차지까지 "+((walk+59)/60)+"분");
            auxRow.setVisibility(View.VISIBLE);
            int minute=(int)Math.ceil(Math.abs(slack)/60.0);
            slackLabel.setText("여유 "+(slack>=0?"+":"-")+minute+"분");
            return;
        }
        if(!tracking){
            status.setText("승차지 이동시간 미확인");
            return;
        }
        BoardingFeasibilityEngine.Assessment estimate=BoardingFeasibilityEngine.assess(
                now,latestLat,latestLon,gpsTime,accuracy,
                leg.startLat,leg.startLon,arrival.fetchedAtMillis,
                arrival.seconds-(int)Math.max(0,(now-arrival.fetchedAtMillis)/1000L)>0
                        ?arrival.seconds:arrival.nextSeconds);
        status.setText(estimate.describe());
    }


}
