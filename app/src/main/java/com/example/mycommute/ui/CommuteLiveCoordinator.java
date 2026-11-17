package com.example.mycommute.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.TransitRouteMemory;
import com.example.mycommute.engine.CommuteWindow;
import com.example.mycommute.engine.RouteSignature;
import com.example.mycommute.engine.StagePositionMatcher;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.network.KmaWeatherClient;
import com.example.mycommute.network.LiveArrivalClient;
import com.example.mycommute.network.TmapTransitClient;
import com.example.mycommute.network.TmapWalkingClient;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Foreground-only polling coordinator. No server calls when the screen is stopped.
 * TMAP routes are rate-limited separately from short-lived first-boarding arrivals.
 */
final class CommuteLiveCoordinator {
    interface Listener { void changed(); }

    private static final long TICK_MS=60_000L;
    private static final long ROUTE_TTL_MS=2*60*60_000L;
    private static final long ROUTE_RETRY_MS=2*60*60_000L;
    private static final long ARRIVAL_RETRY_MS=60_000L;
    private static final long WEATHER_RETRY_MS=60*60_000L;
    private static final int MAX_LOCAL_TMAP_CALLS_PER_DAY=3;
    private static final long WALK_RETRY_MS=5*60_000L;
    // Process-local cache prevents repeated weather calls when moving between tabs.
    private static final class WeatherCache {
        final String key;
        final KmaWeatherClient.Report report;
        final long fetchedAt;
        WeatherCache(String key,KmaWeatherClient.Report report,long at){
            this.key=key;this.report=report;this.fetchedAt=at;
        }
    }
    private static WeatherCache cachedWeather;

