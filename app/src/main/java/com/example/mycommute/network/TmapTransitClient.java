package com.example.mycommute.network;

import android.os.Handler;
import android.os.Looper;
import com.example.mycommute.BuildConfig;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitRoute;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit on-demand transit route lookup; no automatic polling of the free tier. */
public final class TmapTransitClient {
    private static final String ENDPOINT="https://apis.openapi.sk.com/transit/routes";
    private static final ExecutorService NETWORK=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    public interface Callback {
        void success(List<TransitRoute> routes);
        void error(String message);
    }

    private TmapTransitClient() {}

    public static boolean enabled() {
        String key=BuildConfig.TMAP_TRANSIT_APP_KEY.trim();
        return !key.isEmpty()&&!key.equals("YOUR_TMAP_TRANSIT_APP_KEY_HERE");
    }

    public static void search(final Routine r,final Callback cb) {
        if(!enabled()) {MAIN.post(() -> cb.error("대중교통 AppKey 미설정"));return;}
        if(r==null||r.startName.trim().isEmpty()||r.endName.trim().isEmpty()||!r.validCoordinates()) {
            MAIN.post(() -> cb.error("저장된 루틴의 출발·도착 좌표를 확인해"));return;
        }
        final double startLat=r.startLat,startLon=r.startLon,endLat=r.endLat,endLon=r.endLon;
        NETWORK.execute(() -> {
            try {
                JSONObject request=new JSONObject();
                request.put("startX",String.valueOf(startLon));
                request.put("startY",String.valueOf(startLat));
                request.put("endX",String.valueOf(endLon));
                request.put("endY",String.valueOf(endLat));
                request.put("count",3);
                request.put("lang",0);
                request.put("format","json");
                List<TransitRoute> results=post(request);
                MAIN.post(() -> cb.success(results));
            } catch(Exception e) {
                String message=e.getMessage()==null?"경로 조회 실패":e.getMessage();
                MAIN.post(() -> cb.error(message));
            }
        });
    }

    private static List<TransitRoute> post(JSONObject body) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            connection.setRequestProperty("appKey",BuildConfig.TMAP_TRANSIT_APP_KEY);
            byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream stream=connection.getOutputStream()){stream.write(payload);}
            int http=connection.getResponseCode();
            InputStream stream=http>=200&&http<300?connection.getInputStream():connection.getErrorStream();
            String response=readLimited(stream);
            if(http<200||http>=300)throw new IllegalStateException(
                    "TMAP 대중교통 HTTP "+http+" (API 키·상품 권한·호출 한도를 확인해)");
            List<TransitRoute> routes=TransitRoute.parse(response);
            if(routes.isEmpty())throw new IllegalStateException("대중교통 경로 결과가 없어. 다른 출발·도착지를 확인해");
            return routes;
        } finally { connection.disconnect(); }
    }

    private static String readLimited(InputStream stream) throws Exception {
        if(stream==null)return "";
        StringBuilder result=new StringBuilder();
        try(BufferedReader in=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            char[] buffer=new char[4096];
            int count;
            while((count=in.read(buffer))!=-1) {
                if(result.length()+count>2_000_000)throw new IllegalStateException("API 응답 크기 초과");
                result.append(buffer,0,count);
            }
        }
        return result.toString();
    }
}
