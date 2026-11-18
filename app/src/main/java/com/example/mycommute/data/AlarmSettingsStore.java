package com.example.mycommute.data;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** 계정별 알림 설정. 기본값은 사용자가 요청한 10종 모두 활성화. */
public final class AlarmSettingsStore {
    public static final String PREPARE="prepare", DEPARTURE="departure",
            VEHICLE="vehicle", WALK="walk", TRANSFER="transfer", EXIT="exit",
            DELAY="delay", WEATHER="weather", DEVIATION="deviation", FINISH="finish";
    public static final String[] TYPES={PREPARE,DEPARTURE,VEHICLE,WALK,TRANSFER,EXIT,
            DELAY,WEATHER,DEVIATION,FINISH};
    public static final String[] LABELS={"출발 준비","출발 권장","대중교통 도착 임박",
            "승차지 출발","환승 준비","하차 준비","도착 지연 예상","날씨 주의",
            "경로 이탈","목적지 도착"};
    private AlarmSettingsStore(){}

    private static SharedPreferences prefs(Context c){return RoutineStore.prefs(c);}
    public static boolean overall(Context c){return prefs(c).getBoolean("alerts_enabled",true);}
    public static void setOverall(Context c,boolean enabled){
        prefs(c).edit().putBoolean("alerts_enabled",enabled).apply();
    }
    public static boolean enabled(Context c,String type){
        if(!overall(c))return false;
        for(String known:TYPES)if(known.equals(type))
            return prefs(c).getBoolean("alert_"+type,true);
        return false;
    }
    public static boolean typeEnabled(Context c,String type){
        return prefs(c).getBoolean("alert_"+type,true);
    }
    public static void setType(Context c,String type,boolean enabled){
        for(String known:TYPES)if(known.equals(type)){
            prefs(c).edit().putBoolean("alert_"+type,enabled).apply();return;
        }
    }
    public static int prepareMinutes(Context c){return bound(prefs(c).getInt("alert_prepare_min",15),1,120,15);}
    public static int vehicleMinutes(Context c){return bound(prefs(c).getInt("alert_vehicle_min",5),1,30,5);}
    public static int walkSlackMinutes(Context c){return bound(prefs(c).getInt("alert_walk_slack_min",2),0,20,2);}
    public static int delayMinutes(Context c){return bound(prefs(c).getInt("alert_delay_min",5),1,60,5);}
    private static int bound(int n,int min,int max,int fallback){return n>=min&&n<=max?n:fallback;}
    public static boolean setThresholds(Context c,int prep,int vehicle,int walk,int delay){
        if(prep<1||prep>120||vehicle<1||vehicle>30||walk<0||walk>20||delay<1||delay>60)
            return false;
        prefs(c).edit().putInt("alert_prepare_min",prep)
                .putInt("alert_vehicle_min",vehicle)
                .putInt("alert_walk_slack_min",walk)
                .putInt("alert_delay_min",delay).apply();
        return true;
    }

    public static int defaultMargin(Context c){
        return bound(prefs(c).getInt("default_margin",10),0,120,10);
    }
    public static void setDefaultMargin(Context c,int minutes){
        prefs(c).edit().putInt("default_margin",bound(minutes,0,120,10)).apply();
    }
    public static boolean defaultWeather(Context c){
        return prefs(c).getBoolean("default_weather",true);
    }
    public static void setDefaultWeather(Context c,boolean enabled){
        prefs(c).edit().putBoolean("default_weather",enabled).apply();
    }

    public static JSONObject exportJson(Context c){
        JSONObject result=new JSONObject();
        try {
            result.put("all",overall(c));
            for(String type:TYPES)result.put(type,typeEnabled(c,type));
            result.put("prepareMinutes",prepareMinutes(c));
            result.put("vehicleMinutes",vehicleMinutes(c));
            result.put("walkSlackMinutes",walkSlackMinutes(c));
            result.put("delayMinutes",delayMinutes(c));
            result.put("defaultMargin",defaultMargin(c));
            result.put("defaultWeather",defaultWeather(c));
        }catch(Exception ignored){}
        return result;
    }
    /** 기존 설정 문서와 호환. 없는 필드는 로컬 기본값을 덮어쓰지 않음. */
    public static void importJson(Context c,JSONObject o){
        if(o==null)return;
        SharedPreferences.Editor edit=prefs(c).edit();
        if(o.has("all"))edit.putBoolean("alerts_enabled",o.optBoolean("all",true));
        for(String type:TYPES)
            if(o.has(type))edit.putBoolean("alert_"+type,o.optBoolean(type,true));
        if(o.has("prepareMinutes"))
            edit.putInt("alert_prepare_min",bound(o.optInt("prepareMinutes",15),1,120,15));
        if(o.has("vehicleMinutes"))
            edit.putInt("alert_vehicle_min",bound(o.optInt("vehicleMinutes",5),1,30,5));
        if(o.has("walkSlackMinutes"))
            edit.putInt("alert_walk_slack_min",bound(o.optInt("walkSlackMinutes",2),0,20,2));
        if(o.has("delayMinutes"))
            edit.putInt("alert_delay_min",bound(o.optInt("delayMinutes",5),1,60,5));
        if(o.has("defaultMargin"))
            edit.putInt("default_margin",bound(o.optInt("defaultMargin",10),0,120,10));
        if(o.has("defaultWeather"))
            edit.putBoolean("default_weather",o.optBoolean("defaultWeather",true));
        edit.apply();
    }
}
