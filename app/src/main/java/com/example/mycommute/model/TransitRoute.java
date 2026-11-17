package com.example.mycommute.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A short-lived API result, NOT a persistent commute routine or a live arrival estimate. */
public final class TransitRoute {
    public static final class Point {
        public final double lat, lon;
        public Point(double lat, double lon) { this.lat=lat; this.lon=lon; }
    }

    public static final class Leg {
        public final String mode, routeName, startName, endName;
        /** Boarding point and next stop in TMAP's own dataset (not TAGO nodeId). */
        public final double startLat, startLon;
        public final String nextStopName;
        public final int seconds;
        public final List<Point> points;

        private Leg(String mode,String routeName,String startName,String endName,
                    int seconds,List<Point> points,double startLat,double startLon,String nextStopName) {
            this.mode=mode; this.routeName=routeName; this.startName=startName; this.endName=endName;
            this.seconds=seconds; this.points=Collections.unmodifiableList(points);
            this.startLat=startLat;this.startLon=startLon;this.nextStopName=nextStopName;
        }

        public String title() {
            String type="WALK".equals(mode)?"도보":"BUS".equals(mode)?"버스":
                    "SUBWAY".equals(mode)?"지하철":mode;
            return type+(routeName.isEmpty()?"":" "+routeName)+" "+minutes(seconds)+"분 · "
                    +startName+" → "+endName;
        }
    }

    public final int totalSeconds, walkSeconds, walkMeters, transfers, fareWon;
    public final List<Leg> legs;

    private TransitRoute(int totalSeconds,int walkSeconds,int walkMeters,int transfers,int fareWon,
                         List<Leg> legs) {
        this.totalSeconds=totalSeconds; this.walkSeconds=walkSeconds; this.walkMeters=walkMeters;
        this.transfers=transfers; this.fareWon=fareWon;
        this.legs=Collections.unmodifiableList(legs);
    }

    /**
     * Concatenates separately requested TMAP A→waypoint→B itineraries.
     * The sum is an estimate; includes no guaranteed transfer synchronization.
     */
    public static TransitRoute combine(List<TransitRoute> sections) {
        if(sections==null||sections.isEmpty())throw new IllegalArgumentException("경로 구간이 없어");
        int duration=0,walk=0,meters=0,transfers=0,fare=0;
        List<Leg> out=new ArrayList<>();
        for(TransitRoute section:sections){
            duration+=section.totalSeconds;walk+=section.walkSeconds;
            meters+=section.walkMeters;transfers+=section.transfers;
            fare+=section.fareWon;out.addAll(section.legs);
        }
        return new TransitRoute(duration,walk,meters,transfers,fare,out);
    }

    /** Provider-derived cache: only allowed to be read within the local 23h TTL. */
    public JSONObject toCacheJson() throws JSONException {
        JSONObject object=new JSONObject();
        object.put("duration",totalSeconds);object.put("walk",walkSeconds);
        object.put("meters",walkMeters);object.put("transfers",transfers);object.put("fare",fareWon);
        JSONArray array=new JSONArray();
        for(Leg leg:legs){
            JSONObject item=new JSONObject();
            item.put("mode",leg.mode);item.put("line",leg.routeName);
            item.put("start",leg.startName);item.put("end",leg.endName);
            item.put("seconds",leg.seconds);
            item.put("lat",Double.isFinite(leg.startLat)?leg.startLat:JSONObject.NULL);
            item.put("lon",Double.isFinite(leg.startLon)?leg.startLon:JSONObject.NULL);
            item.put("next",leg.nextStopName);
            JSONArray points=new JSONArray();
            for(Point point:leg.points) {
                JSONArray xy=new JSONArray();xy.put(point.lat);xy.put(point.lon);points.put(xy);
            }
            item.put("points",points);array.put(item);
        }
        object.put("legs",array);
        return object;
    }

    public static TransitRoute fromCacheJson(JSONObject object) throws JSONException {
        JSONArray rows=object.getJSONArray("legs");
        List<Leg> legs=new ArrayList<>();
        for(int i=0;i<rows.length();i++){
            JSONObject item=rows.getJSONObject(i);
            List<Point> pts=new ArrayList<>();
            JSONArray points=item.optJSONArray("points");
            if(points!=null)for(int j=0;j<points.length();j++){
                JSONArray point=points.optJSONArray(j);
                if(point!=null&&point.length()==2)pts.add(new Point(point.getDouble(0),point.getDouble(1)));
            }
            legs.add(new Leg(item.optString("mode",""),item.optString("line",""),
                    item.optString("start",""),item.optString("end",""),
                    item.optInt("seconds",0),pts,
                    item.optDouble("lat",Double.NaN),item.optDouble("lon",Double.NaN),
                    item.optString("next","")));
        }
        if(legs.isEmpty())throw new JSONException("캐시 경로 구간 없음");
        return new TransitRoute(object.optInt("duration",0),object.optInt("walk",0),
                object.optInt("meters",0),object.optInt("transfers",0),
                object.optInt("fare",0),legs);
    }

    public static int minutes(int seconds) { return Math.max(0,(seconds+59)/60); }

