package com.example.mycommute.network;

import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Pure matching/parsing helpers. Never use a TMAP station ID as a TAGO nodeId. */
public final class ArrivalMatcher {
    public static final class Stop {
        public final String cityCode,nodeId,name;
        public final double lat,lon;
        Stop(String cityCode,String nodeId,String name,double lat,double lon) {
            this.cityCode=cityCode;this.nodeId=nodeId;this.name=name;this.lat=lat;this.lon=lon;
        }
    }
    private ArrivalMatcher(){}

    public static String transitLine(String text) {
        if(text==null)return "";
        String name=text.trim().replace(" ","");
        // TMAP sometimes returns bus names with a type prefix; preserve regular route numbers.
        name=name.replaceAll("(?i)^버스","").replaceAll("(?i)번버스$","");
        // TMAP route labels may include a category such as "간선:400".
        if(name.contains(":"))name=name.substring(name.lastIndexOf(':')+1);
        return name;
    }

    public static String subwayLineId(String label) {
        if(label==null)return "";
        String name=label.replace(" ","").toLowerCase(Locale.ROOT);
        if(name.contains("신분당"))return "1077";
        if(name.contains("경의중앙")||name.contains("경의·중앙"))return "1063";
        if(name.contains("수인분당")||name.contains("수인·분당"))return "1075";
        if(name.contains("공항철도"))return "1065";
        if(name.contains("경춘"))return "1067";
        if(name.contains("우이신설"))return "1092";
        if(name.contains("경강"))return "1081";
        if(name.contains("gtx-a")||name.contains("gtxa"))return "1032";
        for(int i=1;i<=9;i++)if(name.contains(i+"호선"))return "100"+i;
        return "";
    }

    private static String normalizeStation(String name) {
        if(name==null)return "";
        String n=name.replaceAll("\\([^)]*\\)","")
                .replaceAll("[^가-힣A-Za-z0-9]","")
                .replaceAll("(지하철[0-9]+호선|신분당선|수도권[0-9]+호선)","");
        return n.replaceAll("역$","");
    }

    private static String commonError(JSONObject response) {
        JSONObject header=response.optJSONObject("response");
        JSONObject inner=header==null?null:header.optJSONObject("header");
        if(inner!=null&&!"00".equals(inner.optString("resultCode",""))) {
            return "TAGO 응답 오류: "+inner.optString("resultCode")+" · "
                    +inner.optString("resultMsg","정류소정보 권한·키 확인");
        }
        return "";
    }

    private static JSONArray items(JSONObject response) throws JSONException {
        String message=commonError(response);
        if(!message.isEmpty())throw new JSONException(message);
        JSONObject envelope=response.optJSONObject("response");
        JSONObject body=envelope==null?null:envelope.optJSONObject("body");
        JSONObject items=body==null?null:body.optJSONObject("items");
        Object raw=items==null?null:items.opt("item");
        if(raw instanceof JSONArray)return (JSONArray)raw;
        JSONArray arr=new JSONArray();
        if(raw instanceof JSONObject)arr.put(raw);
        return arr;
    }

    /** TAGO's nearby-stop API returns citycode+nodeid, not TMAP's stationID. */
    public static Stop chooseNearbyStop(String json,TransitRoute.Leg leg) throws JSONException {
        if(!Double.isFinite(leg.startLat)||!Double.isFinite(leg.startLon))
            throw new JSONException("TMAP 승차 정류장 좌표가 없어");
        JSONArray entries=items(new JSONObject(json));
        Stop first=null;double best=Double.MAX_VALUE,second=Double.MAX_VALUE;
        String target=normalizeStation(leg.startName);
        for(int i=0;i<entries.length();i++) {
            JSONObject row=entries.optJSONObject(i);if(row==null)continue;
            String name=row.optString("nodenm","");
            String candidate=normalizeStation(name);
            if(target.isEmpty()||candidate.isEmpty())continue;
            double lat=row.optDouble("gpslati",Double.NaN);
            double lon=row.optDouble("gpslong",Double.NaN);
            if(!Double.isFinite(lat)||!Double.isFinite(lon))continue;
            double dist=meters(leg.startLat,leg.startLon,lat,lon);
            if(dist>70)continue;
            boolean namesMatch=target.equals(candidate)||
                    (dist<=30&&target.length()>=3&&candidate.length()>=3&&
                            (target.endsWith(candidate)||candidate.endsWith(target)));
            if(!namesMatch)continue;
            String city=row.optString("citycode",""),node=row.optString("nodeid","");
            if(city.isEmpty()||node.isEmpty())continue;
            if(dist<best){second=best;best=dist;first=new Stop(city,node,name,lat,lon);}
            else if(dist<second)second=dist;
        }
        // Two identically named stops facing different directions must not be guessed.
        if(first==null||second-best<20)return null;
        return first;
    }

