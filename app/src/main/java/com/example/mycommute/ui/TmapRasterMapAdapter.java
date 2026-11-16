package com.example.mycommute.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import org.osmdroid.util.GeoPoint;
import com.example.mycommute.model.TransitRoute;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * TMAP Raster Android SDK adapter. Loads the vendor's JAR/AAR at runtime,
 * so the app remains buildable even when the locally licensed SDK is absent.
 * This is NOT the Vector Map SDK (its initialization contract differs).
 */
final class TmapRasterMapAdapter {
    private static final String PACKAGE="com.skt.Tmap.";
    private final Object map;
    private final Class<?> pointClass;
    private final Class<?> markerClass;
    private final Class<?> lineClass;
    private final List<String> markers=new ArrayList<>();
    private boolean routeAdded;
    private final List<String> transitLines=new ArrayList<>();

    static boolean isAvailable() {
        try {Class.forName(PACKAGE+"TMapView");return true;}
        catch(ClassNotFoundException|LinkageError e){return false;}
    }

    TmapRasterMapAdapter(Context context,String appKey) throws Exception {
        if(appKey==null||appKey.isEmpty())throw new IllegalArgumentException("TMAP AppKey 누락");
        Class<?> mapClass=Class.forName(PACKAGE+"TMapView");
        pointClass=Class.forName(PACKAGE+"TMapPoint");
        markerClass=Class.forName(PACKAGE+"TMapMarkerItem");
        lineClass=Class.forName(PACKAGE+"TMapPolyLine");
        Constructor<?> constructor=mapClass.getConstructor(Context.class);
        map=constructor.newInstance(context);
        call(map,"setSKTMapApiKey",new Class[]{String.class},appKey);
        setCenter(37.5664,126.9778,12);
    }

    View view(){return (View)map;}

    /** Raster SDK expects longitude first. */
    void setCenter(double latitude,double longitude,int zoom) throws Exception {
        call(map,"setCenterPoint",new Class[]{double.class,double.class},longitude,latitude);
        call(map,"setZoomLevel",new Class[]{int.class},zoom);
    }

    private Object point(double lat,double lon) throws Exception {
        return pointClass.getConstructor(double.class,double.class).newInstance(lat,lon);
    }

    void addMarker(String id,String title,double lat,double lon) throws Exception {
        Object marker=markerClass.getConstructor().newInstance();
        call(marker,"setTMapPoint",new Class[]{pointClass},point(lat,lon));
        call(marker,"setName",new Class[]{String.class},title);
        call(map,"addMarkerItem",new Class[]{String.class,markerClass},id,marker);
        markers.add(id);
    }

    void clearMarkers() throws Exception {
        for(String id:markers)call(map,"removeMarkerItem",new Class[]{String.class},id);
        markers.clear();
    }

    void drawRoute(List<GeoPoint> coords) throws Exception {
        clearRoute();
        Object line=lineClass.getConstructor().newInstance();
        for(GeoPoint p:coords)call(line,"addLinePoint",new Class[]{pointClass},point(p.getLatitude(),p.getLongitude()));
        call(line,"setLineColor",new Class[]{int.class},Color.rgb(99,87,181));
        // Width is optional across vendor releases.
        try {call(line,"setLineWidth",new Class[]{float.class},5f);}
        catch(NoSuchMethodException e){
            try{call(line,"setLineWidth",new Class[]{int.class},5);}
            catch(NoSuchMethodException ignored){}
        }
        call(map,"addTMapPolyLine",new Class[]{String.class,lineClass},"commute-route",line);
        routeAdded=true;
    }

    void drawTransit(List<TransitRoute.Leg> legs) throws Exception {
        clearRoute();
        for(int i=0;i<legs.size();i++) {
            TransitRoute.Leg leg=legs.get(i);
            if(leg.points.size()<2)continue;
            Object line=lineClass.getConstructor().newInstance();
            for(TransitRoute.Point point:leg.points)
                call(line,"addLinePoint",new Class[]{pointClass},point(point.lat,point.lon));
            int color="BUS".equals(leg.mode)?Color.rgb(43,111,214):
                    "SUBWAY".equals(leg.mode)?Color.rgb(214,62,81):Color.rgb(102,102,102);
            call(line,"setLineColor",new Class[]{int.class},color);
            try{call(line,"setLineWidth",new Class[]{float.class},5f);}
            catch(NoSuchMethodException e){
                try{call(line,"setLineWidth",new Class[]{int.class},5);}
                catch(NoSuchMethodException ignored){}
            }
            String id="transit-leg-"+i;
            call(map,"addTMapPolyLine",new Class[]{String.class,lineClass},id,line);
            transitLines.add(id);
        }
    }

    void clearRoute() throws Exception {
        for(String id:transitLines)call(map,"removeTMapPolyLine",new Class[]{String.class},id);
        transitLines.clear();
        if(!routeAdded)return;
        call(map,"removeTMapPolyLine",new Class[]{String.class},"commute-route");
        routeAdded=false;
    }

    void onResume(){optionalLifecycle("onResume");}
    void onPause(){optionalLifecycle("onPause");}
    void onDestroy(){
        optionalLifecycle("destroy");
    }
    private void optionalLifecycle(String name){
        try {call(map,name,new Class[0]);}
        catch(Exception ignored){}
    }

    private static Object call(Object target,String name,Class<?>[] types,Object...args) throws Exception {
        Method method=target.getClass().getMethod(name,types);
        return method.invoke(target,args);
    }
}
