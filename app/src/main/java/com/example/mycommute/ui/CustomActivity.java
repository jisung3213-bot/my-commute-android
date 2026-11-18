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
        if(!draft.isConfigured())draft=newConfiguredDraft(direction);
        restoreDraft();
        findViewById(R.id.btnDirectionOut).setOnClickListener(v->changeDirection(RoutineStore.OUTBOUND));
        findViewById(R.id.btnDirectionBack).setOnClickListener(v->changeDirection(RoutineStore.RETURN));
        findViewById(R.id.btnNewRoutine).setOnClickListener(v->{
            if(isTracking()) {toast("위치 추적 중 · 경로 변경 불가");return;}
            draft=newConfiguredDraft(direction);
            invalidateRoutes();restoreDraft();
        });
        findViewById(R.id.btnMakeDefault).setOnClickListener(v->{
            if(isTracking()){toast("위치 추적 중 · 경로 변경 불가");return;}
            if(!isSaved(draft.id)){toast("경로 저장 필요");return;}
            RoutineStore.setDefault(this,draft.id);
            ReminderScheduler.scheduleCurrent(this);
            renderSaved();
            toast("기본 "+(direction.equals(RoutineStore.OUTBOUND)?"출근길":"퇴근길")+"로 지정 완료");
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
            UiNotice.show(weatherStatus,UiNotice.State.IDLE,"");
            if(checked&&!routes.isEmpty())refreshWeather();else renderCandidates();
        });
    }

    private Routine newConfiguredDraft(String kind){
        Routine r=RoutineStore.newDraft(kind);
        r.marginMinutes=com.example.mycommute.data.AlarmSettingsStore.defaultMargin(this);
        r.weatherEnabled=com.example.mycommute.data.AlarmSettingsStore.defaultWeather(this);
        return r;
    }

    private boolean isTracking(){
        return RoutineStore.prefs(this).getBoolean("tracking_active",false);
    }
    private boolean isSaved(String id){
        for(Routine r:RoutineStore.list(this,direction))if(id.equals(r.id))return true;
        return false;
    }
    private void changeDirection(String next){
        if(isTracking()){toast("위치 추적 중 · 경로 변경 불가");return;}
        if(next.equals(direction))return;
        RoutineStore.selectDirection(this,next);
        ReminderScheduler.scheduleCurrent(this);
        direction=next;
        draft=RoutineStore.read(this);
        if(!draft.isConfigured())draft=newConfiguredDraft(direction);
        invalidateRoutes();restoreDraft();
    }

    private void loadProfile(String id){
        if(isTracking()){toast("위치 추적 중 · 경로 변경 불가");return;}
        RoutineStore.select(this,id);
        ReminderScheduler.scheduleCurrent(this);
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
            text.setText("저장된 경로 없음");
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
        if(isTracking()){toast("위치 추적 중 · 경로 변경 불가");return;}
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
            if(draft.waypoints.size()>=2){toast("경유지는 최대 2개");return;}
            Waypoint w=new Waypoint();w.name=name;w.category="경유";w.lat=lat;w.lon=lon;
            draft.waypoints.add(w);
        }else return;
        invalidateRoutes();renderStops();
    }

    private boolean readInputs(){
        String name=((EditText)findViewById(R.id.editRouteName)).getText().toString().trim();
        if(name.isEmpty()){toast("경로 이름을 입력하세요");return false;}
        String time=((EditText)findViewById(R.id.editDeparture)).getText().toString().trim();
        if(!time.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")){
            toast("도착시각은 HH:mm 형식");return false;
        }
        int margin;
        try{margin=Integer.parseInt(((EditText)findViewById(R.id.editMargin)).getText().toString().trim());}
        catch(Exception e){toast("여유시간은 숫자로 입력");return false;}
        if(margin<0||margin>180){toast("여유시간은 0~180분");return false;}
        StringBuilder days=new StringBuilder();
        for(int i=0;i<DAYS.length;i++)if(((CheckBox)findViewById(DAYS[i])).isChecked()){
            if(days.length()>0)days.append(",");
            days.append(CAL_DAYS[i]);
        }
        if(days.length()==0){toast("반복 요일 선택 필요");return false;}
        if(!draft.validCoordinates()||draft.startName.isEmpty()||draft.endName.isEmpty()){
            toast("출발지·도착지 선택 필요");return false;
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
        UiNotice.show(resultStatus,UiNotice.State.IDLE,"");
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
                UiNotice.show(resultStatus,UiNotice.State.UNAVAILABLE,"경로 갱신 필요");
            else UiNotice.show(resultStatus,UiNotice.State.IDLE,"");
            return;
        }
        routes=snap.routes;selected=snap.selected;
        UiNotice.show(resultStatus,UiNotice.State.IDLE,"");
        renderCandidates();
    }

    private void searchRoutes(){
        if(searching)return;
        if(!readInputs())return;
        if(!TmapTransitClient.enabled()){
            UiNotice.show(resultStatus,UiNotice.State.UNAVAILABLE,"경로 검색 불가");
            toast("경로 검색 불가");
            return;
        }
        invalidateRoutes();
        searching=true;
        findViewById(R.id.btnSearchTransit).setEnabled(false);
        UiNotice.show(resultStatus,UiNotice.State.LOADING,"경로 검색 중…");
        final int token=++requestGeneration;
        TmapWaypointRouteClient.search(draft,new TmapWaypointRouteClient.Callback(){
            @Override public void success(List<TransitRoute> candidates,int calls){
                if(token!=requestGeneration||isFinishing()||isDestroyed())return;
                searching=false;findViewById(R.id.btnSearchTransit).setEnabled(true);
                routes=candidates;
                UiNotice.show(resultStatus,UiNotice.State.IDLE,"");
                renderCandidates();
                if(draft.weatherEnabled)refreshWeather();
            }
            @Override public void error(String why){
                if(token!=requestGeneration||isFinishing()||isDestroyed())return;
                searching=false;findViewById(R.id.btnSearchTransit).setEnabled(true);
                String message=UiNotice.error(why);
                UiNotice.show(resultStatus,UiNotice.classify(why),message);
                toast(message);
            }
        });
    }

    private void refreshWeather(){
        int generation=++weatherGeneration;
        weatherKnown=false;
        if(!KmaWeatherClient.enabled()){
            UiNotice.show(weatherStatus,UiNotice.State.UNAVAILABLE,"날씨 반영 불가");
            return;
        }
        KmaWeatherClient.forecast(draft,new KmaWeatherClient.Callback(){
            @Override public void success(KmaWeatherClient.Report report){
                if(generation!=weatherGeneration||isFinishing()||isDestroyed())return;
                weather=report.conditions;weatherKnown=true;
                UiNotice.show(weatherStatus,UiNotice.State.IDLE,"");
                renderCandidates();
            }
            @Override public void error(String why){
                if(generation!=weatherGeneration||isFinishing()||isDestroyed())return;
                UiNotice.show(weatherStatus,UiNotice.classify(why),
                        "날씨 반영 불가 · 이동시간순 정렬");
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
                    com.example.mycommute.alarms.ReminderScheduler.scheduleCurrent(this);
                    if(!cached){
                        UiNotice.show(resultStatus,UiNotice.State.ERROR,"경로 상세 저장 불가");
                        toast("기본 설정만 저장됨");
                    }else UiNotice.show(resultStatus,UiNotice.State.IDLE,"");
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
        if(isTracking()){toast("위치 추적 중 · 저장 불가");return false;}
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
        // Persist a user-selected PLAN duration for future offline alarms.
        // It is never presented as a live vehicle ETA.
        if(selected>=0&&selected<routes.size())
            draft.estimatedMinutes=TransitRoute.minutes(routes.get(selected).totalSeconds);
        draft.normalWalkMinutes=0;
        ReminderScheduler.cancel(this,previous);
        RoutineStore.save(this,draft);
        com.example.mycommute.alarms.ReminderScheduler.scheduleCurrent(this);
        renderSaved();
        toast("저장 완료");
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
