package com.example.mycommute.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Waypoint {
    public String name = "";
    public String category = "경유";
    public String plannedTime = ""; // HH:mm; optionally used for a meal or coffee reminder.
    public double lat = 0;
    public double lon = 0;

    public boolean hasCoordinates() { return lat >= 33 && lat <= 39 && lon >= 124 && lon <= 132; }
    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name); o.put("category", category); o.put("plannedTime", plannedTime);
        o.put("lat", lat); o.put("lon", lon); return o;
    }
    public static Waypoint fromJson(JSONObject o) {
        Waypoint w = new Waypoint();
        w.name = o.optString("name"); w.category = o.optString("category", "경유");
        w.plannedTime = o.optString("plannedTime"); w.lat = o.optDouble("lat"); w.lon = o.optDouble("lon");
        return w;
    }
}
