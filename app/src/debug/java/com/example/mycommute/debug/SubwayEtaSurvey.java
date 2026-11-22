package com.example.mycommute.debug;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.example.mycommute.BuildConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Debug-only provider ETA availability survey; no GPS or route/user data. */
public final class SubwayEtaSurvey {
    public interface Callback { void done(String report); }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final String PREF="subway_eta_survey";
    private static final String LAST="last_run";
    private static final long COOLDOWN=15*60_000L;
    // Fixed geographically and line-diverse samples. One request per station.
    private static final String[] STATIONS={
            "광운대","서울","시청","종로3가","청량리","왕십리",
            "강남","고속터미널","사당","잠실","건대입구","신도림"
    };
    private SubwayEtaSurvey(){}
    public static void run(Context context,Callback callback) {
        Context app=context.getApplicationContext();
        IO.execute(()->{
            String report=inspect(app);
            MAIN.post(()->callback.done(report));
        });
    }
    private static String inspect(Context context) {
        StringBuilder report=new StringBuilder("지하철역 도착시간 가용성 조사 · DEBUG\n");
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.KOREA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        report.append("조회시각(KST): ").append(format.format(new Date())).append('\n');
        report.append("대상역: ").append(STATIONS.length)
                .append("개 · 역당 최대 100건 · 조회시점의 표본 결과\n");
        report.append("주의: ETA>0은 원본 barvlDt 기준이며 실제 탑승 방향 매칭 결과는 아님\n\n");
        if(BuildConfig.SEOUL_SUBWAY_API_KEY==null
                ||BuildConfig.SEOUL_SUBWAY_API_KEY.trim().isEmpty()){
            return report.append("중단: SEOUL_SUBWAY_API_KEY 설정 없음").toString();
        }
        long now=System.currentTimeMillis();
        android.content.SharedPreferences prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        long last=prefs.getLong(LAST,0);
        if(last>0&&now-last<COOLDOWN){
            long remain=(COOLDOWN-(now-last)+59999)/60000;
            return report.append("재실행 제한: 약 ").append(remain)
                    .append("분 후 다시 실행 가능 (호출량 보호)").toString();
        }
        prefs.edit().putLong(LAST,now).apply();
        int calls=0,ok=0,noData=0,failed=0;
        int positive=0,zero=0,missing=0,other=0;
        for(String station:STATIONS){
            HttpURLConnection conn=null;
            try{
                String url="http://swopenAPI.seoul.go.kr/api/subway/"
                        +URLEncoder.encode(BuildConfig.SEOUL_SUBWAY_API_KEY,"UTF-8")
                        +"/json/realtimeStationArrival/1/100/"
                        +URLEncoder.encode(station,"UTF-8");
                conn=(HttpURLConnection)new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept","application/json");
                calls++;
                int http=conn.getResponseCode();
                if(http!=200){
                    report.append(station).append(": HTTP ").append(http).append(" · 조사 중단\n");
                    failed++;
                    break;
                }
                StringBuilder raw=new StringBuilder();
                try(BufferedReader reader=new BufferedReader(
                        new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){
                    char[] buffer=new char[4096];int n;
                    while((n=reader.read(buffer))>=0){
                        if(raw.length()+n>2_000_000)throw new IllegalStateException("응답 크기 초과");
                        raw.append(buffer,0,n);
                    }
                }
                JSONObject root=new JSONObject(raw.toString());
                JSONObject provider=root.optJSONObject("errorMessage");
                JSONObject standard=root.optJSONObject("RESULT");
                String code=provider!=null?provider.optString("code","")
                        :standard!=null?standard.optString("CODE",""):"";
                if("INFO-200".equals(code)){
                    noData++;
                    report.append(station).append(": 조회 데이터 없음 (INFO-200)\n");
                    continue;
                }
                if(!code.isEmpty()&&!"INFO-000".equals(code)){
                    failed++;
                    report.append(station).append(": 제공기관 오류 ")
                            .append(code.replaceAll("[^A-Za-z0-9_-]","")).append(" · 조사 중단\n");
                    break;
                }
                JSONArray list=root.optJSONArray("realtimeArrivalList");
                if(list==null){
                    failed++;
                    report.append(station).append(": 예상한 응답 목록이 없음 · 조사 중단\n");
                    break;
                }
                int p=0,z=0,m=0,o=0;
                // Line IDs are provider-supplied and non-identifying.
                Map<String,int[]> byLine=new LinkedHashMap<>();
                for(int i=0;i<list.length();i++){
                    JSONObject row=list.optJSONObject(i);
                    if(row==null)continue;
                    String line=row.optString("subwayId","미상");
                    if(!line.matches("[0-9]{4}"))line="미상";
                    int[] counts=byLine.get(line);
                    if(counts==null){counts=new int[4];byLine.put(line,counts);}
                    Object value=row.opt("barvlDt");
                    if(value==null||JSONObject.NULL.equals(value)
                            ||String.valueOf(value).trim().isEmpty()){
                        m++;counts[2]++;
                    }else{
                        try{
                            int seconds=Integer.parseInt(String.valueOf(value).trim());
                            if(seconds>0){p++;counts[0]++;}
                            else if(seconds==0){z++;counts[1]++;}
                            else{o++;counts[3]++;}
                        }catch(NumberFormatException e){o++;counts[3]++;}
                    }
                }
                ok++;
                positive+=p;zero+=z;missing+=m;other+=o;
                report.append(station).append(": 총 ").append(list.length())
                        .append("건 · ETA>0 ").append(p)
                        .append(" / 0초 ").append(z)
                        .append(" / 누락 ").append(m)
                        .append(" / 비정상 ").append(o).append('\n');
                for(Map.Entry<String,int[]> e:byLine.entrySet()){
                    int[] c=e.getValue();
                    report.append("  노선 ").append(e.getKey())
                            .append(": >0 ").append(c[0]).append(", 0초 ")
                            .append(c[1]).append(", 누락 ").append(c[2])
                            .append(", 비정상 ").append(c[3]).append('\n');
                }
            }catch(Exception e){
                failed++;
                report.append(station).append(": 조회 실패 ")
                        .append(e.getClass().getSimpleName()).append(" · 조사 중단\n");
                break;
            }finally{
                if(conn!=null)conn.disconnect();
            }
            try{Thread.sleep(700);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
        }
        report.append("\n집계: 요청 ").append(calls).append("/")
                .append(STATIONS.length).append(" · 응답 ").append(ok)
                .append("역 · 데이터 없음 ").append(noData).append("역 · 오류 ").append(failed)
                .append("\n열차 행: ETA>0 ").append(positive)
                .append(" / 0초 ").append(zero).append(" / 누락 ")
                .append(missing).append(" / 비정상 ").append(other)
                .append("\n판독: ETA>0 열차 행이 있는 역은 원본 시간 데이터 제공이 확인됨.")
                .append("\n0초만 나온 역도 다른 시간·방향에서는 ETA가 제공될 수 있음.")
                .append("\n키·응답 본문·열차번호·위치정보는 보고서에 포함되지 않음.");
        return report.toString();
    }
}
