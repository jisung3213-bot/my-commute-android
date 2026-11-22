package com.example.mycommute.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mycommute.BuildConfig;
import com.example.mycommute.R;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.PinnedRouteRefresh;
import com.example.mycommute.data.Session;
import com.example.mycommute.data.TransitRouteMemory;
import com.example.mycommute.data.TransitRouteCache;
import com.example.mycommute.location.TrackingService;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.Waypoint;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.network.TmapWalkingClient;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import java.util.ArrayList;
import java.util.List;

/** Prefer the locally installed TMAP Raster SDK; retain OSM only as an explicit fallback. */
public class MapActivity extends AppCompatActivity {
    private static final String TAG="CommuteMap";
    private FrameLayout container;
    private MapView osmMap;
    private TmapRasterMapAdapter tmapMap;
    private TextView status;
    private Switch show;
    private Routine routine;
    private Polyline osmLine;
    private final List<Polyline> transitLines=new ArrayList<>();
    private int requestGeneration;
    private boolean refreshing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(!Session.signedIn(this)) {
            startActivity(new Intent(this,LoginActivity.class));finish();return;
        }
        setContentView(R.layout.activity_map);
        Navigation.bind(this);
        container=findViewById(R.id.mapContainer);
        status=findViewById(R.id.mapStatus);
        show=findViewById(R.id.switchRouteVisible);
        initializeMap();
        routine=RoutineStore.read(this);
        show.setChecked(routine.mapVisible);
        show.setOnCheckedChangeListener((button,checked)-> {
            if(refreshing)return;
            routine.mapVisible=checked;
            RoutineStore.prefs(this).edit()
                    .putBoolean("map_visible_"+routine.id,checked).apply();
            if(!checked)clearRoute();
            else loadRoute();
        });
    }

    private void initializeMap() {
        if(TmapRasterMapAdapter.isAvailable() && TmapWalkingClient.enabled()) {
            try {
                tmapMap=new TmapRasterMapAdapter(this,BuildConfig.TMAP_APP_KEY);
                container.addView(tmapMap.view(),new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
                UiNotice.show(status,UiNotice.State.LOADING,"지도 준비 중…");
                return;
            } catch(Throwable error) {
                Log.e(TAG,"TMAP map initialization failed",error);
                container.removeAllViews();
                tmapMap=null;
            }
        }
        osmMap=new MapView(this);
        osmMap.setTileSource(TileSourceFactory.MAPNIK);
        osmMap.setMultiTouchControls(true);
        osmMap.getController().setZoom(11.0);
        osmMap.getController().setCenter(new GeoPoint(37.5664,126.9778));
        container.addView(osmMap,new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showStatus(String message) {
        UiNotice.show(status,UiNotice.State.UNAVAILABLE,message);
    }

    private void addMarker(String id,String name,double lat,double lon) {
        if(tmapMap!=null) {
            try {tmapMap.addMarker(id,name,lat,lon);}
            catch(Exception e){Log.w(TAG,"TMAP marker unavailable",e);}
        } else {
            Marker marker=new Marker(osmMap);
            marker.setPosition(new GeoPoint(lat,lon));marker.setTitle(name);
            marker.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);
            osmMap.getOverlays().add(marker);
        }
    }

    private void renderStops() {
        if(!routine.isConfigured()) {
            showStatus("등록된 경로 없음");return;
        }
        if(!routine.validCoordinates()) {
            showStatus("출발지·도착지 미설정");return;
        }
        addMarker("start","출발: "+routine.startName,routine.startLat,routine.startLon);
        addMarker("end","도착: "+routine.endName,routine.endLat,routine.endLon);
        for(int i=0;i<routine.waypoints.size();i++) {
            Waypoint w=routine.waypoints.get(i);
            if(w.hasCoordinates())addMarker("via-"+i,w.category+": "+w.name,w.lat,w.lon);
        }
        double minLat=Math.min(routine.startLat,routine.endLat);
        double maxLat=Math.max(routine.startLat,routine.endLat);
        double minLon=Math.min(routine.startLon,routine.endLon);
        double maxLon=Math.max(routine.startLon,routine.endLon);
        for(Waypoint w:routine.waypoints)if(w.hasCoordinates()){
            minLat=Math.min(minLat,w.lat);maxLat=Math.max(maxLat,w.lat);
            minLon=Math.min(minLon,w.lon);maxLon=Math.max(maxLon,w.lon);
        }
        double latitude=(minLat+maxLat)/2,longitude=(minLon+maxLon)/2;
        double span=Math.max(maxLat-minLat,(maxLon-minLon)*0.8);
        int zoom=span>0.5?9:span>0.2?10:span>0.10?11:span>0.05?12:span>0.025?13:14;
        if(tmapMap!=null) {
            try {tmapMap.setCenter(latitude,longitude,zoom);}
            catch(Exception e){Log.w(TAG,"TMAP center change failed",e);}
        } else {
            osmMap.getController().setCenter(new GeoPoint(latitude,longitude));
            osmMap.getController().setZoom(zoom);
        }
        UiNotice.show(status,UiNotice.State.IDLE,"");
    }

    private void clearRoute() {
        ++requestGeneration;
        if(tmapMap!=null) {
            try {tmapMap.clearRoute();}
            catch(Exception e){Log.w(TAG,"TMAP line removal failed",e);}
        } else if(osmMap!=null) {
            for(Polyline line:transitLines)osmMap.getOverlays().remove(line);
            transitLines.clear();
            if(osmLine!=null)osmMap.getOverlays().remove(osmLine);
            osmLine=null;osmMap.invalidate();
        }
    }

    private void loadRoute() {
        int generation=++requestGeneration;
        if(!show.isChecked()||!routine.isConfigured()||!routine.validCoordinates())return;
        TransitRoute selected=TransitRouteMemory.selected(Session.accountId(this),routine);
        if(selected==null) {
            TransitRouteCache.Snapshot snapshot=TransitRouteCache.load(this,routine);
            if(snapshot!=null)selected=snapshot.routes.get(snapshot.selected);
        }
        if(selected!=null){
            renderTransitRoute(selected);
        }else if(PinnedRouteRefresh.request(this,routine,System.currentTimeMillis(),ok->{
            if(generation!=requestGeneration||isFinishing()||isDestroyed())return;
            if(ok)loadRoute();
            else showStatus("상세 경로 확인 불가");
        })){
            UiNotice.show(status,UiNotice.State.LOADING,"상세 경로 갱신 중…");
        }else{
            showStatus(PinnedRouteRefresh.canRefresh(routine)?"상세 경로 확인 불가":"루틴에서 상세 경로 선택");
        }
    }

    private void renderTransitRoute(TransitRoute route) {
        if(tmapMap!=null) {
            try {tmapMap.drawTransit(route.legs);}
            catch(Exception e) {
                Log.e(TAG,"TMAP transit drawing failed",e);
                showStatus("경로 표시 불가");return;
            }
        } else if(osmMap!=null) {
            for(Polyline line:transitLines)osmMap.getOverlays().remove(line);
            transitLines.clear();
            for(TransitRoute.Leg leg:route.legs) {
                if(leg.points.size()<2)continue;
                Polyline line=new Polyline();
                List<GeoPoint> positions=new ArrayList<>();
                for(TransitRoute.Point point:leg.points)
                    positions.add(new GeoPoint(point.lat,point.lon));
                line.setPoints(positions);
                line.setColor("BUS".equals(leg.mode)?android.graphics.Color.rgb(43,111,214):
                        "SUBWAY".equals(leg.mode)?android.graphics.Color.rgb(214,62,81):
                        android.graphics.Color.rgb(102,102,102));
                line.getOutlinePaint().setStrokeWidth(8f);
                osmMap.getOverlays().add(line);
                transitLines.add(line);
            }
            osmMap.invalidate();
        }
        int visible=0;
        for(TransitRoute.Leg leg:route.legs)if(leg.points.size()>=2)visible++;
        if(visible==0)showStatus("경로 선 미제공");
        else UiNotice.show(status,UiNotice.State.IDLE,"");
    }

    @Override protected void onResume() {
        super.onResume();
        Routine pending=TransitRouteMemory.preview(Session.accountId(this));
        String requested=getIntent().getStringExtra("routineId");
        routine=requested!=null?RoutineStore.read(this,requested)
                :pending!=null?pending:RoutineStore.read(this);
        if(tmapMap!=null) {
            tmapMap.onResume();
            try {tmapMap.clearMarkers();tmapMap.clearRoute();}
            catch(Exception e){Log.w(TAG,"TMAP overlay clearing failed",e);}
        } else if(osmMap!=null) {
            osmMap.onResume();osmMap.getOverlays().clear();osmLine=null;transitLines.clear();
        }
        routine.mapVisible=RoutineStore.prefs(this).getBoolean(
                "map_visible_"+routine.id,routine.mapVisible);
        if(show!=null){refreshing=true;show.setChecked(routine.mapVisible);refreshing=false;}
        renderStops();loadRoute();
    }

    @Override protected void onPause() {
        ++requestGeneration;
        if(tmapMap!=null)tmapMap.onPause();
        if(osmMap!=null)osmMap.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if(tmapMap!=null)tmapMap.onDestroy();
        if(osmMap!=null)osmMap.onDetach();
        super.onDestroy();
    }
}
