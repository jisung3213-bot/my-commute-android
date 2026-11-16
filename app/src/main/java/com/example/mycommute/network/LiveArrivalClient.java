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
import java.nio.charset.StandardCharsets;
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
        if(route==null)return null;
        for(TransitRoute.Leg leg:route.legs)
            if("BUS".equals(leg.mode)||"SUBWAY".equals(leg.mode))return leg;
        return null;
    }

    public static void fetch(TransitRoute route,Callback callback) {
        TransitRoute.Leg leg=firstBoarding(route);
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
        String text=request(url);
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
        ArrivalMatcher.Stop stop=ArrivalMatcher.chooseNearbyStop(request(base+location),leg);
        if(stop==null)return TransitArrival.unavailable(
                "가까운 TAGO 정류장 중 이름과 위치를 유일하게 확인할 수 없어",
                System.currentTimeMillis());
        String arrivals="ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList"
                +"?serviceKey="+encode(BuildConfig.TAGO_BUS_SERVICE_KEY)
                +"&_type=json&pageNo=1&numOfRows=100"
                +"&cityCode="+encode(stop.cityCode)+"&nodeId="+encode(stop.nodeId);
        return ArrivalMatcher.busArrival(request(base+arrivals),leg,stop,System.currentTimeMillis());
    }

    private static String encode(String text) throws Exception {
        return URLEncoder.encode(text,StandardCharsets.UTF_8.name());
    }

    private static String request(String address) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(address).openConnection();
        try{
            conn.setConnectTimeout(10000);conn.setReadTimeout(14000);
            conn.setRequestProperty("Accept","application/json");
            int status=conn.getResponseCode();
            if(status!=200)throw new IllegalStateException("HTTP "+status
                    +" (API 인증키·권한 및 호출 한도 확인)");
            StringBuilder sb=new StringBuilder();
            try(InputStream stream=conn.getInputStream();
                BufferedReader input=new BufferedReader(
                        new InputStreamReader(stream,StandardCharsets.UTF_8))){
                char[] buffer=new char[4096];int count;
                while((count=input.read(buffer))!=-1){
                    if(sb.length()+count>1_500_000)
                        throw new IllegalStateException("도착정보 응답 크기 초과");
                    sb.append(buffer,0,count);
                }
            }
            return sb.toString();
        }finally {conn.disconnect();}
    }
}
