package com.example.mycommute.network;

import android.os.Handler;
import android.os.Looper;
import com.example.mycommute.model.Waypoint;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Walking routes and live walking duration, via the authenticated gateway. */
public final class TmapWalkingClient {
    public interface Callback {void success(List<GeoPoint> points);void error(String reason);}
    public interface DurationCallback {void success(int seconds);void error(String reason);}
    private static final ExecutorService NETWORK=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private TmapWalkingClient(){}

    public static boolean enabled(){return GatewayClient.enabled();}

    public static void fetchWalk(final List<Waypoint> stops,Callback cb){
        if(!enabled()){MAIN.post(()->cb.error("서버 연결 필요"));return;}
        NETWORK.execute(()->{
            try {
                List<GeoPoint> path=new ArrayList<>();
                for(int i=0;i<stops.size()-1;i++){
                    List<GeoPoint> segment=getSegment(
                            stops.get(i).lat,stops.get(i).lon,stops.get(i+1).lat,
                            stops.get(i+1).lon,stops.get(i).name,stops.get(i+1).name);
                    if(i>0&&!segment.isEmpty())segment.remove(0);
                    path.addAll(segment);
                }
                MAIN.post(()->cb.success(path));
            }catch(Exception e){
                String msg=e.getMessage()==null?"도보 경로 조회 오류":e.getMessage();
                MAIN.post(()->cb.error(msg));
            }
        });
    }

    public static void estimateSeconds(double fromLat,double fromLon,
                                       double toLat,double toLon,DurationCallback cb){
        if(!enabled()){MAIN.post(()->cb.error("서버 연결 필요"));return;}
        if(!valid(fromLat,fromLon)||!valid(toLat,toLon)){
            MAIN.post(()->cb.error("위치 좌표 오류"));return;
        }
        NETWORK.execute(()->{
            try {
                JSONObject response=request(fromLat,fromLon,toLat,toLon,"현재 위치","승차 위치");
                JSONArray features=response.optJSONArray("features");
                if(features==null)throw new IllegalStateException("도보 경로 미확인");
                for(int i=0;i<features.length();i++){
                    JSONObject item=features.optJSONObject(i);
                    JSONObject props=item==null?null:item.optJSONObject("properties");
                    if(props==null)continue;
                    int duration=props.optInt("totalTime",-1);
                    if(duration>=0&&duration<=7200){
                        MAIN.post(()->cb.success(duration));
                        return;
                    }
                }
                throw new IllegalStateException("도보시간 미제공");
            }catch(Exception e){
                String msg=e.getMessage()==null?"도보시간 조회 오류":e.getMessage();
                MAIN.post(()->cb.error(msg));
            }
        });
    }

    private static JSONObject request(double startLat,double startLon,double endLat,
                                      double endLon,String startName,String endName)throws Exception{
        if(!valid(startLat,startLon)||!valid(endLat,endLon))
            throw new IllegalArgumentException("위치 좌표 오류");
        JSONObject body=new JSONObject();
        body.put("startX",startLon);
        body.put("startY",startLat);
        body.put("endX",endLon);
        body.put("endY",endLat);
        body.put("startName",startName);
        body.put("endName",endName);
        body.put("reqCoordType","WGS84GEO");
        body.put("resCoordType","WGS84GEO");
        return new JSONObject(GatewayClient.request("POST","/v1/walking",body.toString()));
    }

    private static List<GeoPoint> getSegment(double a,double b,double c,double d,
                                            String startName,String endName)throws Exception{
        JSONArray features=request(a,b,c,d,startName,endName).optJSONArray("features");
        if(features==null)throw new IllegalStateException("도보 경로 미확인");
        List<GeoPoint> path=new ArrayList<>();
        for(int i=0;i<features.length();i++){
            JSONObject item=features.optJSONObject(i);
            JSONObject geometry=item==null?null:item.optJSONObject("geometry");
            if(geometry==null)continue;
            JSONArray coords=geometry.optJSONArray("coordinates");
            if(coords==null||!"LineString".equals(geometry.optString("type")))continue;
            for(int j=0;j<coords.length();j++){
                JSONArray point=coords.optJSONArray(j);
                if(point!=null&&point.length()>=2)
                    path.add(new GeoPoint(point.optDouble(1),point.optDouble(0)));
            }
        }
        if(path.size()<2)throw new IllegalStateException("도보 경로 미확인");
        return path;
    }

    private static boolean valid(double lat,double lon){
        return Double.isFinite(lat)&&Double.isFinite(lon)&&
                lat>=33&&lat<=39&&lon>=124&&lon<=132;
    }
}
