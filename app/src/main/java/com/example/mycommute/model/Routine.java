package com.example.mycommute.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Local-first commute routine. All ETA and duration fields are USER ESTIMATES, never live feeds. */
public class Routine {
    public String id = "outbound";
    public String name = "";
    public String arrivalTime = "";
    public int marginMinutes = 10;
    public String startName = "";
    public String endName = "";
    public double startLat = 0, startLon = 0, endLat = 0, endLon = 0;
    public int estimatedMinutes = 0;
    public int normalWalkMinutes = 0;
    public int alertLeadMinutes = 15;
    public String transportMode = "대중교통";
    public String days = ""; // Calendar: Sunday=1, Monday=2 ... Saturday=7.
    public String boardingStopId = "";
    public String boardingRoute = "";
    public String boardingDirection = "";
    public boolean mapVisible = true;
    /** Persist preference only; do not persist provider route details/ETAs. */
    public boolean weatherEnabled = false;
    public boolean apiBased = false;
    public String preferredMode = "";
    /** SHA-256 signature for the user-selected route pattern, not the provider response. */
    public String selectedRouteSignature = "";
    public List<Waypoint> waypoints = new ArrayList<>();

    public boolean isConfigured() { return !startName.trim().isEmpty() && !endName.trim().isEmpty() && (apiBased || (estimatedMinutes>=1 && estimatedMinutes<=720 && normalWalkMinutes>=0 && normalWalkMinutes<=estimatedMinutes)) && marginMinutes>=0 && marginMinutes<=180 && !days.isEmpty() && com.example.mycommute.engine.PlanSchedule.validTime(arrivalTime); }
    public boolean validCoordinates() { return valid(startLat,startLon) && valid(endLat,endLon); }
    public static boolean valid(double lat,double lon) { return lat >= 33 && lat <= 39 && lon >= 124 && lon <= 132; }
    public boolean runsOn(int calendarDay) {
        for (String v: days.split(",")) if (v.trim().equals(String.valueOf(calendarDay))) return true;
        return false;
    }
    public boolean isActiveNow(Calendar now) {
        return !apiBased && isConfigured() && com.example.mycommute.engine.PlanSchedule.active(now,arrivalTime,estimatedMinutes,marginMinutes,days);
    }
    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id",id);o.put("arrivalTime",arrivalTime);o.put("marginMinutes",marginMinutes);
        o.put("name",name);o.put("startName",startName);o.put("endName",endName);
        o.put("startLat",startLat);o.put("startLon",startLon);o.put("endLat",endLat);o.put("endLon",endLon);
        o.put("estimatedMinutes",estimatedMinutes);
        o.put("normalWalkMinutes",normalWalkMinutes);
        o.put("alertLeadMinutes",alertLeadMinutes);
        o.put("transportMode",transportMode);o.put("days",days);
        o.put("boardingStopId",boardingStopId);o.put("boardingRoute",boardingRoute);
        o.put("boardingDirection",boardingDirection);o.put("mapVisible",mapVisible);
        o.put("weatherEnabled",weatherEnabled);o.put("apiBased",apiBased);
        o.put("preferredMode",preferredMode);
        o.put("selectedRouteSignature",selectedRouteSignature);
        JSONArray arr=new JSONArray(); for(Waypoint w:waypoints) arr.put(w.toJson()); o.put("waypoints",arr);
        return o;
    }
    public static Routine fromJson(JSONObject o) {
        Routine r = new Routine();
        r.id=o.optString("id","outbound");r.arrivalTime=o.optString("arrivalTime","");r.marginMinutes=o.optInt("marginMinutes",10);
        r.name=o.optString("name",r.name); r.startName=o.optString("startName");r.endName=o.optString("endName");
        r.startLat=o.optDouble("startLat",0);r.startLon=o.optDouble("startLon",0);r.endLat=o.optDouble("endLat",0);r.endLon=o.optDouble("endLon",0);

        r.estimatedMinutes=o.optInt("estimatedMinutes",0);
        r.normalWalkMinutes=o.optInt("normalWalkMinutes",0);
        r.alertLeadMinutes=o.optInt("alertLeadMinutes",15);
        r.transportMode=o.optString("transportMode",r.transportMode);
        r.days=o.optString("days",r.days);r.boardingStopId=o.optString("boardingStopId");
        r.boardingRoute=o.optString("boardingRoute");r.boardingDirection=o.optString("boardingDirection");
        r.mapVisible=o.optBoolean("mapVisible",true);
        r.weatherEnabled=o.optBoolean("weatherEnabled",false);
        r.apiBased=o.optBoolean("apiBased",false);
        r.preferredMode=o.optString("preferredMode","");
        r.selectedRouteSignature=o.optString("selectedRouteSignature","");
        JSONArray a=o.optJSONArray("waypoints");if(a!=null) for(int i=0;i<a.length();i++) { JSONObject p=a.optJSONObject(i); if(p!=null)r.waypoints.add(Waypoint.fromJson(p)); }
        return r;
    }
}
