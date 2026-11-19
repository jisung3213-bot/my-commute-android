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
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import com.google.android.material.card.MaterialCardView;
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
    private final java.util.Set<Integer> expandedRoutes=new java.util.HashSet<>();
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
        updateDirectionTabs();
        ((TextView)findViewById(R.id.textActiveRoutine)).setText(
                "선택된 "+(direction.equals(RoutineStore.OUTBOUND)?"출근길":"퇴근길")
                +" · "+(draft.name==null||draft.name.trim().isEmpty()
                        ?"새 경로":draft.name.trim()));
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

    private void updateDirectionTabs(){
        boolean out=RoutineStore.OUTBOUND.equals(direction);
        styleTab(R.id.btnDirectionOut,out);
        styleTab(R.id.btnDirectionBack,!out);
    }
    private void styleTab(int id,boolean selected){
        MaterialButton tab=findViewById(id);
        tab.setEnabled(true);
        tab.setBackgroundTintList(ColorStateList.valueOf(
                getColor(selected?R.color.primary:R.color.surface)));
        tab.setTextColor(getColor(selected?R.color.surface:R.color.primary_dark));
        tab.setStrokeColor(ColorStateList.valueOf(
                getColor(selected?R.color.primary:R.color.border)));
        tab.setStrokeWidth(dp(selected?0:1));
        tab.setTypeface(null,selected?Typeface.BOLD:Typeface.NORMAL);
    }

    private void renderSaved(){
        savedList.removeAllViews();
        List<Routine> profiles=RoutineStore.list(this,direction);
        String defaultId=RoutineStore.defaultId(this,direction);
        boolean saved=isSaved(draft.id);
        MaterialButton makeDefault=findViewById(R.id.btnMakeDefault);
        makeDefault.setVisibility(saved&&!draft.id.equals(defaultId)?View.VISIBLE:View.GONE);
        if(profiles.isEmpty()){
            TextView empty=new TextView(this);
            empty.setText("아직 저장된 경로가 없어. 새 경로를 만들어 봐.");
            empty.setTextSize(14);
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setPadding(dp(12),dp(18),dp(12),dp(18));
            savedList.addView(empty);
            return;
        }
        for(Routine profile:profiles){
            boolean current=profile.id.equals(draft.id);
            boolean isDefault=profile.id.equals(defaultId);
            MaterialCardView card=new MaterialCardView(this);
            card.setRadius(dp(14));
            card.setCardElevation(0);
            card.setCardBackgroundColor(getColor(current?R.color.primary_light:R.color.surface));
            card.setStrokeColor(getColor(current?R.color.primary:R.color.border));
            card.setStrokeWidth(dp(current?2:1));
            card.setClickable(true);
            card.setFocusable(true);

            LinearLayout content=new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(16),dp(14),dp(16),dp(14));
            card.addView(content);

            LinearLayout heading=new LinearLayout(this);
            heading.setOrientation(LinearLayout.HORIZONTAL);
            heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
            content.addView(heading);

            TextView title=new TextView(this);
            title.setText(profile.name==null||profile.name.trim().isEmpty()
                    ?"이름 없는 경로":profile.name);
            title.setTextSize(16);
            title.setTypeface(null,Typeface.BOLD);
            title.setTextColor(getColor(R.color.text_primary));
            title.setMaxLines(2);
            heading.addView(title,new LinearLayout.LayoutParams(0,-2,1f));

            if(isDefault){
                TextView badge=new TextView(this);
                badge.setText("기본");
                badge.setTextSize(12);
                badge.setTypeface(null,Typeface.BOLD);
                badge.setTextColor(getColor(R.color.primary_dark));
                badge.setBackgroundResource(R.drawable.bg_route_selected);
                badge.setPadding(dp(10),dp(5),dp(10),dp(5));
                LinearLayout.LayoutParams badgeParams=new LinearLayout.LayoutParams(-2,-2);
                badgeParams.leftMargin=dp(8);
                heading.addView(badge,badgeParams);
            }
            if(current){
                TextView selected=new TextView(this);
                selected.setText("선택 중");
                selected.setTextSize(12);
                selected.setTextColor(getColor(R.color.primary_dark));
                LinearLayout.LayoutParams selectedParams=new LinearLayout.LayoutParams(-2,-2);
                selectedParams.leftMargin=dp(8);
                heading.addView(selected,selectedParams);
            }

            String start=profile.startName==null?"":profile.startName.trim();
            String end=profile.endName==null?"":profile.endName.trim();
            String summary=start+" → "+end;
            if(profile.name==null||!profile.name.contains(summary.trim())){
                TextView detail=new TextView(this);
                detail.setText(summary);
                detail.setTextSize(13);
                detail.setTextColor(getColor(R.color.text_secondary));
                detail.setMaxLines(2);
                LinearLayout.LayoutParams detailParams=new LinearLayout.LayoutParams(-1,-2);
                detailParams.topMargin=dp(7);
                content.addView(detail,detailParams);
            }

            LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);
            cardParams.bottomMargin=dp(10);
            savedList.addView(card,cardParams);
            card.setOnClickListener(v->loadProfile(profile.id));
        }
    }

    private void renderStops(){
        ((MaterialButton)findViewById(R.id.btnStartPlace)).setText(
                draft.startName.isEmpty()?"출발지 선택":draft.startName);
        ((MaterialButton)findViewById(R.id.btnEndPlace)).setText(
                draft.endName.isEmpty()?"도착지 선택":draft.endName);
        com.google.android.material.chip.ChipGroup group=findViewById(R.id.waypointChips);
        group.removeAllViews();
        boolean any=false;
        for(int i=0;i<draft.waypoints.size();i++){
            Waypoint w=draft.waypoints.get(i);
            if(!w.hasCoordinates())continue;
            any=true;
            final int position=i;
            com.google.android.material.chip.Chip chip=new com.google.android.material.chip.Chip(this);
            chip.setText((i+1)+". "+w.name);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v->{
                if(isTracking()){toast("위치 추적 중 · 경유지 변경 불가");return;}
                draft.waypoints.remove(position);
                invalidateRoutes();
                renderStops();
            });
            group.addView(chip);
        }
        ((TextView)findViewById(R.id.textWaypoints)).setText(
                any?"선택한 순서대로 이동해":"등록한 경유 장소가 없어");
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
        routes=Collections.emptyList();selected=-1;order.clear();expandedRoutes.clear();
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

    private int dp(int value){
        return (int)(value*getResources().getDisplayMetrics().density+0.5f);
    }

    private void chooseCandidate(int index){
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
            View card=getLayoutInflater().inflate(R.layout.item_route_result,cards,false);
            com.google.android.material.card.MaterialCardView surface=
                    (com.google.android.material.card.MaterialCardView)card;
            surface.setStrokeColor(getColor(selected==index?R.color.primary:R.color.border));
            surface.setStrokeWidth(dp(selected==index?2:1));
            TextView rank=card.findViewById(R.id.routeCardIndex);
            TextView flag=card.findViewById(R.id.routeCardSelected);
            TextView duration=card.findViewById(R.id.routeCardTime);
            TextView meta=card.findViewById(R.id.routeCardMeta);
            LinearLayout segments=card.findViewById(R.id.routeCardLegs);
            MaterialButton expand=card.findViewById(R.id.routeCardExpand);
            MaterialButton choose=card.findViewById(R.id.routeCardChoose);
            rank.setText((index+1)+"번 경로");
            flag.setVisibility(selected==index?View.VISIBLE:View.GONE);
            duration.setText(TransitRoute.minutes(route.totalSeconds)+"분");
            String info="도보 "+TransitRoute.minutes(route.walkSeconds)+"분 ("+route.walkMeters+"m)"
                    +" · 환승 "+route.transfers+"회";
            if(route.fareWon>0)
                info+=" · "+String.format(java.util.Locale.KOREA,"%,d원",route.fareWon);
            meta.setText(info);
            boolean expanded=expandedRoutes.contains(index);
            segments.setVisibility(expanded?View.VISIBLE:View.GONE);
            expand.setText(expanded?"구간 접기":"구간 상세 보기");
            if(expanded){
                for(TransitRoute.Leg leg:route.legs){
                    LinearLayout entry=new LinearLayout(this);
                    entry.setOrientation(LinearLayout.HORIZONTAL);
                    entry.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    entry.setPadding(0,dp(8),0,dp(8));
                    TextView icon=new TextView(this);
                    icon.setGravity(android.view.Gravity.CENTER);
                    icon.setText("BUS".equals(leg.mode)?"버스":
                            "SUBWAY".equals(leg.mode)?"철도":"도보");
                    icon.setTextSize(12);
                    icon.setTextColor(getColor(R.color.primary_dark));
                    icon.setBackgroundResource(R.drawable.bg_route_selected);
                    icon.setPadding(dp(9),dp(7),dp(9),dp(7));
                    entry.addView(icon,new LinearLayout.LayoutParams(dp(55),-2));
                    TextView detail=new TextView(this);
                    detail.setText((leg.routeName.isEmpty()?"":leg.routeName+" · ")
                            +TransitRoute.minutes(leg.seconds)+"분\n"
                            +leg.startName+" → "+leg.endName);
                    detail.setTextSize(14);
                    detail.setTextColor(getColor(R.color.text_primary));
                    LinearLayout.LayoutParams infoParams=new LinearLayout.LayoutParams(0,-2,1f);
                    infoParams.leftMargin=dp(12);
                    entry.addView(detail,infoParams);
                    segments.addView(entry,new LinearLayout.LayoutParams(-1,-2));
                }
            }
            expand.setOnClickListener(v->{
                if(expandedRoutes.contains(index))expandedRoutes.remove(index);
                else expandedRoutes.add(index);
                renderCandidates();
            });
            choose.setText(selected==index?"선택된 경로":"이 경로 선택");
            choose.setOnClickListener(v->chooseCandidate(index));
            card.setOnClickListener(v->chooseCandidate(index));
            cards.addView(card,new LinearLayout.LayoutParams(-1,-2));
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
