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
    public static boolean enabled() {return !BuildConfig.TMAP_APP_KEY.isEmpty()
            &&!"YOUR_TMAP_APP_KEY_HERE".equals(BuildConfig.TMAP_APP_KEY);}
    public static void search(String text,Callback cb) {
        if(!enabled()) {MAIN.post(()->cb.error("TMAP_APP_KEY를 입력해야 해"));return;}
        String query=text==null?"":text.trim();
        if(query.length()<2) {MAIN.post(()->cb.error("장소명을 2글자 이상 입력해"));return;}
        EXECUTOR.execute(()-> {
            try {
                String url="https://apis.openapi.sk.com/tmap/pois?version=1"
                        +"&searchKeyword="+URLEncoder.encode(query,StandardCharsets.UTF_8.name())
                        +"&searchType=all&searchtypCd=A&count=10&page=1"
                        +"&resCoordType=WGS84GEO&reqCoordType=WGS84GEO";
                HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
                try {
                    conn.setConnectTimeout(8000);conn.setReadTimeout(12000);
                    conn.setRequestProperty("appKey",BuildConfig.TMAP_APP_KEY);
                    conn.setRequestProperty("Accept","application/json");
                    int status=conn.getResponseCode();
                    if(status!=200)throw new IllegalStateException("TMAP 장소 검색 HTTP "+status);
                    String json=read(conn.getInputStream());
                    List<Place> results=Place.parse(json);
                    MAIN.post(()->cb.success(results));
                } finally {conn.disconnect();}
            } catch(Exception e) {
                String message=e.getMessage()==null?"장소 검색 실패":e.getMessage();
                MAIN.post(()->cb.error(message));
            }
        });
    }
    private static String read(InputStream in) throws Exception {
        StringBuilder b=new StringBuilder();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
            char[] buffer=new char[4096];int n;
            while((n=reader.read(buffer))!=-1) {
                if(b.length()+n>500_000)throw new IllegalStateException("장소 검색 응답 초과");
                b.append(buffer,0,n);
            }
        }
        return b.toString();
    }
}
