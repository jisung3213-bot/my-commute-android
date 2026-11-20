package com.example.mycommute.debug;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mycommute.alarms.AlertLabMode;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.TransitRouteCache;
import com.example.mycommute.engine.StagePositionMatcher;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.ui.RouteSchematicView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Isolated debug preview: never changes Android system GPS, persistent tracking,
 * local GPS prefs, route caches or production notifications.
 */
public final class RouteGpsLabActivity extends AppCompatActivity {
    private TransitRoute route;
    private RouteSchematicView diagram;
    private Spinner segment;
    private SeekBar progress;
    private TextView diagnostics;
    private int selectedSegment;
    private int percentage=50;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!AlertLabMode.enabled(this)){finish();return;}
        ScrollView scroll=new ScrollView(this);
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(16);
        panel.setPadding(pad,pad,pad,pad);
        scroll.addView(panel);
        setContentView(scroll);
        label(panel,"GPS 경로 마커 테스트 · DEBUG",22);
        label(panel,"앱 내부 위치 모의실험 · 시스템 GPS 및 실제 위치추적에는 영향 없음",13);

        Routine routine=RoutineStore.read(this);
        TransitRouteCache.Snapshot snapshot=TransitRouteCache.load(this,routine);
        if(snapshot==null){
            label(panel,"선택된 경로의 상세 캐시가 없거나 유효기간(23시간)이 지났어. 커스텀 화면에서 경로를 검색·저장한 뒤 다시 열어.",15);
            return;
        }
        route=snapshot.routes.get(snapshot.selected);
        if(route.legs.isEmpty()){label(panel,"경로 구간이 없어",15);return;}

        diagram=new RouteSchematicView(this,null);
        panel.addView(diagram,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        diagram.setRoute(route);

        label(panel,"마커 위치를 확인할 구간",15);
        segment=new Spinner(this);
        List<String> names=new ArrayList<>();
        for(int i=0;i<route.legs.size();i++){
            TransitRoute.Leg leg=route.legs.get(i);
            names.add((i+1)+". "+leg.startName+" → "+leg.endName+" ("+leg.mode+")");
        }
        segment.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        panel.addView(segment);
        segment.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    android.view.View view,int position,long id){
                selectedSegment=position;updatePreview();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        label(panel,"구간 내 이동 진행률",15);
        progress=new SeekBar(this);
        progress.setMax(100);
        progress.setProgress(percentage);
        panel.addView(progress);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                percentage=value;updatePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });

        Button start=new Button(this);
        start.setText("구간 시작");
        start.setOnClickListener(v->progress.setProgress(0));
        panel.addView(start);
        Button middle=new Button(this);
        middle.setText("구간 중간");
        middle.setOnClickListener(v->progress.setProgress(50));
        panel.addView(middle);
        Button end=new Button(this);
        end.setText("구간 끝");
        end.setOnClickListener(v->progress.setProgress(100));
        panel.addView(end);
        Button hide=new Button(this);
        hide.setText("위치 확인 불가 상태 테스트");
        hide.setOnClickListener(v->{
            diagram.setPreviewPosition(-1,0);
            diagnostics.setText("위치 판정 불가 · 마커가 숨겨져야 정상");
        });
        panel.addView(hide);
        diagnostics=label(panel,"",14);
        updatePreview();
    }
    private TextView label(LinearLayout panel,String value,int sp){
        TextView label=new TextView(this);
        label.setText(value);label.setTextSize(sp);
        label.setPadding(0,dp(7),0,dp(9));
        panel.addView(label);
        return label;
    }
    private int dp(int value){
        return (int)(value*getResources().getDisplayMetrics().density+0.5f);
    }
    private void updatePreview(){
        if(route==null||diagram==null||diagnostics==null)return;
        int index=selectedSegment;
        diagram.setPreviewPosition(index,percentage/100f);
        TransitRoute.Leg leg=route.legs.get(index);
        StringBuilder line=new StringBuilder();
        line.append("모의 위치: ").append(index+1).append("번 구간 · ")
                .append(percentage).append("% · 마커 ").append(index<8?"표시":"도식 8구간 제한으로 숨김");
        if(leg.points.size()>=2){
            // Approximate mock coordinate for checking the production stage matcher.
            double total=0;
            for(int i=1;i<leg.points.size();i++)
                total+=distance(leg.points.get(i-1),leg.points.get(i));
            double desired=total*percentage/100.0;
            double cumulative=0;
            TransitRoute.Point a=leg.points.get(0),b=leg.points.get(1);
            double t=0;
            for(int i=1;i<leg.points.size();i++){
                a=leg.points.get(i-1);b=leg.points.get(i);
                double length=distance(a,b);
                if(i==leg.points.size()-1||cumulative+length>=desired){
                    t=length<=0?0:Math.max(0,Math.min(1,(desired-cumulative)/length));
                    break;
                }
                cumulative+=length;
            }
            double lat=a.lat+(b.lat-a.lat)*t,lon=a.lon+(b.lon-a.lon)*t;
            StagePositionMatcher.Match match=StagePositionMatcher.locate(
                    route,lat,lon,System.currentTimeMillis(),10f,System.currentTimeMillis());
            line.append("\n모의 좌표 판정: ")
                    .append(match.known()?(match.legIndex+1)+"번 구간 매칭":"판정 불가")
                    .append(" · ").append(match.description);
            if(match.known()&&match.legIndex!=index)
                line.append("\n주의: 모의 좌표가 다른 구간으로 판정됨");
        }else{
            line.append("\n경로 좌표가 2개 미만이어서 시각적 위치만 확인 가능");
        }
        diagnostics.setText(line.toString());
    }
    private static double distance(TransitRoute.Point a,TransitRoute.Point b){
        double lat=(a.lat+b.lat)*0.5;
        return Math.hypot((a.lat-b.lat)*111195,
                (a.lon-b.lon)*111195*Math.cos(Math.toRadians(lat)));
    }
}
