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
        return valid(BuildConfig.SEOUL_SUBWAY_API_KEY,"YOUR_SEOUL_SUBWAY_API_KEY_HERE");
    }
    public static boolean busEnabled(){
        return valid(BuildConfig.TAGO_BUS_SERVICE_KEY,"YOUR_TAGO_BUS_SERVICE_KEY_HERE");
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
                    "SEOUL_SUBWAY_API_KEY가 설정되지 않았어",System.currentTimeMillis())));return;
        }
        if("BUS".equals(leg.mode)&&!busEnabled()){
            UI.post(()->callback.complete(TransitArrival.unavailable(
                    "TAGO_BUS_SERVICE_KEY가 설정되지 않았어",System.currentTimeMillis())));return;
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
        String url="http://swopenAPI.seoul.go.kr/api/subway/"
                +URLEncoder.encode(BuildConfig.SEOUL_SUBWAY_API_KEY,StandardCharsets.UTF_8.name())
                +"/json/realtimeStationArrival/0/30/"
                +URLEncoder.encode(station,StandardCharsets.UTF_8.name());
        String text=request(url,"서울시 지하철 도착정보");
        return ArrivalMatcher.subwayArrival(text,leg,System.currentTimeMillis());
    }

    private static TransitArrival bus(TransitRoute.Leg leg) throws Exception {
        if(!Double.isFinite(leg.startLat)||!Double.isFinite(leg.startLon))
            return TransitArrival.unavailable("버스 승차 정류장 좌표가 없어",
                    System.currentTimeMillis());
        String base="https://apis.data.go.kr/1613000/";
        String location="BusSttnInfoInqireService/getCrdntPrxmtSttnList"
                +"?serviceKey="+encode(BuildConfig.TAGO_BUS_SERVICE_KEY)
                +"&_type=json&pageNo=1&numOfRows=100"
                +"&gpsLati="+leg.startLat+"&gpsLong="+leg.startLon;
        ArrivalMatcher.Stop stop=ArrivalMatcher.chooseNearbyStop(request(base+location,"TAGO 정류소정보"),leg);
        if(stop==null)return TransitArrival.unavailable(
                "가까운 TAGO 정류장 중 이름과 위치를 유일하게 확인할 수 없어",
                System.currentTimeMillis());
        String arrivals="ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList"
                +"?serviceKey="+encode(BuildConfig.TAGO_BUS_SERVICE_KEY)
                +"&_type=json&pageNo=1&numOfRows=100"
                +"&cityCode="+encode(stop.cityCode)+"&nodeId="+encode(stop.nodeId);
        return ArrivalMatcher.busArrival(request(base+arrivals,"TAGO 버스도착정보"),leg,stop,System.currentTimeMillis());
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

    private static String request(String address,String provider) throws Exception {
        long now=System.currentTimeMillis();
        Long blocked=blockedUntil.get(provider);
        if(blocked!=null&&blocked>now)
            throw new IllegalStateException(provider+" · 인증/호출 제한 오류 후 재조회 대기 (최대 10분)");
        HttpURLConnection conn=(HttpURLConnection)new URL(address).openConnection();
        try{
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(14000);
            conn.setRequestProperty("Accept","application/json");
            int status=conn.getResponseCode();
            if(status!=200){
                String response=readLimited(conn.getErrorStream(),4096);
                if(status==401||status==403||status==429)
                    blockedUntil.put(provider,now+AUTH_BACKOFF_MS);
                throw new IllegalStateException(ArrivalApiError.describe(provider,status,response));
            }
            return readLimited(conn.getInputStream(),1_500_000);
        }finally{conn.disconnect();}
    }

    private static String readLimited(InputStream stream,int limit) throws Exception {
        if(stream==null)return "";
        try(InputStream input=stream;
            BufferedReader reader=new BufferedReader(
                    new InputStreamReader(input,StandardCharsets.UTF_8))){
            StringBuilder sb=new StringBuilder();
            char[] chars=new char[4096];int count;
            while((count=reader.read(chars))!=-1){
                if(sb.length()+count>limit)
                    throw new IllegalStateException("API 응답 크기 초과");
                sb.append(chars,0,count);
            }
            return sb.toString();
        }
    }
}
