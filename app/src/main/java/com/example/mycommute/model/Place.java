package com.example.mycommute.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** A POI choice; coordinates are kept internally and never typed by the user. */
public final class Place {
    public final String name,address;
    public final double lat,lon;
    public Place(String name,String address,double lat,double lon) {
        this.name=name;this.address=address;this.lat=lat;this.lon=lon;
    }
    public static List<Place> parse(String json) throws JSONException {
        JSONObject root=new JSONObject(json);
        JSONObject info=root.optJSONObject("searchPoiInfo");
        JSONObject pois=info==null?null:info.optJSONObject("pois");
        Object rows=pois==null?null:pois.opt("poi");
        List<Place> result=new ArrayList<>();
        JSONArray arr=rows instanceof JSONArray?(JSONArray)rows:new JSONArray();
        if(rows instanceof JSONObject)arr.put(rows);
        for(int i=0;i<arr.length();i++) {
            JSONObject item=arr.optJSONObject(i);
            if(item==null)continue;
            String name=item.optString("name","").trim();
            double lat=coordinate(item,"pnsLat","frontLat","noorLat");
            double lon=coordinate(item,"pnsLon","frontLon","noorLon");
            if(lat<33||lat>39||lon<124||lon>132||name.isEmpty())continue;
            String address=item.optString("upperAddrName","")+" "+
                    item.optString("middleAddrName","")+" "+
                    item.optString("lowerAddrName","")+" "+
                    item.optString("detailAddrName","");
            JSONObject addressList=item.optJSONObject("newAddressList");
            if(addressList!=null) {
                Object addresses=addressList.opt("newAddress");
                JSONObject first=addresses instanceof JSONObject?(JSONObject)addresses:
                        addresses instanceof JSONArray?((JSONArray)addresses).optJSONObject(0):null;
                if(first!=null&&!first.optString("fullAddressRoad","").isEmpty())
                    address=first.optString("fullAddressRoad");
            }
            result.add(new Place(name,address.trim().replaceAll("\\s+"," "),lat,lon));
        }
        return result;
    }
    private static double coordinate(JSONObject obj,String...keys) {
        for(String key:keys) {
            String value=obj.optString(key,"");
            try {
                double n=Double.parseDouble(value);
                if(Double.isFinite(n)&&n!=0)return n;
            } catch(Exception ignored){}
        }
        return 0;
    }
}
