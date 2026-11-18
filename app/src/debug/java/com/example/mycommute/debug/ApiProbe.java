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
 * Debug-only live provider wire/schema probe. At most 7 requests per run:
 * subway 1, Seoul bus station 1 + arrival 1, TAGO stops 2 + arrival 1, KMA 1.
 * Fixed, non-personal sample parameters; no route search or device location.
 * Never reports API keys, URLs, raw responses, stop identifiers or tokens.
 */
public final class ApiProbe {
    public interface Callback { void done(String report); }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private final StringBuilder log=new StringBuilder();
    private final Context context;
    private int requested;
    private int success;
    private int failed;
    private int skipped;
    private ApiProbe(Context context){this.context=context.getApplicationContext();}
    public static void run(Context c,Callback cb){
        ApiProbe probe=new ApiProbe(c);
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
            conn.setRequestProperty("Accept",name.startsWith("SEOUL-BUS")?"application/xml":"application/json");
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
    // Diagnostics redact credentials and response payloads, but classify provider error semantics.
    private static String safeCode(String raw){
        if(raw==null)return "MISSING";
        String trimmed=raw.trim();
        if(trimmed.length()>48)return "UNSAFE_LONG_CODE";
        return trimmed.matches("[A-Za-z0-9_-]{1,48}")?trimmed:"UNRECOGNIZED_CODE";
    }
    private static String classifyMessage(String message){
        if(message==null||message.trim().isEmpty())return "NO_MESSAGE";
        String s=message.toLowerCase(Locale.ROOT);
        if(s.contains("limit")||s.contains("quota")||s.contains("한도")||s.contains("초과"))
            return "QUOTA_OR_RATE_LIMIT";
        if(s.contains("key")||s.contains("인증")||s.contains("auth")||
                s.contains("token")||s.contains("access denied"))
            return "AUTH_OR_KEY_REJECTED";
        if(s.contains("permission")||s.contains("승인")||s.contains("권한"))
            return "SERVICE_APPROVAL_REQUIRED";
        if(s.contains("not found")||s.contains("no data")||s.contains("데이터가 없")||
                s.contains("검색 결과")||s.contains("조회 결과"))
            return "NO_DATA";
        if(s.contains("parameter")||s.contains("invalid")||s.contains("요청")||
                s.contains("입력")||s.contains("파라미터"))
            return "PARAMETER_OR_REQUEST_ERROR";
        if(s.contains("maintenance")||s.contains("점검")||s.contains("internal")||
                s.contains("server"))return "PROVIDER_SERVER_OR_MAINTENANCE";
        return "OTHER_PROVIDER_MESSAGE";
    }
    private static void appendProviderError(StringBuilder output,String prefix,JSONObject object){
        if(object==null)return;
        String code=object.optString("code",object.optString("resultCode",
                object.optString("status","")));
        String message=object.optString("message",object.optString("resultMsg",""));
        String developer=object.optString("developerMessage","");
        output.append(prefix).append(" CODE=").append(safeCode(code))
                .append(" MESSAGE_CLASS=").append(classifyMessage(message))
                .append(" DEVELOPER_MESSAGE_CLASS=").append(classifyMessage(developer))
                .append('\n');
    }
    private void runAll(){
        log.append("MY-COMMUTE LIVE API PROBE\n");
        SimpleDateFormat timestamp=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z",Locale.US);
        timestamp.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        log.append("RUN_AT=").append(timestamp.format(new java.util.Date())).append('\n');
        log.append("ENV=DEBUG SDK=").append(android.os.Build.VERSION.SDK_INT).append('\n');
        log.append("PARAMS=sample Seoul station / Seoul and Suwon bus samples / Seoul weather grid");
        log.append("\nSAFETY=7 requests max per run; no local time cooldown; no keys/raw body/URL in report");
        log.append("\nRESULT_SCOPE=HTTP, envelope schema, field presence, provider response codes, timing");
        log.append("\n\n=== SUBWAY: realtimeStationArrival ===\n");
        subway();
        log.append("\n\n=== SEOUL BUS: dedicated BIS service ===\n");
        seoulBus();
        log.append("\n=== TAGO BUS: nearby stops + arrival ===\n");
        bus();
        log.append("\n=== KMA: village forecast ===\n");
        weather();
        log.append("\n=== SUMMARY ===\n");
        log.append("HTTP_REQUESTS=").append(requested)
                .append(" SCHEMA_OK=").append(success)
                .append(" FAILED=").append(failed)
                .append(" SKIPPED=").append(skipped).append('\n');
        log.append("NOTE=HTTP 200 with zero items means provider reachable, NOT real-time ETA verified.\n");
        log.append("NOTE=This probe does not exercise matching to saved user commute routes.\n");
        log.append("END LIVE API PROBE\n");
    }
    private static String normalizedKey(String raw){
        if(raw==null)return "";
        String v=raw.trim();
        if(v.matches(".*%[0-9a-fA-F]{2}.*")){
            try{v=URLDecoder.decode(v,"UTF-8");}
            catch(Exception ignored){}
        }
        return v;
    }
    private void seoulBus(){
        if(!com.example.mycommute.network.SeoulBusClient.enabled()){
            skip("SEOUL-BUS","NO_SHARED_OR_SEOUL_KEY_CONFIGURED");
            return;
        }
        // Equality flag only: no key contents, hashes, URLs, or response payloads.
        String seoul=normalizedKey(BuildConfig.SEOUL_BUS_SERVICE_KEY);
        String tago=normalizedKey(BuildConfig.TAGO_BUS_SERVICE_KEY);
        String kma=normalizedKey(BuildConfig.KMA_SERVICE_KEY);
        log.append("PORTAL_KEY_SHARED SEOUL_EQUALS_TAGO=")
                .append(!seoul.isEmpty()&&seoul.equals(tago))
                .append(" SEOUL_EQUALS_KMA=")
                .append(!seoul.isEmpty()&&seoul.equals(kma))
                .append(" (does NOT imply equal service permissions)").append('\n');
        String encoded;
        try{encoded=enc(seoul);}
        catch(Exception e){failed++;log.append("SEOUL-BUS KEY_ENCODING_ERROR\n");return;}
        // The two independently approved APIs must be probed even if one fails.
        probeSeoulBusEndpoint("SEOUL-BUS-STATION-SERVICE",
                "http://ws.bus.go.kr/api/rest/stationinfo/getStationByName"
                +"?serviceKey="+encoded+"&stSrch="+enc("서울역"),
                "stId","stNm");
        // Fixed public documentation example ID: this checks authorization and envelope,
        // not live arrivals on the user's route (may validly return 0 records).
        probeSeoulBusEndpoint("SEOUL-BUS-ARRIVAL-SERVICE",
                "http://ws.bus.go.kr/api/rest/arrive/getArrInfoByRouteAll"
                +"?serviceKey="+encoded+"&busRouteId=100100118",
                "rtNm","exps1");
    }
    private void probeSeoulBusEndpoint(String name,String url,String required,String optional){
        Response r=call(name,url);
        if(r.status!=200){
            log.append(name).append(" AUTH_OR_GATEWAY_STATUS=")
                    .append(r.status==401?"UNAUTHORIZED":r.status==403?"FORBIDDEN":
                            r.status==429?"RATE_LIMIT":"HTTP_OR_TRANSPORT_FAILURE")
                    .append(" (HTTP status alone cannot distinguish key format vs approval)").append('\n');
            return;
        }
        if(!r.body.trim().startsWith("<")){
            failed++;
            log.append(name).append(" NON_XML_RESPONSE_CLASS=")
                    .append(r.body.trim().startsWith("{")?"JSON":"OTHER").append('\n');
            return;
        }
        try{
            javax.xml.parsers.DocumentBuilderFactory f=javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            f.setFeature("http://xml.org/sax/features/external-general-entities",false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            org.w3c.dom.Document doc=f.newDocumentBuilder().parse(
                    new java.io.ByteArrayInputStream(r.body.getBytes(StandardCharsets.UTF_8)));
            org.w3c.dom.NodeList headers=doc.getElementsByTagName("headerCd");
            String code=headers.getLength()>0?headers.item(0).getTextContent().trim():"MISSING";
            org.w3c.dom.NodeList rows=doc.getElementsByTagName("itemList");
            log.append(name).append(" PROVIDER_CODE=").append(safeCode(code))
                    .append(" ITEM_COUNT=").append(rows.getLength()).append('\n');
            if(!"0".equals(code)&&!"00".equals(code)){
                failed++;
                log.append(name).append(" PROVIDER_REJECTED_OR_ERROR\n");
                return;
            }
            if(rows.getLength()==0){
                skip(name+"-ROW","PROVIDER_ACCEPTED_EMPTY_SAMPLE");
                return;
            }
            org.w3c.dom.Element row=(org.w3c.dom.Element)rows.item(0);
            boolean has=row.getElementsByTagName(required).getLength()>0;
            schema(name+"-ROW",null,has,
                    "XML required="+required+" present; optional="+optional+
                            "; contents redacted");
        }catch(Exception ex){
            failed++;
            log.append(name).append(" XML_ERROR=").append(ex.getClass().getSimpleName())
                    .append('\n');
        }
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
            JSONObject standardResult=root.optJSONObject("RESULT");
            JSONArray rows=list(root,"realtimeArrivalList");
            JSONObject gatewayError=root.has("status")&&root.has("code")?root:null;
            log.append("SUBWAY ENVELOPE=").append(gatewayError!=null?"GATEWAY_ERROR":
                    rows.length()>0?"ARRIVAL_LIST":
                    error!=null?"SEOUL_ERROR_MESSAGE":
                    standardResult!=null?"SEOUL_RESULT":
                    root.has("realtimeArrivalList")?"EMPTY_ARRIVAL_LIST":"UNKNOWN")
                    .append(" ITEM_COUNT=").append(rows.length()).append('\n');
            appendProviderError(log,"SUBWAY-GATEWAY",gatewayError);
            appendProviderError(log,"SUBWAY-STANDARD",standardResult);
            appendProviderError(log,"SUBWAY-ERROR",error);
            log.append("SUBWAY ROOT_FIELDS=").append(fields(root)).append('\n');
            if(gatewayError!=null){
                failed++;
                log.append("SUBWAY DIAGNOSIS=HTTP_200_WITH_PROVIDER_ERROR; NOT_VALID_ARRIVAL_JSON\n");
                // Do not count missing row as a second distinct error.
                return;
            }
            schema("SUBWAY",root,error!=null||root.has("realtimeArrivalList")
                    ||standardResult!=null,"expected real-time API response envelope");
            if(rows.length()==0){
                skip("SUBWAY-ROW","EMPTY_RESULT; cannot validate ETA schema");
                return;
            }
            JSONObject sample=first(rows);
            schema("SUBWAY-ROW",sample,sample!=null
                    &&sample.has("subwayId")&&sample.has("statnNm"),
                    "required=subwayId,statnNm; optional=trainLineNm,barvlDt,recptnDt");
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
        }catch(Exception e){failed++;log.append("SUBWAY PARSE_ERROR=")
                .append(e.getClass().getSimpleName()).append('\n');}
    }
    private static final class StopSample {
        final String cityCode,nodeId;
        StopSample(String cityCode,String nodeId){this.cityCode=cityCode;this.nodeId=nodeId;}
    }
    private StopSample sampleNearbyStop(String label,String latitude,String longitude) throws Exception {
        String base="https://apis.data.go.kr/1613000/";
        String query="BusSttnInfoInqireService/getCrdntPrxmtSttnList"
                +"?serviceKey="+key(BuildConfig.TAGO_BUS_SERVICE_KEY)
                +"&_type=json&pageNo=1&numOfRows=100&gpsLati="+latitude+"&gpsLong="+longitude;
        Response r=call("TAGO-"+label,base+query);
        if(r.status!=200)return null;
        JSONObject root=new JSONObject(r.body);
        JSONObject response=root.optJSONObject("response");
        JSONObject header=response==null?null:response.optJSONObject("header");
        JSONObject body=response==null?null:response.optJSONObject("body");
        JSONArray stops=list(body==null?null:body.optJSONObject("items"),"item");
        String code=header==null?"MISSING":header.optString("resultCode","MISSING");
        int total=body==null?-1:body.optInt("totalCount",-1);
        log.append("TAGO-").append(label).append(" PROVIDER_CODE=").append(safeCode(code))
                .append(" TOTAL_COUNT=").append(total)
                .append(" ITEM_COUNT=").append(stops.length())
                .append(" HAS_ITEMS=").append(body!=null&&body.has("items"))
                .append(" BODY_FIELDS=").append(fields(body)).append('\n');
        appendProviderError(log,"TAGO-"+label+"-HEADER",header);
        if(!"00".equals(code)){
            failed++;
            log.append("TAGO-").append(label).append(" DIAGNOSIS=PROVIDER_ERROR\n");
            return null;
        }
        if(stops.length()==0){
            log.append("TAGO-").append(label)
                    .append(" STATUS=EMPTY_RESULT (provider accepted query; no stop record)\n");
            skip("TAGO-"+label+"-ROW","EMPTY_RESULT is not malformed schema");
            return null;
        }
        JSONObject first=first(stops);
        schema("TAGO-"+label+"-ROW",first,first!=null
                &&first.has("nodeid")&&first.has("citycode"),
                "required=nodeid,citycode; sample IDs not shown");
        return first!=null&&!first.optString("nodeid","").isEmpty()
                &&!first.optString("citycode","").isEmpty()
                ?new StopSample(first.optString("citycode"),first.optString("nodeid")):null;
    }
    private void bus(){
        if(!LiveArrivalClient.busEnabled()){skip("TAGO","KEY_MISSING");return;}
        try {
            // Two independent areas prevent interpreting an empty Seoul query as general TAGO failure.
            StopSample seoul=sampleNearbyStop("SEOUL","37.555","126.972");
            StopSample suwon=sampleNearbyStop("SUWON","37.2655","127.0000");
            StopSample selected=seoul!=null?seoul:suwon;
            if(seoul==null&&suwon!=null)
                log.append("TAGO COMPARISON=SEOUL_EMPTY_OR_UNUSABLE_BUT_SUWON_HAS_STOPS\n");
            if(seoul==null&&suwon==null)
                log.append("TAGO COMPARISON=BOTH_SAMPLES_NO_USABLE_STOP; coverage or coordinates unresolved\n");
            if(selected==null){skip("TAGO-ARRIVALS","NO_USABLE_STOP_IN_EITHER_SAMPLE");return;}
            String base="https://apis.data.go.kr/1613000/";
            String arrivals="ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList"
                    +"?serviceKey="+key(BuildConfig.TAGO_BUS_SERVICE_KEY)
                    +"&_type=json&pageNo=1&numOfRows=100"
                    +"&cityCode="+enc(selected.cityCode)+"&nodeId="+enc(selected.nodeId);
            Response r=call("TAGO-ARRIVALS",base+arrivals);
            if(r.status!=200)return;
            JSONObject incoming=new JSONObject(r.body);
            JSONObject resp=incoming.optJSONObject("response");
            JSONObject h=resp==null?null:resp.optJSONObject("header");
            JSONObject b=resp==null?null:resp.optJSONObject("body");
            JSONArray rows=list(b==null?null:b.optJSONObject("items"),"item");
            String arrivalCode=h==null?"MISSING":h.optString("resultCode","MISSING");
            int total=b==null?-1:b.optInt("totalCount",-1);
            log.append("TAGO-ARRIVALS PROVIDER_CODE=").append(safeCode(arrivalCode))
                    .append(" TOTAL_COUNT=").append(total)
                    .append(" ITEM_COUNT=").append(rows.length())
                    .append(" BODY_FIELDS=").append(fields(b)).append('\n');
            appendProviderError(log,"TAGO-ARRIVALS-HEADER",h);
            if(!"00".equals(arrivalCode)){
                failed++;
                log.append("TAGO-ARRIVALS DIAGNOSIS=PROVIDER_ERROR\n");
                return;
            }
            if(rows.length()==0){
                skip("TAGO-ARRIVALS-ROW","VALID_ENVELOPE_EMPTY_DATA");
                return;
            }
            JSONObject sample=first(rows);
            schema("TAGO-ARRIVALS",sample,sample!=null&&sample.has("routeno"),
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
            SimpleDateFormat dateFormat=new SimpleDateFormat("yyyyMMdd",Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            String date=dateFormat.format(issued.getTime());
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
