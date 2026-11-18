package com.example.mycommute.debug;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.example.mycommute.BuildConfig;
import com.example.mycommute.network.ArrivalApiError;
import com.example.mycommute.network.KmaWeatherClient;
import com.example.mycommute.network.LiveArrivalClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Debug-only live provider wire/schema probe. At most 4 network requests per run:
 * subway 1, TAGO nearby stops 1 + station arrival 1, KMA forecast 1.
 * Fixed, non-personal sample parameters; no route search or device location.
 * Never reports API keys, URLs, raw responses, stop identifiers or tokens.
 */
public final class ApiProbe {
    public interface Callback { void done(String report); }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final long COOLDOWN_MS=15*60_000L;
    private final StringBuilder log=new StringBuilder();
    private final Context context;
    private int requested;
    private int success;
    private int failed;
    private int skipped;
    private ApiProbe(Context context){this.context=context.getApplicationContext();}
    public static void run(Context c,Callback cb){
        ApiProbe probe=new ApiProbe(c);
        long now=System.currentTimeMillis();
        android.content.SharedPreferences prefs=c.getSharedPreferences(
                "provider_probe_debug_only",Context.MODE_PRIVATE);
        long last=prefs.getLong("last_run",0);
        if(last>0&&now>=last&&now-last<COOLDOWN_MS){
            cb.done("LIVE API PROBE\\nSTATUS=COOLDOWN\\nNEXT_IN_SEC="+
                    ((COOLDOWN_MS-(now-last)+999)/1000)+
                    "\\n실 API 호출을 제한하기 위해 15분당 한 번만 실행할 수 있어.");
            return;
        }
        prefs.edit().putLong("last_run",now).apply();
        IO.execute(()->{
            try{probe.runAll();}
            catch(Exception e){probe.log.append("INTERNAL_ERROR=")
                    .append(e.getClass().getSimpleName()).append('\n');}
            MAIN.post(()->cb.done(probe.log.toString()));
        });
    }
    private static String key(String raw) throws Exception{
        if(raw==null)return "";
        String s=raw.trim();
        if(s.matches(".*%[0-9a-fA-F]{2}.*")) {
            try{s=URLDecoder.decode(s,"UTF-8");}
            catch(IllegalArgumentException ignored){}
        }
        return URLEncoder.encode(s,"UTF-8");
    }
    private static String enc(String raw) throws Exception{
        return URLEncoder.encode(raw,"UTF-8");
    }
    private static String fields(JSONObject object){
        if(object==null)return "(none)";
        JSONArray keys=object.names();
        if(keys==null)return "(empty object)";
        StringBuilder out=new StringBuilder();
        for(int i=0;i<Math.min(keys.length(),32);i++){
            if(i>0)out.append(",");
            out.append(keys.optString(i).replaceAll("[^a-zA-Z0-9_]", ""));
        }
        if(keys.length()>32)out.append(",...(+").append(keys.length()-32).append(")");
        return out.toString();
    }
    private static JSONArray list(JSONObject parent,String key){
        if(parent==null)return new JSONArray();
        Object obj=parent.opt(key);
        if(obj instanceof JSONArray)return (JSONArray)obj;
        JSONArray a=new JSONArray();
        if(obj instanceof JSONObject)a.put(obj);
        return a;
    }
    private static JSONObject first(JSONArray arr){return arr.length()>0?arr.optJSONObject(0):null;}
    private static String text(InputStream stream,int max) throws Exception{
        if(stream==null)return "";
        try(BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            char[] buffer=new char[4096];
            int count;StringBuilder b=new StringBuilder();
            while((count=r.read(buffer))!=-1){
                if(b.length()+count>max)throw new IllegalStateException("RESPONSE_SIZE_EXCEEDED");
                b.append(buffer,0,count);
            }
            return b.toString();
        }
    }
    private static final class Response {
        int status,size;
        long elapsed;
        String body;
    }
    private Response call(String name,String url) {
        requested++;
        HttpURLConnection conn=null;
        long start=System.nanoTime();
        Response result=new Response();
        try{
            conn=(HttpURLConnection)new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(18000);
            conn.setRequestProperty("Accept","application/json");
            result.status=conn.getResponseCode();
            result.body=text(result.status>=200&&result.status<300
                    ?conn.getInputStream():conn.getErrorStream(),1_800_000);
            result.size=result.body.length();
        }catch(Exception ex){
            result.body="";
            result.status=-1;
            log.append(name).append(" TRANSPORT_ERROR=")
                    .append(ex instanceof java.net.SocketTimeoutException
                            ?"TIMEOUT":ex.getClass().getSimpleName()).append('\n');
        }finally{
            result.elapsed=(System.nanoTime()-start)/1_000_000;
            if(conn!=null)conn.disconnect();
        }
        log.append(name).append(" HTTP=").append(result.status)
                .append(" ELAPSED_MS=").append(result.elapsed)
                .append(" CHARS=").append(result.size).append('\n');
        if(result.status!=200){
            failed++;
            if(result.status>0)log.append(name).append(" ERROR_CLASS=")
                    .append(ArrivalApiError.describe(name,result.status,result.body))
                    .append('\n');
        }
        return result;
    }
    private void skip(String name,String reason){
        skipped++;
        log.append(name).append(" SKIP=").append(reason).append('\n');
    }
    private void schema(String name,JSONObject object,boolean valid,String desc){
        if(valid)success++;else failed++;
        log.append(name).append(" ").append(valid?"SCHEMA_OK":"SCHEMA_FAIL")
                .append(" ").append(desc).append('\n')
                .append(name).append(" FIELDS=").append(fields(object)).append('\n');
    }
    private void runAll(){
        log.append("MY-COMMUTE LIVE API PROBE\\n");
        log.append("RUN_AT=").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z",Locale.US)
                .format(new java.util.Date())).append('\n');
        log.append("ENV=DEBUG SDK=").append(android.os.Build.VERSION.SDK_INT).append('\n');
        log.append("PARAMS=sample Seoul station / Seoul Station bus transfer / Seoul weather grid");
        log.append("\\nSAFETY=4 requests max; one run per 15min; no keys/raw body/URL in report");
        log.append("\\nRESULT_SCOPE=HTTP, envelope schema, field presence, provider response codes, timing");
        log.append("\\n\n=== SUBWAY: realtimeStationArrival ===\n");
        subway();
        log.append("\\n=== TAGO BUS: nearby stops + arrival ===\n");
        bus();
        log.append("\\n=== KMA: village forecast ===\n");
        weather();
        log.append("\\n=== SUMMARY ===\n");
        log.append("HTTP_REQUESTS=").append(requested)
                .append(" SCHEMA_OK=").append(success)
                .append(" FAILED=").append(failed)
                .append(" SKIPPED=").append(skipped).append('\n');
        log.append("NOTE=HTTP 200 with zero items means provider reachable, NOT real-time ETA verified.\n");
        log.append("NOTE=This probe does not exercise matching to saved user commute routes.\n");
        log.append("END LIVE API PROBE\n");
    }
    private void subway(){
        if(!LiveArrivalClient.subwayEnabled()){skip("SUBWAY","KEY_MISSING");return;}
        try {
            String url="http://swopenAPI.seoul.go.kr/api/subway/"
                    +enc(BuildConfig.SEOUL_SUBWAY_API_KEY)
                    +"/json/realtimeStationArrival/0/30/"+enc("서울");
            Response r=call("SUBWAY",url);
            if(r.status!=200)return;
            JSONObject root=new JSONObject(r.body);
            JSONObject error=root.optJSONObject("errorMessage");
            JSONArray rows=list(root,"realtimeArrivalList");
            String code=error==null?"NOT_PRESENT":error.optString("status","EMPTY");
            log.append("SUBWAY PROVIDER_STATUS=").append(code.replaceAll("[^a-zA-Z0-9_-]",""))
                    .append(" ITEM_COUNT=").append(rows.length()).append('\n');
            JSONObject sample=first(rows);
            schema("SUBWAY",root,error!=null||root.has("realtimeArrivalList"),
                    "envelope errorMessage/realtimeArrivalList");
            schema("SUBWAY-ROW",sample,sample!=null
                    &&sample.has("subwayId")&&sample.has("statnNm"),
                    "required=subwayId,statnNm; optional=trainLineNm,barvlDt,recptnDt");
            if(sample!=null){
                int timed=0,statusOnly=0,hasDirection=0;
                for(int i=0;i<rows.length();i++){
                    JSONObject o=rows.optJSONObject(i);
                    if(o==null)continue;
                    if(o.optInt("barvlDt",-1)>0)timed++;
                    else if(o.has("arvlMsg2")||o.has("arvlCd"))statusOnly++;
                    if(o.has("trainLineNm"))hasDirection++;
                }
                log.append("SUBWAY ETA_ROWS=").append(timed)
                        .append(" STATUS_ONLY_ROWS=").append(statusOnly)
                        .append(" DIRECTION_FIELD_ROWS=").append(hasDirection).append('\n');
            }
        }catch(Exception e){failed++;log.append("SUBWAY PARSE_ERROR=")
                .append(e.getClass().getSimpleName()).append('\n');}
    }
    private void bus(){
        if(!LiveArrivalClient.busEnabled()){skip("TAGO","KEY_MISSING");return;}
        try{
            String base="https://apis.data.go.kr/1613000/";
            String query="BusSttnInfoInqireService/getCrdntPrxmtSttnList"
                    +"?serviceKey="+key(BuildConfig.TAGO_BUS_SERVICE_KEY)
                    +"&_type=json&pageNo=1&numOfRows=100&gpsLati=37.555&gpsLong=126.972";
            Response stopsResponse=call("TAGO-STOPS",base+query);
            if(stopsResponse.status!=200){skip("TAGO-ARRIVALS","UPSTREAM_STOP_HTTP_ERROR");return;}
            JSONObject root=new JSONObject(stopsResponse.body);
            JSONObject response=root.optJSONObject("response");
            JSONObject header=response==null?null:response.optJSONObject("header");
            JSONObject body=response==null?null:response.optJSONObject("body");
            JSONArray stops=list(body==null?null:body.optJSONObject("items"),"item");
            String code=header==null?"MISSING":header.optString("resultCode","MISSING");
            log.append("TAGO-STOPS PROVIDER_CODE=").append(code.replaceAll("[^a-zA-Z0-9_-]",""))
                    .append(" ITEM_COUNT=").append(stops.length()).append('\n');
            if(!"00".equals(code)){
                schema("TAGO-STOPS",header,false,"provider resultCode != 00");
                skip("TAGO-ARRIVALS","UPSTREAM_PROVIDER_ERROR");
                return;
            }
            schema("TAGO-STOPS",first(stops),first(stops)!=null
                    &&first(stops).has("nodeid")&&first(stops).has("citycode"),
                    "required=nodeid,citycode; sample stop IDs NOT printed");
            JSONObject first=first(stops);
            if(first==null||first.optString("nodeid","").isEmpty()
                    ||first.optString("citycode","").isEmpty()){
                skip("TAGO-ARRIVALS","NO_USABLE_STOP_ID");return;
            }
            String arrivals="ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList"
                    +"?serviceKey="+key(BuildConfig.TAGO_BUS_SERVICE_KEY)
                    +"&_type=json&pageNo=1&numOfRows=100"
                    +"&cityCode="+enc(first.optString("citycode"))
                    +"&nodeId="+enc(first.optString("nodeid"));
            Response r=call("TAGO-ARRIVALS",base+arrivals);
            if(r.status!=200)return;
            JSONObject incoming=new JSONObject(r.body);
            JSONObject resp=incoming.optJSONObject("response");
            JSONObject h=resp==null?null:resp.optJSONObject("header");
            JSONObject b=resp==null?null:resp.optJSONObject("body");
            JSONArray rows=list(b==null?null:b.optJSONObject("items"),"item");
            String arrivalCode=h==null?"MISSING":h.optString("resultCode","MISSING");
            log.append("TAGO-ARRIVALS PROVIDER_CODE=").append(arrivalCode.replaceAll("[^a-zA-Z0-9_-]",""))
                    .append(" ITEM_COUNT=").append(rows.length()).append('\n');
            schema("TAGO-ARRIVALS",first(rows),"00".equals(arrivalCode)
                    &&first(rows)!=null&&first(rows).has("routeno"),
                    "required=routeno; optional=arrtime,arrtime2,arrprevstationcnt");
            int eta=0;
            for(int i=0;i<rows.length();i++){
                JSONObject o=rows.optJSONObject(i);
                if(o!=null&&(o.optInt("arrtime",-1)>0||o.optInt("arrtime2",-1)>0))eta++;
            }
            log.append("TAGO-ARRIVALS ETA_ROWS=").append(eta).append('\n');
        }catch(Exception e){failed++;log.append("TAGO PARSE_ERROR=")
                .append(e.getClass().getSimpleName()).append('\n');}
    }
    private void weather(){
        if(!KmaWeatherClient.enabled()){skip("KMA","KEY_MISSING");return;}
        try{
            Calendar issued=Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
            issued.add(Calendar.MINUTE,-40);
            int[] bases={2,5,8,11,14,17,20,23};
            int hour=issued.get(Calendar.HOUR_OF_DAY),baseHour=-1;
            for(int h:bases)if(h<=hour)baseHour=h;
            if(baseHour<0){issued.add(Calendar.DATE,-1);baseHour=23;}
            issued.set(Calendar.HOUR_OF_DAY,baseHour);
            String date=new SimpleDateFormat("yyyyMMdd",Locale.US).format(issued.getTime());
            String hh=String.format(Locale.US,"%02d00",baseHour);
            String url="https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
                    +"?serviceKey="+key(BuildConfig.KMA_SERVICE_KEY)
                    +"&pageNo=1&numOfRows=1000&dataType=JSON"
                    +"&base_date="+date+"&base_time="+hh+"&nx=60&ny=127";
            log.append("KMA REQUEST_BASE_KST=").append(date).append("/").append(hh)
                    .append(" NX=60 NY=127\n");
            Response r=call("KMA",url);
            if(r.status!=200)return;
            JSONObject root=new JSONObject(r.body);
            JSONObject resp=root.optJSONObject("response");
            JSONObject header=resp==null?null:resp.optJSONObject("header");
            JSONObject body=resp==null?null:resp.optJSONObject("body");
            JSONArray rows=list(body==null?null:body.optJSONObject("items"),"item");
            String code=header==null?"MISSING":header.optString("resultCode","MISSING");
            log.append("KMA PROVIDER_CODE=").append(code.replaceAll("[^a-zA-Z0-9_-]",""))
                    .append(" ITEM_COUNT=").append(rows.length()).append('\n');
            JSONObject sample=first(rows);
            schema("KMA",header,"00".equals(code),"response.header.resultCode == 00");
            schema("KMA-ITEM",sample,sample!=null&&sample.has("category")
                    &&sample.has("fcstDate")&&sample.has("fcstTime")
                    &&sample.has("fcstValue"),
                    "required=category,fcstDate,fcstTime,fcstValue");
            int tmp=0,pty=0,pop=0;
            String earliest="",latest="";
            for(int i=0;i<rows.length();i++){
                JSONObject o=rows.optJSONObject(i);if(o==null)continue;
                String cat=o.optString("category","");
                if("TMP".equals(cat))tmp++;
                if("PTY".equals(cat))pty++;
                if("POP".equals(cat))pop++;
                String when=o.optString("fcstDate","")+o.optString("fcstTime","");
                if(when.length()==12){
                    if(earliest.isEmpty()||when.compareTo(earliest)<0)earliest=when;
                    if(latest.isEmpty()||when.compareTo(latest)>0)latest=when;
                }
            }
            log.append("KMA CATEGORY_COUNTS TMP=").append(tmp)
                    .append(" PTY=").append(pty).append(" POP=").append(pop).append('\n');
            log.append("KMA FORECAST_RANGE=").append(earliest)
                    .append("..").append(latest).append('\n');
            if(rows.length()==1000)log.append(
                    "KMA WARNING=PAGE_FULL; totalCount/pageNo may require pagination for complete coverage\n");
        }catch(Exception e){failed++;log.append("KMA PARSE_ERROR=")
                .append(e.getClass().getSimpleName()).append('\n');}
    }
}
