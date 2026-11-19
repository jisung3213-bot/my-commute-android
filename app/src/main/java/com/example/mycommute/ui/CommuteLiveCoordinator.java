package com.example.mycommute.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.TransitRouteMemory;
import com.example.mycommute.data.TransitRouteCache;
import com.example.mycommute.engine.CommuteWindow;
import com.example.mycommute.engine.StagePositionMatcher;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.network.KmaWeatherClient;
import com.example.mycommute.network.LiveArrivalClient;
import com.example.mycommute.network.TmapWalkingClient;

/**
 * Foreground-only polling coordinator. No server calls when the screen is stopped.
 * Reads the saved TMAP route and refreshes only live data for the screen.
 */
final class CommuteLiveCoordinator {
    interface Listener { void changed(); }

    private static final long TICK_MS=60_000L;
    private static final long ARRIVAL_RETRY_MS=60_000L;
    private static final long WEATHER_RETRY_MS=60*60_000L;
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
    private boolean loadingArrival,loadingWeather,loadingWalk;
    private int boardingLegIndex=-1;
    private int walkSeconds=-1;
    private long walkingRequestedAt,walkingFetchedAt;
    private StagePositionMatcher.Match stage;
    private double currentLat=Double.NaN,currentLon=Double.NaN;
    private float currentAccuracy=Float.NaN;
    private long currentFixTime;
    private boolean gpsTracking;
    private long routeFetchedAt,arrivalRequestedAt,weatherRequestedAt;
    private TransitRoute route;
    private TransitArrival arrival;
    private KmaWeatherClient.Report weather;
    private String weatherStatus="기상정보 대기 중";

    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(!running)return;
            long now=System.currentTimeMillis();
            // The dashboard itself is a valid reason to display weather and
            // next-vehicle information, even before the commute window starts.
            loadRoute();
            loadArrival();
            loadWeather();
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
            weatherStatus="루틴 미설정";
            notifyChange();return;
        }
        TransitRoute cached=TransitRouteMemory.selected(userId,routine);
        TransitRouteCache.Snapshot disk=TransitRouteCache.load(context,routine);
        if(cached==null&&disk!=null)cached=disk.routes.get(disk.selected);
        if(cached!=null){
            route=cached;
            routeFetchedAt=disk!=null?disk.timestamp:System.currentTimeMillis();
            stage=null;
            boardingLegIndex=StagePositionMatcher.boardingIndex(route,null);
        }
        loadWeather();
        loadRoute();
        loadArrival();
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
        loadRoute();
        loadArrival();
        loadWeather();
        loadWalking(true);
        notifyChange();
    }

    TransitRoute route(){return route;}
    TransitArrival arrival(){return arrival;}
    KmaWeatherClient.Report weather(){return weather;}
    String weatherStatus(){return weatherStatus;}
    boolean activeWindow(){return CommuteWindow.active(routine,System.currentTimeMillis());}
    long targetArrivalMillis(){return CommuteWindow.nearestTarget(routine,System.currentTimeMillis());}
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
            loadArrival();
        }
        loadWalking(false);
        notifyChange();
    }

    /**
     * Viewing a saved profile never spends a TMAP transit route request.
     * Only the explicit button on the Custom screen performs a new search.
     */
    private void loadRoute(){
        if(!running)return;
        TransitRouteCache.Snapshot disk=TransitRouteCache.load(context,routine);
        if(disk==null){
            route=null;arrival=null;stage=null;boardingLegIndex=-1;
            return;
        }
        if(route!=null&&routeFetchedAt==disk.timestamp)return;
        route=disk.routes.get(disk.selected);
        routeFetchedAt=disk.timestamp;
        arrival=null;arrivalRequestedAt=0;
        stage=StagePositionMatcher.locate(route,currentLat,currentLon,
                currentFixTime,currentAccuracy,System.currentTimeMillis());
        boardingLegIndex=StagePositionMatcher.boardingIndex(route,stage);
        walkSeconds=-1;walkingFetchedAt=0;
        loadArrival();
        notifyChange();
    }

    private void loadArrival(){
        if(!running||loadingArrival||route==null)return;
        if(boardingLegIndex<0)boardingLegIndex=StagePositionMatcher.boardingIndex(route,stage);
        if(boardingLegIndex<0)return;
        long now=System.currentTimeMillis();
        if(now-arrivalRequestedAt<ARRIVAL_RETRY_MS)return;
        arrivalRequestedAt=now;loadingArrival=true;
        final int token=generation;
        final TransitRoute forRoute=route;
        final int legIndex=boardingLegIndex;
        notifyChange();
        LiveArrivalClient.fetchLeg(forRoute.legs.get(legIndex),result->{
            if(!valid(token)||forRoute!=route||legIndex!=boardingLegIndex)return;
            loadingArrival=false;arrival=result;
            notifyChange();
        });
    }

    private void loadWeather(){
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
                String weatherNote=report.conditions.rain
                        ?(report.precipitationType==2||report.precipitationType==3
                            ?"눈·비 예보":"비 예보")
                        :report.conditions.lowTemperature?"영하 기온":"";
                RoutineStore.prefs(context).edit()
                        .putString("alert_weather_text_"+routine.id,weatherNote)
                        .putLong("alert_weather_at_"+routine.id,System.currentTimeMillis()).apply();
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
        if(!TmapWalkingClient.enabled())return;
        walkingRequestedAt=now;loadingWalk=true;
        final int token=generation,legIndex=boardingLegIndex;
        final TransitRoute forRoute=route;
        TmapWalkingClient.estimateSeconds(currentLat,currentLon,
                leg.startLat,leg.startLon,new TmapWalkingClient.DurationCallback(){
            @Override public void success(int seconds){
                if(!valid(token)||forRoute!=route||legIndex!=boardingLegIndex)return;
                loadingWalk=false;walkSeconds=seconds;
                walkingFetchedAt=System.currentTimeMillis();
                notifyChange();
            }
            @Override public void error(String why){
                if(!valid(token)||forRoute!=route||legIndex!=boardingLegIndex)return;
                loadingWalk=false;walkSeconds=-1;walkingFetchedAt=0;
                notifyChange();
            }
        });
    }

    private boolean valid(int token){return running&&generation==token;}
    private void notifyChange(){if(running)listener.changed();}
}
