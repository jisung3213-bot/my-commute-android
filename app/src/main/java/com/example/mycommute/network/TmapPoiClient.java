package com.example.mycommute.network;

import android.os.Handler;
import android.os.Looper;
import com.example.mycommute.BuildConfig;
import com.example.mycommute.model.Place;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TmapPoiClient {
    public interface Callback {void success(List<Place> places);void error(String message);}
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private TmapPoiClient(){}
    public static boolean enabled(){return GatewayClient.enabled();}
    public static void search(String text,Callback cb) {
        String query=text==null?"":text.trim();
        if(query.length()<2){MAIN.post(()->cb.error("검색어를 확인하세요"));return;}
        EXECUTOR.execute(()->{
            try {
                String json=GatewayClient.request("GET","/v1/pois?q="
                        +URLEncoder.encode(query,StandardCharsets.UTF_8.name()),null);
                List<Place> results=Place.parse(json);
                MAIN.post(()->cb.success(results));
            }catch(Exception e){
                String msg=e.getMessage()==null?"장소 검색 실패":e.getMessage();
                MAIN.post(()->cb.error(msg));
            }
        });
    }

}
