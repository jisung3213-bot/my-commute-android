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
import com.example.mycommute.data.Session;
import com.example.mycommute.data.TransitRouteMemory;
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
            RoutineStore.save(this,routine);
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
                status.setText("TMAP Raster SDK 지도를 로딩하고 있어.");
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
        String provider=tmapMap!=null?"TMAP SDK": "OSM 임시 지도";
        String action="";
        if(tmapMap==null) {
            action=!TmapRasterMapAdapter.isAvailable()
                ?" · TMAP Raster SDK JAR/AAR를 app/libs/에 설치해야 해"
                :(!TmapWalkingClient.enabled()
                    ?" · local.properties의 TMAP_APP_KEY를 설정해야 해"
                    :" · TMAP SDK 초기화 실패: Logcat의 CommuteMap 확인");
        }
        status.setText(provider+action+"\n"+message);
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
            showStatus("루틴 미등록: 커스텀 화면에서 설정하면 경로가 표시돼.");return;
        }
        if(!routine.validCoordinates()) {
            showStatus("출발지·도착지의 좌표를 등록하면 마커를 표시해.");return;
        }
        addMarker("start","출발: "+routine.startName,routine.startLat,routine.startLon);
        addMarker("end","도착: "+routine.endName,routine.endLat,routine.endLon);
        for(int i=0;i<routine.waypoints.size();i++) {
            Waypoint w=routine.waypoints.get(i);
            if(w.hasCoordinates())addMarker("via-"+i,w.category+": "+w.name,w.lat,w.lon);
        }
        double latitude=(routine.startLat+routine.endLat)/2;
        double longitude=(routine.startLon+routine.endLon)/2;
        if(tmapMap!=null) {
            try {tmapMap.setCenter(latitude,longitude,12);}
            catch(Exception e){Log.w(TAG,"TMAP center change failed",e);}
        } else {
            osmMap.getController().setCenter(new GeoPoint(latitude,longitude));
            osmMap.getController().setZoom(12.0);
        }
        showStatus("출발·도착·경유지 마커 표시. 도보 경로만 API 연결 시 선으로 표시돼.");
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
        final int generation=++requestGeneration;
        if(!show.isChecked()||!routine.isConfigured()||!routine.validCoordinates())return;
        TransitRoute selected=TransitRouteMemory.selected(Session.accountId(this),routine);
        if(selected!=null){renderTransitRoute(selected);return;}
        if(!"도보".equals(routine.transportMode)) {
            showStatus("대중교통·혼합 경로는 아직 연동하지 않았어. 마커만 표시돼.");return;
        }
        if(!TmapWalkingClient.enabled()) {
            showStatus("TMAP AppKey 미설정: 보행 경로는 표시하지 않아.");return;
        }
        List<Waypoint> stops=new ArrayList<>();
        Waypoint start=new Waypoint();start.name=routine.startName;
        start.lat=routine.startLat;start.lon=routine.startLon;stops.add(start);
        for(Waypoint w:routine.waypoints)if(w.hasCoordinates())stops.add(w);
        Waypoint end=new Waypoint();end.name=routine.endName;
        end.lat=routine.endLat;end.lon=routine.endLon;stops.add(end);
        showStatus("TMAP 보행 경로 조회 중...");
        TmapWalkingClient.fetchWalk(stops,new TmapWalkingClient.Callback() {
            @Override public void success(List<GeoPoint> points) {
                if(isFinishing()||isDestroyed()||generation!=requestGeneration)return;
                if(tmapMap!=null) {
                    try {tmapMap.drawRoute(points);}
                    catch(Exception e) {showStatus("TMAP SDK 경로 선 표시 실패: "+e.getMessage());Log.e(TAG,"TMAP draw route",e);return;}
                } else {
                    if(osmLine!=null)osmMap.getOverlays().remove(osmLine);
                    osmLine=new Polyline();osmLine.setPoints(points);
                    osmLine.getOutlinePaint().setStrokeWidth(9f);
                    osmLine.setColor(android.graphics.Color.rgb(99,87,181));
                    osmLine.setEnabled(show.isChecked());
                    osmMap.getOverlays().add(0,osmLine);osmMap.invalidate();
                }
                showStatus("TMAP 보행 경로 응답 표시. 대중교통 경로는 별도 연동 대상이야.");
            }
            @Override public void error(String reason) {
                if(!isFinishing()&&!isDestroyed()&&generation==requestGeneration)
                    showStatus("TMAP 보행 경로 조회 실패: "+reason);
            }
        });
    }


    private void renderTransitRoute(TransitRoute route) {
        if(tmapMap!=null) {
            try {tmapMap.drawTransit(route.legs);}
            catch(Exception e) {
                Log.e(TAG,"TMAP transit drawing failed",e);
                showStatus("대중교통 경로 표시 실패: "+e.getMessage());return;
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
        showStatus("선택한 대중교통 경로 · "+TransitRoute.minutes(route.totalSeconds)
                +"분 · 환승 "+route.transfers+"회 · 선 표시 "+visible+"/"+route.legs.size()+"구간"
                +" (좌표가 제공되지 않는 구간은 표시하지 않음)");
    }

    @Override protected void onResume() {
        super.onResume();
        Routine pending=TransitRouteMemory.preview(Session.accountId(this));
        routine=pending==null?RoutineStore.read(this):pending;
        if(tmapMap!=null) {
            tmapMap.onResume();
            try {tmapMap.clearMarkers();tmapMap.clearRoute();}
            catch(Exception e){Log.w(TAG,"TMAP overlay clearing failed",e);}
        } else if(osmMap!=null) {
            osmMap.onResume();osmMap.getOverlays().clear();osmLine=null;transitLines.clear();
        }
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
