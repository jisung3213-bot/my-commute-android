package com.example.mycommute.network;

import android.os.Handler;
import android.os.Looper;
import com.example.mycommute.BuildConfig;
import com.example.mycommute.engine.WeatherRouteRanker;
import com.example.mycommute.engine.CommuteWindow;
import com.example.mycommute.model.Routine;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** KMA short-range forecast for the next selected arrival date; no fake fair-weather default. */
public final class KmaWeatherClient {
    public interface Callback {void success(Report report);void error(String reason);}
    public static final class Report {
        public final WeatherRouteRanker.Conditions conditions;
        public final String label, forecastAt;
        public final double temperatureC;
        public final int rainProbability, precipitationType;
        Report(WeatherRouteRanker.Conditions conditions,String label,
               String forecastAt,double temperatureC,int rainProbability,int precipitationType) {
            this.conditions=conditions;this.label=label;this.forecastAt=forecastAt;
            this.temperatureC=temperatureC;this.rainProbability=rainProbability;
            this.precipitationType=precipitationType;
        }
        public String temperatureText(){
            return Double.isFinite(temperatureC)
                    ?String.format(Locale.KOREA,"%.0f°C",temperatureC):"기온 미확인";
        }
        public String conditionText(){
            String rain;
            if(precipitationType==0)rain="강수 없음";
            else if(precipitationType==1)rain="비";
            else if(precipitationType==2)rain="비·눈";
            else if(precipitationType==3)rain="눈";
            else if(precipitationType==4)rain="소나기";
            else rain="강수형태 미확인";
            return rain+(rainProbability>=0?" · 강수확률 "+rainProbability+"%":"");
        }
    }
    private static final ExecutorService NETWORK=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final TimeZone KST=TimeZone.getTimeZone("Asia/Seoul");
    private KmaWeatherClient(){}

    public static boolean enabled() {
        String key=BuildConfig.KMA_SERVICE_KEY.trim();
        return !key.isEmpty()&&!"YOUR_DECODED_KMA_SERVICE_KEY_HERE".equals(key);
    }

