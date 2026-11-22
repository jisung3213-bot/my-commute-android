package com.example.mycommute.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import com.example.mycommute.alarms.AutoCommute;
import com.example.mycommute.data.ClockPreference;
import com.example.mycommute.data.PinnedRouteRefresh;
import com.example.mycommute.data.TransitRouteCache;
import com.example.mycommute.engine.CommuteWindow;
import com.example.mycommute.location.TrackingService;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mycommute.R;
import com.example.mycommute.alarms.ReminderScheduler;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.model.Routine;

public class MainActivity extends AppCompatActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable updateDirection=this::showUpcoming;
    private String manualRoute;
    private String shownRoute;

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if(!Session.signedIn(this)) {startActivity(new Intent(this,LoginActivity.class));finish();return;}
        setContentView(R.layout.activity_main);Navigation.bind(this);
        if(savedState!=null)manualRoute=savedState.getString("manualRoute");
        findViewById(R.id.homeOutbound).setOnClickListener(v->chooseDirection(RoutineStore.OUTBOUND));
        findViewById(R.id.homeReturn).setOnClickListener(v->chooseDirection(RoutineStore.RETURN));
        findViewById(R.id.btnSelectRoutine).setOnClickListener(v->showNamedRoutes());
        findViewById(R.id.btnGoLogs).setOnClickListener(v -> startActivity(new Intent(this,AlarmLogActivity.class)));
        findViewById(R.id.btnGoTracking).setOnClickListener(v -> startActivity(new Intent(this,TrackingActivity.class)));
    }
    @Override protected void onResume() {
        super.onResume();
        if(!Session.signedIn(this))return;
        ReminderScheduler.scheduleCurrent(this);
        AutoCommute.resume(this,true);
        showUpcoming();
    }

    @Override protected void onPause(){
        handler.removeCallbacks(updateDirection);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);
        state.putString("manualRoute",manualRoute);
    }

    private void showUpcoming(){
        if(manualRoute!=null){show(manualRoute);return;}
        String outbound=RoutineStore.defaultId(this,RoutineStore.OUTBOUND);
        String inbound=RoutineStore.defaultId(this,RoutineStore.RETURN);
        long now=System.currentTimeMillis();
        long nextOut=CommuteWindow.nextArrival(RoutineStore.read(this,outbound),now);
        long nextBack=CommuteWindow.nextArrival(RoutineStore.read(this,inbound),now);
        show(nextOut<=nextBack?outbound:inbound);
        long next=Math.min(nextOut,nextBack);
        if(next!=Long.MAX_VALUE)handler.postDelayed(updateDirection,Math.min(next-now,60_000L));
    }

    private void show(String id){
        shownRoute=id;
        if(!id.equals(RoutineStore.selected(this)))RoutineStore.select(this,id);
        render();
    }

    private void chooseDirection(String id) {
        if(id.equals(RoutineStore.directionOf(shownRoute)))return;
        selectPreview(RoutineStore.defaultId(this,id));
    }

    private void selectPreview(String id){
        handler.removeCallbacks(updateDirection);
        manualRoute=id;
        show(id);
    }

    private void showNamedRoutes(){
        String direction=RoutineStore.directionOf(shownRoute);
        java.util.List<Routine> list=RoutineStore.list(this,direction);
        if(list.isEmpty()){
            android.widget.Toast.makeText(this,"저장된 경로 없음",
                    android.widget.Toast.LENGTH_SHORT).show();return;
        }
        String[] names=new String[list.size()];
        String defaultId=RoutineStore.defaultId(this,direction);
        for(int i=0;i<list.size();i++){
            Routine r=list.get(i);
            names[i]=(r.id.equals(defaultId)?"★ 기본 · ":"")+r.name
                    +" · "+r.startName+" → "+r.endName;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("저장된 경로")
                .setItems(names,(d,index)->selectPreview(list.get(index).id)).show();
    }

    private void render() {
        Routine r=RoutineStore.read(this,shownRoute);
        String direction=RoutineStore.directionOf(r.id);
        boolean outbound=direction.equals(RoutineStore.OUTBOUND);
        Navigation.direction(this,R.id.homeOutbound,outbound);
        Navigation.direction(this,R.id.homeReturn,!outbound);
        String label=outbound?"출근길":"퇴근길";
        if(r.id.equals(TrackingService.activeRoutineId())&&TrackingService.guiding())label+=" · 위치 추적 중";
        else if(r.isConfigured()&&r.id.equals(RoutineStore.defaultId(this,direction)))label+=" · 기본 경로";
        ((TextView)findViewById(R.id.mainDirection)).setText(label);
        ((TextView)findViewById(R.id.mainRoute)).setText(r.isConfigured()
                ?r.name+"\n"+r.startName+" → "+r.endName:"등록된 경로 없음");
        TextView time=findViewById(R.id.mainTime);
        if(!r.isConfigured()){
            UiNotice.show(time,UiNotice.State.EMPTY,"출퇴근 경로 등록 필요");
            return;
        }
        int duration=ReminderScheduler.durationMinutes(this,r);
        String goal="목표 "+ClockPreference.time(this,r.arrivalTime);
        String plan=duration>0?"출발 "+ClockPreference.time(this,r.plannedDeparture(duration))+" · "+goal:goal;
        time.setVisibility(View.VISIBLE);
        time.setTextColor(getColor(R.color.text_secondary));
        time.setText(plan);
        boolean missing=r.apiBased&&TransitRouteCache.load(this,r)==null;
        if(missing&&PinnedRouteRefresh.canRefresh(r)){
            boolean loading=PinnedRouteRefresh.request(this,r,System.currentTimeMillis(),ok->{
                if(isDestroyed()||isFinishing()||!r.id.equals(shownRoute))return;
                if(ok)render();
                else UiNotice.show(time,UiNotice.State.UNAVAILABLE,plan+"\n상세 경로 확인 불가");
            });
            UiNotice.show(time,loading?UiNotice.State.LOADING:UiNotice.State.UNAVAILABLE,
                    plan+"\n"+(loading?"상세 경로 갱신 중…":"상세 경로 확인 불가"));
        }else if(missing){
            time.setText(plan+"\n루틴에서 상세 경로 선택");
        }
    }
}
