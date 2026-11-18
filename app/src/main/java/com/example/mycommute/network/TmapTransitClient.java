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
    private static final ExecutorService NETWORK=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    public interface Callback {
        void success(List<TransitRoute> routes);
        void error(String message);
    }

    private TmapTransitClient() {}

    public static boolean enabled() {
        return GatewayClient.enabled();
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
                List<TransitRoute> results=TransitRoute.parse(GatewayClient.request(
                        "POST","/v1/transit/routes",request.toString()));
                if(results.isEmpty())throw new IllegalStateException("경로 결과 없음");
                MAIN.post(() -> cb.success(results));
            } catch(Exception e) {
                String message=e.getMessage()==null?"경로 조회 실패":e.getMessage();
                MAIN.post(() -> cb.error(message));
            }
        });
    }

}