    public static void forecast(final Routine r,final Callback cb) {
        if(!enabled()) {MAIN.post(()->cb.error("기상청 API 키가 없어. 기상정보를 적용하지 않았어"));return;}
        if(!r.validCoordinates()) {MAIN.post(()->cb.error("기상 조회 위치가 없어"));return;}
        NETWORK.execute(()-> {
            try {
                Calendar at=targetArrival(r);
                Calendar issued=Calendar.getInstance(KST);
                issued.add(Calendar.MINUTE,-40); // Wait for the latest released forecast.
                int[] bases={2,5,8,11,14,17,20,23};
                int hour=issued.get(Calendar.HOUR_OF_DAY),baseHour=-1;
                for(int value:bases)if(value<=hour)baseHour=value;
                if(baseHour<0){issued.add(Calendar.DATE,-1);baseHour=23;}
                issued.set(Calendar.HOUR_OF_DAY,baseHour);
                issued.set(Calendar.MINUTE,0);issued.set(Calendar.SECOND,0);
                int[] grid=toGrid(r.startLat,r.startLon);
                String url="https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
                        +"?serviceKey="+URLEncoder.encode(BuildConfig.KMA_SERVICE_KEY,StandardCharsets.UTF_8.name())
                        +"&pageNo=1&numOfRows=1000&dataType=JSON"
                        +"&base_date="+date(issued)+"&base_time="+clock(issued)
                        +"&nx="+grid[0]+"&ny="+grid[1];
                HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
                String text;
                try {
                    connection.setConnectTimeout(10000);connection.setReadTimeout(18000);
                    int code=connection.getResponseCode();
                    if(code!=200)throw new IllegalStateException("기상청 HTTP "+code);
                    try(BufferedReader reader=new BufferedReader(
                            new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8))) {
                        StringBuilder builder=new StringBuilder();
                        char[] data=new char[4096];int n;
                        while((n=reader.read(data))!=-1){
                            if(builder.length()+n>2_000_000)throw new IllegalStateException("기상 응답이 너무 커");
                            builder.append(data,0,n);
                        }
                        text=builder.toString();
                    }
                } finally{connection.disconnect();}
                Report report=parse(text,at);
                MAIN.post(()->cb.success(report));
            }catch(Exception e) {
                String message=e.getMessage()==null?"기상청 조회 실패":e.getMessage();
                MAIN.post(()->cb.error(message));
            }
        });
    }

    private static Report parse(String json,Calendar at) throws Exception {
        JSONObject response=new JSONObject(json).optJSONObject("response");
        JSONObject header=response==null?null:response.optJSONObject("header");
        if(header==null||!"00".equals(header.optString("resultCode")))
            throw new IllegalStateException("기상청 응답 오류: "+(header==null?"빈 응답":header.optString("resultMsg")));
        JSONObject body=response.optJSONObject("body");
        JSONObject items=body==null?null:body.optJSONObject("items");
        JSONArray array=items==null?null:items.optJSONArray("item");
        if(array==null)throw new IllegalStateException("기상청 예보 목록 없음");
        Map<String,Map<String,String>> byHour=new HashMap<>();
        long preferred=at.getTimeInMillis();
        String nearest=null;long distance=Long.MAX_VALUE;
        SimpleDateFormat parser=new SimpleDateFormat("yyyyMMddHHmm",Locale.US);
        parser.setTimeZone(KST);
        for(int i=0;i<array.length();i++){
            JSONObject item=array.optJSONObject(i);if(item==null)continue;
            String key=item.optString("fcstDate")+item.optString("fcstTime");
            if(key.length()!=12)continue;
            byHour.computeIfAbsent(key,k->new HashMap<>())
                    .put(item.optString("category"),item.optString("fcstValue"));
            if(!"TMP".equals(item.optString("category"))&&!"PTY".equals(item.optString("category")))continue;
            long delta=Math.abs(parser.parse(key).getTime()-preferred);
            if(delta<distance){nearest=key;distance=delta;}
        }
        if(nearest==null||distance>2*60*60*1000L)
            throw new IllegalStateException("해당 도착시간대 예보가 아직 제공되지 않아");
        Map<String,String> entries=byHour.get(nearest);
        String pty=entries.get("PTY"),pop=entries.get("POP"),tmp=entries.get("TMP");
        if(pty==null&&pop==null&&tmp==null)
            throw new IllegalStateException("강수·기온 예보를 확인할 수 없어");
        int precipitation=number(pty,-1),chance=number(pop,-1);
        double temp=decimal(tmp,Double.NaN);
        boolean rain=(precipitation>0)||(chance>=60);
        boolean cold=Double.isFinite(temp)&&temp<=0;
        String label="기상청 "+nearest.substring(4,6)+"/"+nearest.substring(6,8)+" "
                +nearest.substring(8,10)+":00 예보";
        if(Double.isFinite(temp))label+=" · "+temp+"℃";
        if(chance>=0)label+=" · 강수확률 "+chance+"%";
        if(precipitation>=0)label+=" · 강수형태 "+(precipitation==0?"없음":"있음");
        return new Report(new WeatherRouteRanker.Conditions(true,rain,false,cold),
                label,nearest.substring(4,6)+"/"+nearest.substring(6,8)+" "
                        +nearest.substring(8,10)+":00",
                temp,chance,precipitation);
    }

    private static int number(String s,int fallback){try{return Integer.parseInt(s);}catch(Exception e){return fallback;}}
    private static double decimal(String s,double fallback){try{return Double.parseDouble(s);}catch(Exception e){return fallback;}}
    private static String date(Calendar c){return String.format(Locale.US,"%04d%02d%02d",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));}
    private static String clock(Calendar c){return String.format(Locale.US,"%02d00",c.get(Calendar.HOUR_OF_DAY));}

    private static Calendar targetArrival(Routine r) throws Exception {
        long now=System.currentTimeMillis();
        long nearest=CommuteWindow.nearestTarget(r,now);
        if(nearest<0)throw new IllegalStateException("유효한 목표 도착시각이 없어");
        // The dashboard is useful outside the commute window too.
        // For distant schedules the provider has no forecast yet: show today's
        // local forecast rather than reporting a broken API connection.
        if(!CommuteWindow.active(r,now)&&Math.abs(nearest-now)>72*60*60_000L)
            return Calendar.getInstance(KST);
        // Within the active travel window use today's trip, including shortly after
        // the scheduled arrival. Otherwise use the next scheduled target.
        if(!CommuteWindow.active(r,now)&&nearest<=now) {
            Calendar target=Calendar.getInstance(KST);
            target.setTimeInMillis(nearest);
            for(int i=0;i<8&&target.getTimeInMillis()<=now;i++)
                target.add(Calendar.DAY_OF_YEAR,1);
            // Seek the next configured weekday rather than assuming tomorrow.
            for(int i=0;i<8;i++){
                if(target.after(Calendar.getInstance(KST))&&r.runsOn(target.get(Calendar.DAY_OF_WEEK)))
                    return target;
                target.add(Calendar.DAY_OF_YEAR,1);
            }
        }
        Calendar result=Calendar.getInstance(KST);
        result.setTimeInMillis(nearest);
        return result;
    }

    /** KMA 5 km Lambert-conformal projection, WGS84 latitude/longitude to nx/ny. */
    static int[] toGrid(double lat,double lon){
        double re=6371.00877/5.0;
        double slat1=Math.toRadians(30),slat2=Math.toRadians(60);
        double olon=Math.toRadians(126),olat=Math.toRadians(38);
        double sn=Math.log(Math.cos(slat1)/Math.cos(slat2))/
                Math.log(Math.tan(Math.PI/4+slat2/2)/Math.tan(Math.PI/4+slat1/2));
        double sf=Math.pow(Math.tan(Math.PI/4+slat1/2),sn)*Math.cos(slat1)/sn;
        double ro=re*sf/Math.pow(Math.tan(Math.PI/4+olat/2),sn);
        double ra=re*sf/Math.pow(Math.tan(Math.PI/4+Math.toRadians(lat)/2),sn);
        double theta=Math.toRadians(lon)-olon;
        if(theta>Math.PI)theta-=2*Math.PI;
        if(theta<-Math.PI)theta+=2*Math.PI;
        theta*=sn;
        return new int[]{(int)Math.floor(ra*Math.sin(theta)+43+0.5),
                (int)Math.floor(ro-ra*Math.cos(theta)+136+0.5)};
    }
}
