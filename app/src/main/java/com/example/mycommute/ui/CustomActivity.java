package com.example.mycommute.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.example.mycommute.R;
import com.example.mycommute.alarms.ReminderScheduler;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.data.TransitRouteCache;
import com.example.mycommute.data.TransitRouteMemory;
import com.example.mycommute.engine.RouteSignature;
import com.example.mycommute.engine.WeatherRouteRanker;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.model.Waypoint;
import com.example.mycommute.network.KmaWeatherClient;
import com.example.mycommute.network.TmapTransitClient;
import com.example.mycommute.network.TmapWaypointRouteClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CustomActivity extends AppCompatActivity {
    private static final int START=101,END=102,VIA=103;
    private static final int[] DAYS={R.id.dayMon,R.id.dayTue,R.id.dayWed,R.id.dayThu,
            R.id.dayFri,R.id.daySat,R.id.daySun};
    private static final int[] CAL_DAYS={2,3,4,5,6,7,1};
    private String direction;
    private Routine draft;
    private List<TransitRoute> routes=Collections.emptyList();
    private final List<Integer> order=new ArrayList<>();
    private int selected=-1;
    private boolean searching,restoring,weatherKnown;
    private int requestGeneration,weatherGeneration;
    private WeatherRouteRanker.Conditions weather=new WeatherRouteRanker.Conditions(false,false,false);
    private TextView weatherStatus,resultStatus;
    private LinearLayout cards,savedList;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(!Session.signedIn(this)){
            startActivity(new Intent(this,LoginActivity.class));finish();return;
        }
        setContentView(R.layout.activity_custom);
        Navigation.bind(this);
        weatherStatus=findViewById(R.id.textWeatherStatus);
        resultStatus=findViewById(R.id.textTransitResult);
        cards=findViewById(R.id.transitCards);
        savedList=findViewById(R.id.savedRoutineList);
        direction=RoutineStore.activeDirection(this);
        draft=RoutineStore.read(this);
        if(!draft.isConfigured())draft=RoutineStore.newDraft(direction);
        restoreDraft();
        findViewById(R.id.btnDirectionOut).setOnClickListener(v->changeDirection(RoutineStore.OUTBOUND));
        findViewById(R.id.btnDirectionBack).setOnClickListener(v->changeDirection(RoutineStore.RETURN));
        findViewById(R.id.btnNewRoutine).setOnClickListener(v->{
            if(isTracking()) {toast("이동 추적 중에는 변경할 수 없어");return;}
            draft=RoutineStore.newDraft(direction);
            invalidateRoutes();restoreDraft();
        });
        findViewById(R.id.btnMakeDefault).setOnClickListener(v->{
            if(isTracking()){toast("이동 추적 중에는 변경할 수 없어");return;}
            if(!isSaved(draft.id)){toast("먼저 경로 설정을 저장해");return;}
            RoutineStore.setDefault(this,draft.id);
            renderSaved();
            toast("기본 "+(direction.equals(RoutineStore.OUTBOUND)?"출근길":"퇴근길")+"로 지정했어");
        });
        findViewById(R.id.btnStartPlace).setOnClickListener(v->startPlace(START));
        findViewById(R.id.btnEndPlace).setOnClickListener(v->startPlace(END));
        findViewById(R.id.btnAddWaypoint).setOnClickListener(v->startPlace(VIA));
        findViewById(R.id.btnClearWaypoints).setOnClickListener(v->{
            draft.waypoints.clear();invalidateRoutes();renderStops();
        });
        findViewById(R.id.btnSearchTransit).setOnClickListener(v->searchRoutes());
        findViewById(R.id.btnSaveRoutine).setOnClickListener(v->saveDraft(false));
        findViewById(R.id.btnPreviewMap).setOnClickListener(v->showMap());
        ((SwitchMaterial)findViewById(R.id.switchWeather)).setOnCheckedChangeListener((button,checked)->{
            if(restoring)return;
            draft.weatherEnabled=checked;
            weatherKnown=false;weather=new WeatherRouteRanker.Conditions(false,false,false);
            weatherGeneration++;
            weatherStatus.setText(checked?"날씨 반영 선택됨 · 검색 시 기상정보를 확인해":
                    "날씨 미반영 · 이동시간 우선");
            if(checked&&!routes.isEmpty())refreshWeather();else renderCandidates();
        });
    }

    private boolean isTracking(){
        return RoutineStore.prefs(this).getBoolean("tracking_active",false);
    }
    private boolean isSaved(String id){
        for(Routine r:RoutineStore.list(this,direction))if(id.equals(r.id))return true;
        return false;
    }
    private void changeDirection(String next){
        if(isTracking()){toast("이동 추적 중에는 변경할 수 없어");return;}
        if(next.equals(direction))return;
        RoutineStore.selectDirection(this,next);
        direction=next;
        draft=RoutineStore.read(this);
        if(!draft.isConfigured())draft=RoutineStore.newDraft(direction);
        invalidateRoutes();restoreDraft();
    }

    private void loadProfile(String id){
        if(isTracking()){toast("이동 추적 중에는 변경할 수 없어");return;}
        RoutineStore.select(this,id);
        direction=RoutineStore.directionOf(id);
        draft=RoutineStore.read(this,id);
        invalidateRoutes();restoreDraft();
    }

    private void restoreDraft(){
        restoring=true;
        ((TextView)findViewById(R.id.textActiveRoutine)).setText(
                "현재 경로 · "+(direction.equals(RoutineStore.OUTBOUND)?"출근":"퇴근"));
        ((MaterialButton)findViewById(R.id.btnDirectionOut)).setEnabled(!direction.equals(RoutineStore.OUTBOUND));
        ((MaterialButton)findViewById(R.id.btnDirectionBack)).setEnabled(!direction.equals(RoutineStore.RETURN));
        ((EditText)findViewById(R.id.editRouteName)).setText(draft.name);
        ((EditText)findViewById(R.id.editDeparture)).setText(
                draft.arrivalTime.isEmpty()?"09:00":draft.arrivalTime);
        ((EditText)findViewById(R.id.editMargin)).setText(String.valueOf(draft.marginMinutes));
        for(int i=0;i<DAYS.length;i++)
            ((CheckBox)findViewById(DAYS[i])).setChecked(draft.runsOn(CAL_DAYS[i]));
        ((SwitchMaterial)findViewById(R.id.switchWeather)).setChecked(draft.weatherEnabled);
        restoring=false;
        renderStops();renderSaved();
        showCached();
    }

    private void renderSaved(){
        savedList.removeAllViews();
        List<Routine> profiles=RoutineStore.list(this,direction);
        if(profiles.isEmpty()){
            TextView text=new TextView(this);
            text.setText("저장된 경로가 없어. 이름과 장소를 입력해 새 경로를 만들어");
            text.setPadding(12,12,12,12);
            savedList.addView(text);
        }
        String defaultId=RoutineStore.defaultId(this,direction);
        for(Routine profile:profiles){
            boolean current=profile.id.equals(draft.id);
            MaterialButton button=new MaterialButton(this);
            button.setAllCaps(false);
            button.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            button.setText((profile.id.equals(defaultId)?"★ 기본 · ":"")
                    +(current?"[선택] ":"")+profile.name+"\n"+
                    profile.startName+" → "+profile.endName);
            button.setTextSize(13);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin=7;savedList.addView(button,lp);
            button.setOnClickListener(v->loadProfile(profile.id));
        }
    }

    private void renderStops(){
        ((MaterialButton)findViewById(R.id.btnStartPlace)).setText(
                draft.startName.isEmpty()?"출발지 검색":"출발 · "+draft.startName);
        ((MaterialButton)findViewById(R.id.btnEndPlace)).setText(
                draft.endName.isEmpty()?"도착지 검색":"도착 · "+draft.endName);
        StringBuilder text=new StringBuilder();
        for(Waypoint w:draft.waypoints)if(w.hasCoordinates()){
            if(text.length()>0)text.append(" → ");
            text.append(w.name);
        }
        ((TextView)findViewById(R.id.textWaypoints)).setText(
                text.length()==0?"경유지 없음":"경유 순서: "+text);
    }

    private void startPlace(int request){
        if(isTracking()){toast("이동 추적 중에는 경로를 변경할 수 없어");return;}
        startActivityForResult(new Intent(this,PlaceSearchActivity.class),request);
    }

    @Override protected void onActivityResult(int request,int code,Intent data){
        super.onActivityResult(request,code,data);
        if(code!=Activity.RESULT_OK||data==null)return;
        String name=data.getStringExtra(PlaceSearchActivity.EXTRA_NAME);
        double lat=data.getDoubleExtra(PlaceSearchActivity.EXTRA_LAT,0);
        double lon=data.getDoubleExtra(PlaceSearchActivity.EXTRA_LON,0);
        if(name==null||!Routine.valid(lat,lon))return;
        if(request==START){draft.startName=name;draft.startLat=lat;draft.startLon=lon;}
        else if(request==END){draft.endName=name;draft.endLat=lat;draft.endLon=lon;}
        else if(request==VIA){
            if(draft.waypoints.size()>=2){toast("대중교통 경유지는 최대 2개야");return;}
            Waypoint w=new Waypoint();w.name=name;w.category="경유";w.lat=lat;w.lon=lon;
            draft.waypoints.add(w);
        }else return;
        invalidateRoutes();renderStops();
    }

    private boolean readInputs(){
        String name=((EditText)findViewById(R.id.editRouteName)).getText().toString().trim();
        if(name.isEmpty()){toast("경로 이름을 입력해");return false;}
        String time=((EditText)findViewById(R.id.editDeparture)).getText().toString().trim();
        if(!time.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")){
            toast("목표 도착시각은 HH:mm 형식으로 입력해");return false;
        }
        int margin;
        try{margin=Integer.parseInt(((EditText)findViewById(R.id.editMargin)).getText().toString().trim());}
        catch(Exception e){toast("도착 여유시간은 숫자로 입력해");return false;}
        if(margin<0||margin>180){toast("여유시간은 0~180분으로 설정해");return false;}
        StringBuilder days=new StringBuilder();
        for(int i=0;i<DAYS.length;i++)if(((CheckBox)findViewById(DAYS[i])).isChecked()){
            if(days.length()>0)days.append(",");
            days.append(CAL_DAYS[i]);
        }
        if(days.length()==0){toast("반복 요일을 선택해");return false;}
        if(!draft.validCoordinates()||draft.startName.isEmpty()||draft.endName.isEmpty()){
            toast("출발지와 도착지를 선택해");return false;
        }
        draft.name=name;draft.arrivalTime=time;draft.marginMinutes=margin;
        draft.days=days.toString();
        draft.weatherEnabled=((SwitchMaterial)findViewById(R.id.switchWeather)).isChecked();
        draft.apiBased=true;draft.mapVisible=true;draft.transportMode="대중교통";
        return true;
    }

    private void invalidateRoutes(){
        ++requestGeneration;++weatherGeneration;
        searching=false;
        findViewById(R.id.btnSearchTransit).setEnabled(true);
        routes=Collections.emptyList();selected=-1;order.clear();
        cards.removeAllViews();
        resultStatus.setText("저장된 경로가 있으면 불러오고, 신규 경로는 검색 버튼으로 조회해");
        findViewById(R.id.btnPreviewMap).setEnabled(false);
        TransitRouteMemory.clear();
        weatherKnown=false;
        weather=new WeatherRouteRanker.Conditions(false,false,false);
    }

    private void showCached(){
        boolean expired=TransitRouteCache.hasExpired(this,draft.id);
        TransitRouteCache.Snapshot snap=TransitRouteCache.load(this,draft);
        if(snap==null){
            if(expired)
                resultStatus.setText("저장된 경로 형상의 23시간 보관 기간이 지났어. 새 경로 검색이 필요해");
            else resultStatus.setText("루틴 설정은 저장돼 있어. 경로 형상은 새 경로 검색을 눌러 조회해");
            return;
        }
        routes=snap.routes;selected=snap.selected;
        resultStatus.setText("저장된 경로 미리보기 · API 호출 없음 · 23시간 내 임시 데이터");
        renderCandidates();
    }

    private void searchRoutes(){
        if(searching)return;
        if(!readInputs())return;
        if(!TmapTransitClient.enabled()){
            resultStatus.setText("TMAP 대중교통 AppKey가 필요해");return;
        }
        invalidateRoutes();
        searching=true;
        findViewById(R.id.btnSearchTransit).setEnabled(false);
        resultStatus.setText("TMAP 구간별 경로 검색 중 · 경유지에 따라 API 호출 횟수가 증가해");
        final int token=++requestGeneration;
        TmapWaypointRouteClient.search(draft,new TmapWaypointRouteClient.Callback(){
            @Override public void success(List<TransitRoute> candidates,int calls){
                if(token!=requestGeneration||isFinishing()||isDestroyed())return;
                searching=false;findViewById(R.id.btnSearchTransit).setEnabled(true);
                routes=candidates;
                resultStatus.setText(calls+"회 API 조회 · 후보 "+routes.size()
                        +"개 · 원하는 후보를 누르면 이름 있는 루틴과 23시간 캐시로 저장해");
                renderCandidates();
                if(draft.weatherEnabled)refreshWeather();
            }
            @Override public void error(String why){
                if(token!=requestGeneration||isFinishing()||isDestroyed())return;
                searching=false;findViewById(R.id.btnSearchTransit).setEnabled(true);
                resultStatus.setText("경로 조회 실패: "+why);
            }
        });
    }

    private void refreshWeather(){
        int generation=++weatherGeneration;
        weatherKnown=false;
        if(!KmaWeatherClient.enabled()){
            weatherStatus.setText("기상청 API 키 미설정 · 이동시간 기준");
            return;
        }
        KmaWeatherClient.forecast(draft,new KmaWeatherClient.Callback(){
            @Override public void success(KmaWeatherClient.Report report){
                if(generation!=weatherGeneration||isFinishing()||isDestroyed())return;
                weather=report.conditions;weatherKnown=true;
                weatherStatus.setText(report.label+" · 후보 정렬에 반영");
                renderCandidates();
            }
            @Override public void error(String why){
                if(generation!=weatherGeneration||isFinishing()||isDestroyed())return;
                weatherStatus.setText("날씨 확인 실패 · 이동시간 기준: "+why);
            }
        });
    }

    private void renderCandidates(){
        cards.removeAllViews();order.clear();
        if(routes.isEmpty())return;
        for(int i=0;i<routes.size();i++)order.add(i);
        if(draft.weatherEnabled&&weatherKnown)order.sort((a,b)->
                Double.compare(score(routes.get(a)),score(routes.get(b))));
        else order.sort(Comparator.comparingInt(i->routes.get(i).totalSeconds));
        for(int index:order){
            TransitRoute route=routes.get(index);
            MaterialButton button=new MaterialButton(this);
            button.setAllCaps(false);
            button.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            button.setTextSize(13);
            button.setText((selected==index?"[선택된 경로] ":"")+
                    route.summary(index+1)+"\n"+route.stepsSummary());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin=8;cards.addView(button,lp);
            button.setOnClickListener(v->{
                selected=index;
                if(saveDraft(true)) {
                    boolean cached=TransitRouteCache.save(this,draft,routes,selected);
                    TransitRouteMemory.put(Session.accountId(this),draft,routes);
                    TransitRouteMemory.choose(Session.accountId(this),draft,selected);
                    resultStatus.setText(cached
                            ?"선택한 경로를 저장했어 · 23시간 이내 다시 API 호출 없이 확인 가능"
                            :"루틴 설정은 저장했지만 상세 경로 캐시 저장 실패 · 현재 세션에서만 지도 확인 가능");
                }
                renderCandidates();
            });
        }
        findViewById(R.id.btnPreviewMap).setEnabled(selected>=0&&selected<routes.size());
    }

    private double score(TransitRoute r){
        int duration=TransitRoute.minutes(r.totalSeconds);
        int walk=Math.min(TransitRoute.minutes(r.walkSeconds),Math.max(1,duration));
        return WeatherRouteRanker.score(
                new WeatherRouteRanker.Candidate("route","대중교통",Math.max(1,duration),
                        walk,0,r.transfers,false),weather);
    }

    /** Save human-authored settings independently from any paid API request. */
    private boolean saveDraft(boolean withSelectedRoute){
        if(isTracking()){toast("이동 추적 중에는 저장할 수 없어");return false;}
        if(!readInputs())return false;
        Routine previous=RoutineStore.read(this,draft.id);
        if(withSelectedRoute&&selected>=0&&selected<routes.size()){
            draft.selectedRouteSignature=RouteSignature.of(routes.get(selected));
            draft.preferredMode="대중교통";
        }else if(!previous.selectedRouteSignature.isEmpty()&&
                !samePlaces(previous,draft)){
            draft.selectedRouteSignature="";
            TransitRouteCache.clear(this,draft.id);
        }
        draft.estimatedMinutes=0;draft.normalWalkMinutes=0;
        ReminderScheduler.cancel(this,previous);
        RoutineStore.save(this,draft);
        renderSaved();
        toast(withSelectedRoute?"이름 붙인 경로와 검색 결과를 저장했어":"경로 설정 저장 완료");
        requestNotificationPermission();
        return true;
    }

    private boolean samePlaces(Routine a,Routine b){
        if(a.startLat!=b.startLat||a.startLon!=b.startLon||
                a.endLat!=b.endLat||a.endLon!=b.endLon||
                a.waypoints.size()!=b.waypoints.size())return false;
        for(int i=0;i<a.waypoints.size();i++)
            if(a.waypoints.get(i).lat!=b.waypoints.get(i).lat||
                    a.waypoints.get(i).lon!=b.waypoints.get(i).lon)return false;
        return true;
    }

    private void showMap(){
        if(selected<0||selected>=routes.size())return;
        if(!readInputs())return;
        TransitRouteMemory.put(Session.accountId(this),draft,routes);
        TransitRouteMemory.choose(Session.accountId(this),draft,selected);
        startActivity(new Intent(this,MapActivity.class));
    }

    private void requestNotificationPermission(){
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(
                this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},302);
    }
    @Override protected void onDestroy(){
        requestGeneration++;weatherGeneration++;super.onDestroy();
    }
    private void toast(String text){
        android.widget.Toast.makeText(this,text,android.widget.Toast.LENGTH_SHORT).show();
    }
}
