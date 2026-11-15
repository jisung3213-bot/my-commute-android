package com.example.mycommute.network;

import android.os.Handler;
import android.os.Looper;
import com.example.mycommute.BuildConfig;
import com.example.mycommute.model.Waypoint;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/** TMAP pedestrian segments only. Not a multimodal public transit routing engine. */
public final class TmapWalkingClient {
    public interface Callback {void success(List<GeoPoint> points);void error(String reason);}
    private static final java.util.concurrent.ExecutorService NETWORK=Executors.newSingleThreadExecutor();
    private TmapWalkingClient() { }
    public static boolean enabled() {
        return !BuildConfig.TMAP_APP_KEY.isEmpty() && !BuildConfig.TMAP_APP_KEY.equals("YOUR_TMAP_APP_KEY_HERE");
    }
    public static void fetchWalk(final List<Waypoint> stops,Callback cb) {
        if(!enabled()) {cb.error("TMAP AppKey 미설정");return;}
        NETWORK.execute(() -> {
            try {
                List<GeoPoint> path=new ArrayList<>();
                for(int i=0;i<stops.size()-1;i++) {
                    List<GeoPoint> segment=getSegment(stops.get(i),stops.get(i+1));
                    if(i>0&&!segment.isEmpty()) segment.remove(0);
                    path.addAll(segment);
                }
                new Handler(Looper.getMainLooper()).post(() -> cb.success(path));
            } catch(Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> cb.error(e.getMessage()==null?"경로 조회 실패":e.getMessage()));
            }
        });
    }
    private static List<GeoPoint> getSegment(Waypoint start,Waypoint end) throws Exception {
        URL url=new URL("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        try {
            conn.setConnectTimeout(9000);conn.setReadTimeout(14000);
            conn.setRequestMethod("POST");conn.setRequestProperty("appKey",BuildConfig.TMAP_APP_KEY);
            conn.setRequestProperty("Accept","application/json");conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            conn.setDoOutput(true);
            JSONObject body=new JSONObject();
            body.put("startX",start.lon);body.put("startY",start.lat);body.put("endX",end.lon);body.put("endY",end.lat);
            body.put("startName",start.name);body.put("endName",end.name);body.put("reqCoordType","WGS84GEO");
            body.put("resCoordType","WGS84GEO");
            try(OutputStream o=conn.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=conn.getResponseCode();
            if(code<200||code>=300) throw new IllegalStateException("TMAP HTTP "+code);
            StringBuilder text=new StringBuilder();
            try(BufferedReader b=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))) {
                String line;while((line=b.readLine())!=null)text.append(line);
            }
            JSONObject result=new JSONObject(text.toString());
            JSONArray features=result.optJSONArray("features");
            if(features==null)throw new IllegalStateException("TMAP 경로 좌표 누락");
            List<GeoPoint> segment=new ArrayList<>();
            for(int i=0;i<features.length();i++) {
                JSONObject geometry=features.getJSONObject(i).optJSONObject("geometry");if(geometry==null)continue;
                String type=geometry.optString("type");JSONArray coords=geometry.optJSONArray("coordinates");if(coords==null)continue;
                if("LineString".equals(type)) for(int j=0;j<coords.length();j++) {
                    JSONArray xy=coords.optJSONArray(j);if(xy!=null&&xy.length()>=2) segment.add(new GeoPoint(xy.optDouble(1),xy.optDouble(0)));
                }
            }
            if(segment.size()<2)throw new IllegalStateException("도보 경로가 반환되지 않았어");
            return segment;
        } finally {conn.disconnect();}
    }
}