    public String summary(int ordinal) {
        return String.format(Locale.KOREA,"%d번 경로 · %d분 · 도보 %d분(%dm) · 환승 %d회",
                ordinal,minutes(totalSeconds),minutes(walkSeconds),walkMeters,transfers)
                +(fareWon>0?String.format(Locale.KOREA," · %,d원",fareWon):"");
    }

    public String stepsSummary() {
        StringBuilder out=new StringBuilder();
        for(Leg leg:legs) {
            if(out.length()>0)out.append('\n');
            out.append("• ").append(leg.title());
        }
        return out.toString();
    }

    public static List<TransitRoute> parse(String response) throws JSONException {
        JSONObject root=new JSONObject(response);
        JSONObject metadata=root.optJSONObject("metaData");
        JSONObject plan=metadata==null?null:metadata.optJSONObject("plan");
        JSONArray list=plan==null?null:plan.optJSONArray("itineraries");
        if(list==null)throw new JSONException("TMAP 응답에 경로 목록이 없음");
        List<TransitRoute> routes=new ArrayList<>();
        for(int i=0;i<list.length();i++) {
            JSONObject item=list.optJSONObject(i);
            if(item==null)continue;
            JSONArray legArray=item.optJSONArray("legs");
            if(legArray==null)continue;
            List<Leg> legs=new ArrayList<>();
            for(int j=0;j<legArray.length();j++) {
                JSONObject leg=legArray.optJSONObject(j);
                if(leg==null)continue;
                String mode=leg.optString("mode","");
                String line=leg.optString("route","");
                JSONArray lanes=leg.optJSONArray("Lane");
                if(lanes==null)lanes=leg.optJSONArray("lane");
                if(line.isEmpty()&&lanes!=null&&lanes.length()>0) {
                    JSONObject first=lanes.optJSONObject(0);
                    if(first!=null)line=first.optString("route","");
                }
                JSONObject start=leg.optJSONObject("start"),end=leg.optJSONObject("end");
                String startName=start==null?"출발":start.optString("name","출발");
                String endName=end==null?"도착":end.optString("name","도착");
                JSONObject passStopList=leg.optJSONObject("passStopList");
                JSONArray stationList=passStopList==null?null:passStopList.optJSONArray("stationList");
                if(stationList==null&&passStopList!=null)stationList=passStopList.optJSONArray("stations");
                JSONObject firstStation=stationList==null?null:stationList.optJSONObject(0);
                JSONObject nextStation=stationList!=null&&stationList.length()>1?stationList.optJSONObject(1):null;
                String nextName=nextStation==null?"":nextStation.optString("stationName","");
                double boardingLat=firstStation!=null?firstStation.optDouble("lat",Double.NaN):Double.NaN;
                double boardingLon=firstStation!=null?firstStation.optDouble("lon",Double.NaN):Double.NaN;
                if(!Double.isFinite(boardingLat)||!Double.isFinite(boardingLon)) {
                    boardingLat=start==null?Double.NaN:start.optDouble("lat",Double.NaN);
                    boardingLon=start==null?Double.NaN:start.optDouble("lon",Double.NaN);
                }
                if(firstStation!=null&&!firstStation.optString("stationName","").isEmpty())
                    startName=firstStation.optString("stationName");
                List<Point> geometry=new ArrayList<>();
                JSONObject shape=leg.optJSONObject("passShape");
                if(shape!=null)appendLine(geometry,shape.optString("linestring",""));
                if(geometry.isEmpty()&&"WALK".equals(mode)) {
                    JSONArray steps=leg.optJSONArray("steps");
                    if(steps!=null)for(int k=0;k<steps.length();k++) {
                        JSONObject step=steps.optJSONObject(k);
                        if(step!=null)appendLine(geometry,step.optString("linestring",""));
                    }
                }
                legs.add(new Leg(mode,line,startName,endName,
                        Math.max(0,leg.optInt("sectionTime",0)),geometry,
                        boardingLat,boardingLon,nextName));
            }
            if(legs.isEmpty())continue;
            JSONObject fare=item.optJSONObject("fare");
            JSONObject regular=fare==null?null:fare.optJSONObject("regular");
            routes.add(new TransitRoute(
                    Math.max(0,item.optInt("totalTime",0)),
                    Math.max(0,item.optInt("totalWalkTime",0)),
                    Math.max(0,item.optInt("totalWalkDistance",0)),
                    Math.max(0,item.optInt("transferCount",0)),
                    regular==null?0:Math.max(0,regular.optInt("totalFare",0)),legs));
        }
        return routes;
    }

    /** Official linestring format: longitude,latitude longitude,latitude ... */
    private static void appendLine(List<Point> points,String line) {
        if(line==null||line.trim().isEmpty())return;
        for(String pair:line.trim().split("\\s+")) {
            String[] xy=pair.split(",");
            if(xy.length!=2)continue;
            try {
                double lon=Double.parseDouble(xy[0]),lat=Double.parseDouble(xy[1]);
                if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)continue;
                if(!points.isEmpty()) {
                    Point prev=points.get(points.size()-1);
                    if(prev.lat==lat&&prev.lon==lon)continue;
                }
                points.add(new Point(lat,lon));
            } catch(NumberFormatException ignored) { }
        }
    }
}