    private final Context context;
    private final Routine routine;
    private final String userId;
    private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private boolean running;
    private int generation;
    private boolean loadingRoute,loadingArrival,loadingWeather,loadingWalk;
    private int boardingLegIndex=-1;
    private int walkSeconds=-1;
    private long walkingRequestedAt,walkingFetchedAt;
    private StagePositionMatcher.Match stage;
    private double currentLat=Double.NaN,currentLon=Double.NaN;
    private float currentAccuracy=Float.NaN;
    private long currentFixTime;
    private boolean gpsTracking;
    private long routeFetchedAt,routeRequestedAt,arrivalRequestedAt,weatherRequestedAt;
    private TransitRoute route;
    private TransitArrival arrival;
    private KmaWeatherClient.Report weather;
    private String routeStatus="저장된 경로의 실시간 조회를 준비 중이야";
    private String arrivalStatus="첫 차량 도착정보 대기 중";
    private String weatherStatus="기상정보 대기 중";
    private String walkingStatus="정류장 도보시간 확인 전";

    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(!running)return;
            long now=System.currentTimeMillis();
            // The dashboard itself is a valid reason to display weather and
            // next-vehicle information, even before the commute window starts.
            loadRoute(false);
            loadArrival(false);
            loadWeather(false);
            if(CommuteWindow.active(routine,now))loadWalking(false);
            notifyChange();
            handler.postDelayed(this,TICK_MS);
        }
    };

    CommuteLiveCoordinator(Context context,Routine routine,String userId,Listener listener){
        this.context=context.getApplicationContext();this.routine=routine;this.userId=userId;
        this.listener=listener;
    }

    void start(){
        if(running)return;
        running=true;generation++;
        if(!routine.isConfigured()||!routine.validCoordinates()){
            routeStatus="출발지·도착지와 반복 요일을 저장해야 해";
            weatherStatus="루틴 미설정";
            notifyChange();return;
        }
        TransitRoute cached=TransitRouteMemory.selected(userId,routine);
        if(cached!=null){
            route=cached;routeFetchedAt=System.currentTimeMillis();
            routeStatus="이번 세션의 선택 경로 미리보기 · 현재 조회 결과";
        } else {
            routeStatus=CommuteWindow.active(routine,System.currentTimeMillis())
                    ?"이동 시간대 경로 조회 준비 중"
                    :"지금은 등록한 이동 시간대가 아니야 · 자동 조회 대기 중";
        }
        loadWeather(false);
        loadRoute(false);
        loadArrival(false);
        if(CommuteWindow.active(routine,System.currentTimeMillis()))loadWalking(false);
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker,TICK_MS);
        notifyChange();
    }

    void stop(){
        running=false;generation++;
        handler.removeCallbacks(ticker);
    }

    /** Manual update is available outside the scheduled window, still rate-limited. */
    void refreshNow(){
        if(!running)return;
        loadRoute(true);
        loadArrival(true);
        loadWeather(true);
        loadWalking(true);
        notifyChange();
    }

    TransitRoute route(){return route;}
    TransitArrival arrival(){return arrival;}
    KmaWeatherClient.Report weather(){return weather;}
    String routeStatus(){return routeStatus;}
    String arrivalStatus(){return arrivalStatus;}
    String weatherStatus(){return weatherStatus;}
    boolean activeWindow(){return CommuteWindow.active(routine,System.currentTimeMillis());}
    long targetArrivalMillis(){return CommuteWindow.nearestTarget(routine,System.currentTimeMillis());}
    long routeFetchedAtMillis(){return routeFetchedAt;}
    StagePositionMatcher.Match stage(){return stage;}
    int boardingLegIndex(){return boardingLegIndex;}
    int walkSeconds(){
        if(walkingFetchedAt>0&&System.currentTimeMillis()-walkingFetchedAt<10*60_000L&&walkSeconds>=0)
            return walkSeconds;
        if(route==null||boardingLegIndex<0)return -1;
        int sec=0;
        for(int i=0;i<boardingLegIndex;i++)
            if("WALK".equals(route.legs.get(i).mode))
                sec+=route.legs.get(i).seconds;
        return sec>0?sec:-1;
    }
    String walkingStatus(){return walkingStatus;}

    void onPosition(double lat,double lon,long fixTime,float accuracy,boolean tracking) {
        currentLat=lat;currentLon=lon;currentFixTime=fixTime;
        currentAccuracy=accuracy;gpsTracking=tracking;
        if(route==null){stage=null;return;}
        // A fresh foreground fix can identify the nearby route segment without
        // enabling continuous background GPS tracking.
        stage=StagePositionMatcher.locate(route,lat,lon,fixTime,accuracy,
                System.currentTimeMillis());
        int newLeg=StagePositionMatcher.boardingIndex(route,stage);
        if(newLeg!=boardingLegIndex){
            boardingLegIndex=newLeg;arrival=null;arrivalRequestedAt=0;
            loadingArrival=false;loadingWalk=false;
            walkSeconds=-1;walkingFetchedAt=0;walkingRequestedAt=0;
            loadArrival(false);
        }
        loadWalking(false);
        notifyChange();
    }

    private void loadRoute(boolean manual){
        if(!running||loadingRoute||!TmapTransitClient.enabled()) {
            if(!TmapTransitClient.enabled())routeStatus="TMAP 대중교통 API 키가 설정되지 않았어";
            return;
        }
        long now=System.currentTimeMillis();
        if(route!=null&&now-routeFetchedAt<ROUTE_TTL_MS)return;
        if(route!=null){
            route=null;arrival=null;boardingLegIndex=-1;routeFetchedAt=0;
            loadingArrival=false;loadingWalk=false;
            walkSeconds=-1;walkingFetchedAt=0;stage=null;
            routeStatus="기존 경로 정보가 만료되어 다시 조회해야 해";
        }
        long previous=context.getSharedPreferences("live_route_lookup",Context.MODE_PRIVATE)
                .getLong(userId+"|"+routine.id,0);
        if(now-routeRequestedAt<ROUTE_RETRY_MS ||
                (previous>0&&now-previous<ROUTE_RETRY_MS)) {
            routeStatus="TMAP 경로 재조회 간격 제한(2시간) · 이동 시간대에는 저장된 경로 미리보기 활용";
            return;
        }
        if(!takeRouteBudget(now)){
            routeStatus="오늘의 앱 내 TMAP 경로 조회 상한에 도달했어 (3회)";
            notifyChange();return;
        }
        routeRequestedAt=now;loadingRoute=true;
        context.getSharedPreferences("live_route_lookup",Context.MODE_PRIVATE).edit()
                .putLong(userId+"|"+routine.id,now).apply();
        final int token=generation;
        routeStatus="TMAP 경로 검색 중...";
        notifyChange();
        TmapTransitClient.search(routine,new TmapTransitClient.Callback(){
            @Override public void success(List<TransitRoute> results){
                if(!valid(token))return;
                loadingRoute=false;
                if(results.isEmpty()){routeStatus="경로 후보가 없어";notifyChange();return;}
                int selected=choosePreference(results);
                route=results.get(selected);
                routeFetchedAt=System.currentTimeMillis();
                TransitRouteMemory.put(userId,routine,results);
                TransitRouteMemory.choose(userId,routine,selected);
                boolean matched=routine.selectedRouteSignature.isEmpty()||
                        RouteSignature.of(route).equals(routine.selectedRouteSignature);
                routeStatus=(matched?"실시간 경로 조회 완료":
                        "선택한 노선 패턴과 일치하는 후보가 없어 빠른 경로를 임시 표시 중")
                        +" · "+(selected+1)+"번 후보 · "+TransitRoute.minutes(route.totalSeconds)+"분 (계획 기준)";
                arrival=null;arrivalRequestedAt=0;
                loadingArrival=false;loadingWalk=false;
                stage=gpsTracking?StagePositionMatcher.locate(route,currentLat,currentLon,
                        currentFixTime,currentAccuracy,System.currentTimeMillis()):null;
                boardingLegIndex=StagePositionMatcher.boardingIndex(route,stage);
                walkSeconds=-1;walkingFetchedAt=0;walkingRequestedAt=0;
                loadArrival(true);
                loadWalking(false);
                notifyChange();
            }
            @Override public void error(String reason){
                if(!valid(token))return;
                loadingRoute=false;
                routeStatus="경로 조회 실패: "+reason;
                notifyChange();
            }
        });
    }

    private int choosePreference(List<TransitRoute> options){
        String preferred=routine.selectedRouteSignature;
        if(preferred!=null&&!preferred.isEmpty()){
            for(int i=0;i<options.size();i++)
                if(preferred.equals(RouteSignature.of(options.get(i))))return i;
        }
        int fastest=0;
        for(int i=1;i<options.size();i++)
            if(options.get(i).totalSeconds<options.get(fastest).totalSeconds)fastest=i;
        return fastest;
    }

    private void loadArrival(boolean manual){
        if(!running||loadingArrival||route==null)return;
        if(boardingLegIndex<0)boardingLegIndex=StagePositionMatcher.boardingIndex(route,stage);
        if(boardingLegIndex<0)return;
        long now=System.currentTimeMillis();
        if(now-arrivalRequestedAt<ARRIVAL_RETRY_MS)return;
        arrivalRequestedAt=now;loadingArrival=true;
        final int token=generation;
        final TransitRoute forRoute=route;
        final int legIndex=boardingLegIndex;
        arrivalStatus="다음 차량 도착정보 갱신 중...";
        notifyChange();
        LiveArrivalClient.fetchLeg(forRoute.legs.get(legIndex),result->{
            if(!valid(token)||forRoute!=route||legIndex!=boardingLegIndex)return;
            loadingArrival=false;arrival=result;
            arrivalStatus=result.available?"현재 선택 구간 도착정보 갱신 완료":
                    "차량 도착정보 미확인 · "+result.message;
            notifyChange();
        });
    }

    private void loadWeather(boolean manual){
        if(!running||loadingWeather)return;
        long now=System.currentTimeMillis();
        String key=userId+"|"+routine.id+"|"+routine.startLat+"|"
                +routine.startLon+"|"+routine.arrivalTime+"|"+routine.days;
        if(cachedWeather!=null&&cachedWeather.key.equals(key)
                &&now-cachedWeather.fetchedAt<WEATHER_RETRY_MS){
            weather=cachedWeather.report;
            weatherStatus=weather.label+" · 최근 조회 캐시";
            notifyChange();
            return;
        }
        if(now-weatherRequestedAt<WEATHER_RETRY_MS)return;
        weather=null;
        weatherRequestedAt=now;loadingWeather=true;
        weatherStatus="기상청 단기예보 조회 중...";
        final int token=generation;
        notifyChange();
        KmaWeatherClient.forecast(routine,new KmaWeatherClient.Callback(){
            @Override public void success(KmaWeatherClient.Report report){
                if(!valid(token))return;
                loadingWeather=false;weather=report;
                cachedWeather=new WeatherCache(key,report,System.currentTimeMillis());
                weatherStatus=report.label;
                notifyChange();
            }
            @Override public void error(String why){
                if(!valid(token))return;
                loadingWeather=false;weather=null;
                weatherStatus="기상정보 미확인 · "+why;
                notifyChange();
            }
        });
    }

    private void loadWalking(boolean manual){
        if(!running||!gpsTracking||loadingWalk||route==null||boardingLegIndex<0)return;
        long now=System.currentTimeMillis();
        if(!CommuteWindow.active(routine,now)&&!manual)return;
        if(now-walkingRequestedAt<WALK_RETRY_MS)return;
        if(currentFixTime<=0||now-currentFixTime>90000||now-currentFixTime< -30000||
                !Float.isFinite(currentAccuracy)||currentAccuracy<0||currentAccuracy>75)return;
        TransitRoute.Leg leg=route.legs.get(boardingLegIndex);
        if(!Double.isFinite(leg.startLat)||!Double.isFinite(leg.startLon))return;
        if(!TmapWalkingClient.enabled()){
            walkingStatus="TMAP 보행 API 미설정 · 경로 계획 도보시간 사용";
            return;
        }
        walkingRequestedAt=now;loadingWalk=true;
        final int token=generation,legIndex=boardingLegIndex;
        final TransitRoute forRoute=route;
        walkingStatus="현재 위치에서 승차지까지 실제 보행시간 조회 중...";
        TmapWalkingClient.estimateSeconds(currentLat,currentLon,
                leg.startLat,leg.startLon,new TmapWalkingClient.DurationCallback(){
            @Override public void success(int seconds){
                if(!valid(token)||forRoute!=route||legIndex!=boardingLegIndex)return;
                loadingWalk=false;walkSeconds=seconds;
                walkingFetchedAt=System.currentTimeMillis();
                walkingStatus="TMAP 보행 경로 기준 · 약 "+((seconds+59)/60)+"분";
                notifyChange();
            }
            @Override public void error(String why){
                if(!valid(token)||forRoute!=route||legIndex!=boardingLegIndex)return;
                loadingWalk=false;walkSeconds=-1;walkingFetchedAt=0;
                walkingStatus="보행 경로 조회 실패 · 계획 도보시간 사용: "+why;
                notifyChange();
            }
        });
    }

    private boolean takeRouteBudget(long now){
        SharedPreferences prefs=RoutineStore.prefs(context);
        SimpleDateFormat date=new SimpleDateFormat("yyyyMMdd",Locale.US);
        date.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        String day=date.format(new Date(now));
        int count=day.equals(prefs.getString("tmap_route_budget_day",""))
                ?prefs.getInt("tmap_route_budget_count",0):0;
        if(count>=MAX_LOCAL_TMAP_CALLS_PER_DAY)return false;
        prefs.edit().putString("tmap_route_budget_day",day)
                .putInt("tmap_route_budget_count",count+1).apply();
        return true;
    }

    private boolean valid(int token){return running&&generation==token;}
    private void notifyChange(){if(running)listener.changed();}
}
