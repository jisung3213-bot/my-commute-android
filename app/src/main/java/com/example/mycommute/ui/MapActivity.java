package com.example.mycommute.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.seoulmobilitytester.R;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.Waypoint;
import com.example.mycommute.network.TmapWalkingClient;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import java.util.ArrayList;
import java.util.List;

/** OSM dev-map fallback. TMAP SDK is intentionally not impersonated without its binary/license. */
public class MapActivity extends AppCompatActivity {
    private MapView map;
    private TextView status;
    private Switch show;
    private Routine routine;
    private Polyline routeLine;
    private int requestGeneration;
    private boolean refreshing;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(!Session.signedIn(this)) {startActivity(new Intent(this,LoginActivity.class));finish();return;}
        setContentView(R.layout.activity_map);Navigation.bind(this);
        map=findViewById(R.id.mapView);status=findViewById(R.id.mapStatus);show=findViewById(R.id.switchRouteVisible);
        map.setTileSource(TileSourceFactory.MAPNIK);map.setMultiTouchControls(true);
        map.getController().setZoom(11.0);
        map.getController().setCenter(new GeoPoint(36.5,127.5));
        routine=RoutineStore.read(this);show.setChecked(routine.mapVisible);
        show.setOnCheckedChangeListener((button,checked)-> {
            if(refreshing)return;
            routine.mapVisible=checked;RoutineStore.save(this,routine);
            if(routeLine!=null)routeLine.setEnabled(checked);
            if(checked&&routeLine==null)loadRoute();map.invalidate();
        });

    }
    private void addMarker(String title,double lat,double lon) {
        Marker m=new Marker(map);m.setPosition(new GeoPoint(lat,lon));m.setTitle(title);
        m.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);map.getOverlays().add(m);
    }
    private void renderStops() {
        if(!routine.isConfigured()) {status.setText("루틴 미등록: 출퇴근 커스텀 화면에서 설정해줘.");return;}
        if(!routine.validCoordinates()) {status.setText("지도 마커를 보려면 출발지·도착지 좌표를 등록해줘.");return;}
        addMarker("출발: "+routine.startName,routine.startLat,routine.startLon);
        addMarker("도착: "+routine.endName,routine.endLat,routine.endLon);
        for(Waypoint w:routine.waypoints) if(w.hasCoordinates()) addMarker(w.category+": "+w.name,w.lat,w.lon);
        map.getController().setCenter(new GeoPoint((routine.startLat+routine.endLat)/2,
                (routine.startLon+routine.endLon)/2));
        map.getController().setZoom(12.0);
        status.setText("출발·도착·경유지 마커 표시 · 경로 선은 실제 보행 결과일 때만 나타나.");
    }
    private void loadRoute() {
        final int generation=++requestGeneration;
        if(!show.isChecked()||!routine.validCoordinates())return;
        if(!routine.transportMode.equals("도보")) {
            status.setText("출발·경유지 마커만 표시 중. 대중교통/혼합 경로는 연동되지 않아 임의의 길을 그리지 않아.");return;
        }
        if(!TmapWalkingClient.enabled()) {status.setText("TMAP 키 미설정: 보행 경로 선은 표시하지 않아 (마커만 제공).");return;}
        List<Waypoint> stops=new ArrayList<>();
        Waypoint start=new Waypoint();start.name=routine.startName;start.lat=routine.startLat;start.lon=routine.startLon;stops.add(start);
        for(Waypoint w:routine.waypoints)if(w.hasCoordinates())stops.add(w);
        Waypoint end=new Waypoint();end.name=routine.endName;end.lat=routine.endLat;end.lon=routine.endLon;stops.add(end);
        status.setText("TMAP 보행 경로 조회 중...");
        TmapWalkingClient.fetchWalk(stops,new TmapWalkingClient.Callback() {
            @Override public void success(List<GeoPoint> points) {
                if(isFinishing()||isDestroyed()||generation!=requestGeneration)return;
                if(routeLine!=null)map.getOverlays().remove(routeLine);
                routeLine=new Polyline();routeLine.setPoints(points);routeLine.getOutlinePaint().setStrokeWidth(9f);
                routeLine.setColor(android.graphics.Color.rgb(99,87,181));routeLine.setEnabled(show.isChecked());
                map.getOverlays().add(0,routeLine);map.invalidate();status.setText("TMAP 보행 경로 응답 표시 (대중교통 정보는 별도)");
            }
            @Override public void error(String reason) { if(!isFinishing()&&!isDestroyed()&&generation==requestGeneration)status.setText("TMAP 경로 조회 실패: "+reason); }
        });
    }
    @Override protected void onResume() {super.onResume();if(map!=null){map.onResume();routine=RoutineStore.read(this);map.getOverlays().clear();routeLine=null;
            renderStops();refreshing=true;show.setChecked(routine.mapVisible);refreshing=false;loadRoute();}}
    @Override protected void onPause() {if(map!=null)map.onPause();super.onPause();}
    @Override protected void onDestroy() {if(map!=null)map.onDetach();super.onDestroy();}
}