    public static TransitArrival busArrival(String json,TransitRoute.Leg leg,Stop stop,long now)
            throws JSONException {
        String label=transitLine(leg.routeName);
        if(label.isEmpty())return TransitArrival.unavailable("TMAP 버스 노선명이 없어 조회할 수 없어",now);
        JSONArray data=items(new JSONObject(json));
        java.util.TreeSet<Integer> arrivals=new java.util.TreeSet<>();
        int remaining=-1,closest=Integer.MAX_VALUE;
        for(int i=0;i<data.length();i++){
            JSONObject row=data.optJSONObject(i);if(row==null)continue;
            if(!label.equals(transitLine(row.optString("routeno",""))))continue;
            // TAGO's node ID may be omitted in a stop-filtered response.
            String node=row.optString("nodeid","");
            if(!node.isEmpty()&&!stop.nodeId.equals(node))continue;
            for(String field:new String[]{"arrtime","arrtime2"}) {
                int seconds=row.optInt(field,-1);
                if(seconds<=0)continue;
                arrivals.add(seconds);
                if(seconds<closest){
                    closest=seconds;
                    remaining="arrtime2".equals(field)?row.optInt("arrprevstationcnt2",-1)
                            :row.optInt("arrprevstationcnt",-1);
                }
            }
        }
        if(arrivals.isEmpty())
            return TransitArrival.unavailable(stop.name+" · "+label+"번 버스의 실시간 도착정보가 없어",now);
        int first=arrivals.first();
        Integer second=arrivals.higher(first);
        return TransitArrival.found(stop.name,label+"번 버스","TAGO 기준",first,remaining,
                second==null?-1:second,now);
    }

    public static TransitArrival subwayArrival(String json,TransitRoute.Leg leg,long now)
            throws JSONException {
        String line=subwayLineId(leg.routeName);
        String target=normalizeStation(leg.startName);
        String next=normalizeStation(leg.nextStopName);
        if(line.isEmpty())return TransitArrival.unavailable(
                "지하철 호선 식별 실패 · "+leg.routeName,now);
        if(target.isEmpty()||next.isEmpty())return TransitArrival.unavailable(
                "승차역 또는 다음역 정보가 없어 진행 방향을 확인할 수 없어",now);
        JSONObject root=new JSONObject(json);
        JSONObject error=root.optJSONObject("errorMessage");
        if(error!=null&&!error.optString("status","").equals("200"))
            return TransitArrival.unavailable(
                    "서울 지하철 API: "+error.optString("message","조회 실패"),now);
        JSONArray list=root.optJSONArray("realtimeArrivalList");
        if(list==null)return TransitArrival.unavailable("해당 역의 실시간 열차 정보가 없어",now);
        java.util.TreeSet<Integer> arrivals=new java.util.TreeSet<>();
        String direction="";
        for(int i=0;i<list.length();i++){
            JSONObject item=list.optJSONObject(i);if(item==null)continue;
            if(!line.equals(item.optString("subwayId","")))continue;
            if(!normalizeStation(item.optString("statnNm","")).equals(target))continue;
            String text=normalizeStation(item.optString("trainLineNm",""));
            if(!text.contains(next))continue; // Do not show an opposite-direction train.
            long generated=timestamp(item.optString("recptnDt",""));
            long age=now-generated;
            if(generated<=0||age< -30000L||age>5*60000L)continue;
            int raw=item.optInt("barvlDt",-1);
            if(raw<0)continue;
            int corrected=Math.max(0,raw-(int)Math.max(0,age/1000));
            // Arrival and at-platform signals are not a future incoming train.
            if(corrected<=0)continue;
            arrivals.add(corrected);
            if(direction.isEmpty())direction=item.optString("trainLineNm","");
        }
        if(arrivals.isEmpty())
            return TransitArrival.unavailable(
                    leg.startName+" · "+leg.routeName+" · 진행 방향이 일치하는 최신 열차 정보가 없어",now);
        int first=arrivals.first();
        Integer next=arrivals.higher(first);
        return TransitArrival.found(leg.startName,leg.routeName,direction,first,-1,
                next==null?-1:next,now);
    }

    private static long timestamp(String text) {
        try {
            SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);
            format.setLenient(false);format.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            Date date=format.parse(text);return date==null?0:date.getTime();
        }catch(Exception ignored){return 0;}
    }

    private static double meters(double lat1,double lon1,double lat2,double lon2) {
        double phi1=Math.toRadians(lat1),phi2=Math.toRadians(lat2);
        double a=Math.pow(Math.sin((phi2-phi1)/2),2)+Math.cos(phi1)*Math.cos(phi2)*
                Math.pow(Math.sin(Math.toRadians(lon2-lon1)/2),2);
        return 6371000*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }
}
