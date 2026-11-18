package com.example.mycommute.network;

import android.os.Handler;
import android.os.Looper;
import com.example.mycommute.BuildConfig;
import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * First boarding only. Strict matching prevents displaying a departure from
 * another subway direction or a similarly named bus stop.
 */
public final class LiveArrivalClient {
    public interface Callback {void complete(TransitArrival result);}
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    // All requests run on IO, so this map is accessed from one worker thread.
    private static final Map<String,Long> blockedUntil=new HashMap<>();
    private static final long AUTH_BACKOFF_MS=10*60_000L;
    private LiveArrivalClient(){}

    public static boolean subwayEnabled(){
        return GatewayClient.enabled();
    }
    public static boolean busEnabled(){
        return GatewayClient.enabled();
    }
    private static boolean valid(String value,String placeholder){
        return value!=null&&!value.trim().isEmpty()&&!placeholder.equals(value.trim());
    }

    public static TransitRoute.Leg firstBoarding(TransitRoute route) {
        int index=firstBoardingIndex(route);
        return index>=0?route.legs.get(index):null;
    }

    public static int firstBoardingIndex(TransitRoute route) {
        if(route==null)return -1;
        for(int i=0;i<route.legs.size();i++)
            if("BUS".equals(route.legs.get(i).mode)||
                    "SUBWAY".equals(route.legs.get(i).mode))return i;
        return -1;
    }

    public static void fetch(TransitRoute route,Callback callback) {
        fetchLeg(firstBoarding(route),callback);
    }

    public static void fetchLeg(TransitRoute.Leg leg,Callback callback) {
        if(leg==null){UI.post(()->callback.complete(TransitArrival.unavailable(
                "이 경로에는 버스·지하철 탑승 구간이 없어",System.currentTimeMillis())));return;}
        if("SUBWAY".equals(leg.mode)&&!subwayEnabled()){
            UI.post(()->callback.complete(TransitArrival.unavailable(
                    "서버 연결 필요",System.currentTimeMillis())));return;
        }
        if("BUS".equals(leg.mode)&&!busEnabled()){
            UI.post(()->callback.complete(TransitArrival.unavailable(
                    "서버 연결 필요",System.currentTimeMillis())));return;
        }
        IO.execute(()->{
            TransitArrival result;
            try {
                if("SUBWAY".equals(leg.mode))result=subway(leg);
                else result=bus(leg);
            }catch(Exception e){
                String why=e.getMessage()==null?"알 수 없는 오류":e.getMessage();
                result=TransitArrival.unavailable("실시간 도착정보 조회 실패: "+why,
                        System.currentTimeMillis());
            }
            TransitArrival finalResult=result;
            UI.post(()->callback.complete(finalResult));
        });
    }

    private static TransitArrival subway(TransitRoute.Leg leg) throws Exception {
        String station=leg.startName.replaceAll("역$","").trim();
        if(station.isEmpty())return TransitArrival.unavailable("승차역 정보가 없어",
                System.currentTimeMillis());
        String text=GatewayClient.request("GET","/v1/subway?station="
                +encode(station),null);
        return ArrivalMatcher.subwayArrival(text,leg,System.currentTimeMillis());
    }

    private static TransitArrival bus(TransitRoute.Leg leg) throws Exception {
        if(!Double.isFinite(leg.startLat)||!Double.isFinite(leg.startLon))
            return TransitArrival.unavailable("버스 승차 정류장 좌표가 없어",
                    System.currentTimeMillis());
        String location="/v1/bus/stops?lat="+leg.startLat+"&lon="+leg.startLon;
        ArrivalMatcher.Stop stop=ArrivalMatcher.chooseNearbyStop(
                GatewayClient.request("GET",location,null),leg);
        if(stop==null)return TransitArrival.unavailable(
                "승차 정류장 확인 불가",System.currentTimeMillis());
        String arrivals="/v1/bus/arrivals?cityCode="+encode(stop.cityCode)
                +"&nodeId="+encode(stop.nodeId);
        return ArrivalMatcher.busArrival(
                GatewayClient.request("GET",arrivals,null),leg,stop,System.currentTimeMillis());
    }

    private static String encode(String text) throws Exception {
        // Accept both decoded and percent-encoded portal keys, without double encoding.
        String key=text.trim();
        if(key.matches(".*%[0-9a-fA-F]{2}.*")) {
            try {key=URLDecoder.decode(key,StandardCharsets.UTF_8.name());}
            catch(IllegalArgumentException ignored) { }
        }
        return URLEncoder.encode(key,StandardCharsets.UTF_8.name());
    }

}
