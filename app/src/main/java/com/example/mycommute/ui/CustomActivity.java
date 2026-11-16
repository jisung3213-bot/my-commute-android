package com.example.mycommute.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.example.mycommute.R;
import com.example.mycommute.alarms.ReminderScheduler;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.data.TransitRouteMemory;
import com.example.mycommute.engine.WeatherRouteRanker;
import com.example.mycommute.model.Place;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.model.Waypoint;
import com.example.mycommute.network.KmaWeatherClient;
import com.example.mycommute.network.TmapTransitClient;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CustomActivity extends AppCompatActivity {
    private static final int START=101,END=102,VIA=103;
    private static final int[] DAY_IDS={R.id.dayMon,R.id.dayTue,R.id.dayWed,R.id.dayThu,
            R.id.dayFri,R.id.daySat,R.id.daySun};
    private static final int[] CALENDAR_DAYS={2,3,4,5,6,7,1};
    private Routine draft;
    private List<TransitRoute> routes=Collections.emptyList();
    private List<Integer> order=new ArrayList<>();
    private int selected=-1;
    private boolean searching;
    private int requestGeneration,weatherGeneration;
    private boolean weatherKnown;
    private WeatherRouteRanker.Conditions conditions=new WeatherRouteRanker.Conditions(false,false,false);
    private TextView weatherStatus,resultStatus;
    private LinearLayout cards;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(!Session.signedIn(this)){startActivity(new Intent(this,LoginActivity.class));finish();return;}
        setContentView(R.layout.activity_custom);
        Navigation.bind(this);
        weatherStatus=findViewById(R.id.textWeatherStatus);
        resultStatus=findViewById(R.id.textTransitResult);
        cards=findViewById(R.id.transitCards);
        draft=RoutineStore.read(this);
        restore(draft);
        findViewById(R.id.btnStartPlace).setOnClickListener(v->startPlaceSearch(START));
        findViewById(R.id.btnEndPlace).setOnClickListener(v->startPlaceSearch(END));
        findViewById(R.id.btnAddWaypoint).setOnClickListener(v->startPlaceSearch(VIA));
        findViewById(R.id.btnClearWaypoints).setOnClickListener(v->{
            draft.waypoints.clear();renderPlaces();invalidateRoutes();
        });
        findViewById(R.id.btnSelectRoutineCustom).setOnClickListener(v->
            new AlertDialog.Builder(this).setTitle("루틴 선택")
                .setItems(new String[]{"출근 / 등교","퇴근 / 하교"},(d,i)-> {
                    if(RoutineStore.prefs(this).getBoolean("tracking_active",false)){
                        toast("이동 중에는 루틴을 변경할 수 없어");return;
                    }
                    RoutineStore.select(this,RoutineStore.IDS[i]);
                    draft=RoutineStore.read(this);restore(draft);invalidateRoutes();
                }).show());
        findViewById(R.id.btnSearchTransit).setOnClickListener(v->searchRoutes());
        findViewById(R.id.btnSaveRoutine).setOnClickListener(v->saveSelected());
        findViewById(R.id.btnPreviewMap).setOnClickListener(v->showMap());
        ((SwitchMaterial)findViewById(R.id.switchWeather)).setOnCheckedChangeListener((button,enabled)->{
            draft.weatherEnabled=enabled;
            if(enabled){
                weatherStatus.setText("기상정보 조회 전 · 현재는 이동시간 기준이야");
                if(!routes.isEmpty())refreshWeather();
            }else{
                ++weatherGeneration;
                weatherKnown=false;
                conditions=new WeatherRouteRanker.Conditions(false,false,false);
                weatherStatus.setText("날씨 미적용 · 이동시간이 짧은 경로 우선");
                rankAndRender();
            }
        });
    }

    private void startPlaceSearch(int request) {
        if(RoutineStore.prefs(this).getBoolean("tracking_active",false)){
            toast("이동 중에는 루틴을 수정할 수 없어");return;
        }
        startActivityForResult(new Intent(this,PlaceSearchActivity.class),request);
    }

    @Override protected void onActivityResult(int request,int resultCode,Intent data) {
        super.onActivityResult(request,resultCode,data);
        if(resultCode!=Activity.RESULT_OK||data==null)return;
        String name=data.getStringExtra(PlaceSearchActivity.EXTRA_NAME);
        double lat=data.getDoubleExtra(PlaceSearchActivity.EXTRA_LAT,0);
        double lon=data.getDoubleExtra(PlaceSearchActivity.EXTRA_LON,0);
        if(name==null||!Routine.valid(lat,lon))return;
        if(request==START){draft.startName=name;draft.startLat=lat;draft.startLon=lon;}
        else if(request==END){draft.endName=name;draft.endLat=lat;draft.endLon=lon;}
        else if(request==VIA) {
            Waypoint w=new Waypoint();w.name=name;w.category="경유";w.lat=lat;w.lon=lon;
            draft.waypoints.add(w);
        }else return;
        renderPlaces();
        invalidateRoutes();
    }

    private void restore(Routine r) {
        ((TextView)findViewById(R.id.textActiveRoutine)).setText(
                "현재: "+("return".equals(r.id)?"퇴근 / 하교":"출근 / 등교"));
        ((EditText)findViewById(R.id.editDeparture)).setText(r.arrivalTime.isEmpty()?"09:00":r.arrivalTime);
        ((EditText)findViewById(R.id.editMargin)).setText(""+r.marginMinutes);
        for(int i=0;i<DAY_IDS.length;i++)
            ((CheckBox)findViewById(DAY_IDS[i])).setChecked(r.runsOn(CALENDAR_DAYS[i]));
        ((SwitchMaterial)findViewById(R.id.switchWeather)).setChecked(r.weatherEnabled);
        renderPlaces();
    }

    private void renderPlaces() {
        ((MaterialButton)findViewById(R.id.btnStartPlace)).setText(
                draft.startName.isEmpty()?"출발지 검색":"출발 · "+draft.startName);
        ((MaterialButton)findViewById(R.id.btnEndPlace)).setText(
                draft.endName.isEmpty()?"도착지 검색":"도착 · "+draft.endName);
        StringBuilder b=new StringBuilder();
        for(Waypoint w:draft.waypoints) {
            if(!w.hasCoordinates())continue;
            if(b.length()>0)b.append(" · ");
            b.append(w.name);
        }
        ((TextView)findViewById(R.id.textWaypoints)).setText(
                b.length()==0?"주변 시설·경유지 없음":"지도 마커: "+b);
    }

    private boolean readConditions() {
        String time=((EditText)findViewById(R.id.editDeparture)).getText().toString().trim();
        if(!time.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")){
            toast("목표 도착 시각을 HH:mm으로 입력해");return false;
        }
        int margin;
        try{margin=Integer.parseInt(((EditText)findViewById(R.id.editMargin)).getText().toString().trim());}
        catch(Exception ex){toast("도착 여유시간은 숫자로 입력해");return false;}
        if(margin<0||margin>180){toast("도착 여유시간은 0~180분으로 설정해");return false;}
        List<String> days=new ArrayList<>();
        for(int i=0;i<DAY_IDS.length;i++)
            if(((CheckBox)findViewById(DAY_IDS[i])).isChecked())days.add(""+CALENDAR_DAYS[i]);
        if(days.isEmpty()){toast("반복 요일을 하나 이상 선택해");return false;}
        draft.arrivalTime=time;draft.marginMinutes=margin;
        draft.days=android.text.TextUtils.join(",",days);
        draft.weatherEnabled=((SwitchMaterial)findViewById(R.id.switchWeather)).isChecked();
        draft.apiBased=true;draft.mapVisible=true;
        draft.name=("return".equals(draft.id)?"퇴근 / 하교 · ":"출근 / 등교 · ")
                +draft.startName+" → "+draft.endName;
        return true;
    }

    private void invalidateRoutes() {
        requestGeneration++;weatherGeneration++;
        routes=Collections.emptyList();order.clear();selected=-1;
        cards.removeAllViews();
        resultStatus.setText("장소를 설정한 뒤 경로를 검색해");
        findViewById(R.id.btnSaveRoutine).setEnabled(false);
        findViewById(R.id.btnPreviewMap).setEnabled(false);
        TransitRouteMemory.clear();
        weatherKnown=false;
        conditions=new WeatherRouteRanker.Conditions(false,false,false);
    }

    private void searchRoutes() {
        if(searching)return;
        if(draft.startName.trim().isEmpty()||draft.endName.trim().isEmpty()||!draft.validCoordinates()){
            toast("출발지와 도착지를 장소 검색에서 선택해");return;
        }
        if(!readConditions())return;
        if(!TmapTransitClient.enabled()){
            resultStatus.setText("local.properties의 TMAP_TRANSIT_APP_KEY를 입력해야 해");return;
        }
        invalidateRoutes();
        searching=true;
        findViewById(R.id.btnSearchTransit).setEnabled(false);
        resultStatus.setText("TMAP 대중교통 경로 조회 중...");
        final int token=++requestGeneration;
        final Routine target=draft;
        TmapTransitClient.search(target,new TmapTransitClient.Callback(){
            private void finish(){searching=false;findViewById(R.id.btnSearchTransit).setEnabled(true);}
            @Override public void success(List<TransitRoute> result) {
                if(isFinishing()||isDestroyed()||token!=requestGeneration)return;
                finish();
                routes=result;
                resultStatus.setText("경로 "+result.size()+"개 검색됨 · 후보를 눌러 선택해");
                if(draft.weatherEnabled)refreshWeather();
                else rankAndRender();
            }
            @Override public void error(String message) {
                if(isFinishing()||isDestroyed()||token!=requestGeneration)return;
                finish();
                resultStatus.setText("경로 검색 실패: "+message);
            }
        });
    }

    private void refreshWeather() {
        final int token=++weatherGeneration;
        weatherKnown=false;
        conditions=new WeatherRouteRanker.Conditions(false,false,false);
        rankAndRender();
        if(!KmaWeatherClient.enabled()){
            weatherStatus.setText("기상정보 API 키 미설정 · 날씨를 가정하지 않고 기본 정렬");
            return;
        }
        if(!readConditions())return;
        weatherStatus.setText("기상청 예보 조회 중 · 조회 전에는 기본 정렬");
        KmaWeatherClient.forecast(draft,new KmaWeatherClient.Callback() {
            @Override public void success(KmaWeatherClient.Report report) {
                if(isFinishing()||isDestroyed()||token!=weatherGeneration||!draft.weatherEnabled)return;
                conditions=report.conditions;weatherKnown=true;
                weatherStatus.setText(report.label+" · 추천 순위에 반영");
                rankAndRender();
            }
            @Override public void error(String reason) {
                if(isFinishing()||isDestroyed()||token!=weatherGeneration||!draft.weatherEnabled)return;
                weatherStatus.setText("기상정보 미확인: "+reason+" · 기본 정렬");
                rankAndRender();
            }
        });
    }

    private void rankAndRender() {
        if(cards==null)return;
        cards.removeAllViews();
        if(routes.isEmpty())return;
        order.clear();
        for(int i=0;i<routes.size();i++)order.add(i);
        if(draft.weatherEnabled&&weatherKnown)order.sort((a,b)->
                Double.compare(score(routes.get(a)),score(routes.get(b))));
        else order.sort(Comparator.comparingInt(i->routes.get(i).totalSeconds));
        for(int index:order){
            TransitRoute option=routes.get(index);
            MaterialButton card=new MaterialButton(this);
            boolean checked=selected==index;
            String title=(index==order.get(0)?"추천 · ":"")+(checked?"[선택됨] ":"")
                    +option.summary(index+1)+"\n"+option.stepsSummary();
            card.setText(title);
            card.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            card.setAllCaps(false);
            card.setTextSize(14);
            card.setPadding(14,14,14,14);
            card.setCornerRadius(14);
            if(!checked)card.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.primary_light)));
            card.setTextColor(getResources().getColor(checked?android.R.color.white:R.color.text_primary));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
            params.bottomMargin=12;
            cards.addView(card,params);
            card.setOnClickListener(v->{selected=index;rankAndRender();});
        }
        if(selected>=0&&selected<routes.size()){
            resultStatus.setText("선택: "+routes.get(selected).summary(selected+1)
                    +" · 지도에서 경로 확인 후 저장할 수 있어");
            findViewById(R.id.btnSaveRoutine).setEnabled(true);
            findViewById(R.id.btnPreviewMap).setEnabled(true);
        }
    }

    private double score(TransitRoute r) {
        int duration=TransitRoute.minutes(r.totalSeconds),walk=TransitRoute.minutes(r.walkSeconds);
        int cappedWalk=Math.min(walk,Math.max(1,duration));
        WeatherRouteRanker.Candidate c=new WeatherRouteRanker.Candidate(
                "transit","대중교통",Math.max(1,duration),cappedWalk,0,r.transfers,false);
        return WeatherRouteRanker.score(c,conditions);
    }

    private boolean preview() {
        if(selected<0||selected>=routes.size())return false;
        if(!readConditions())return false;
        TransitRouteMemory.put(Session.accountId(this),draft,routes);
        return TransitRouteMemory.choose(Session.accountId(this),draft,selected);
    }

    private void showMap() {
        if(!preview())return;
        startActivity(new Intent(this,MapActivity.class));
    }

    private void saveSelected() {
        if(RoutineStore.prefs(this).getBoolean("tracking_active",false)){
            toast("현재 이동을 종료한 뒤 변경해");return;
        }
        if(!preview())return;
        Routine previous=RoutineStore.read(this);
        draft.transportMode="대중교통";
        draft.preferredMode="대중교통"; // Only store preference, not route timing.
        // TMAP itinerary times are NOT persisted as permanent plan or alert time.
        draft.estimatedMinutes=0;
        draft.normalWalkMinutes=0;
        draft.boardingRoute="";draft.boardingStopId="";draft.boardingDirection="";
        ReminderScheduler.cancel(this,previous);
        RoutineStore.save(this,draft);
        toast("출퇴근 루틴 저장 완료 · 경로는 임시 조회 결과이며 알림은 실시간 연동 후 제공돼");
        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)
                !=PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},302);
    }

    private void toast(String text) {android.widget.Toast.makeText(this,text,android.widget.Toast.LENGTH_LONG).show();}
}
