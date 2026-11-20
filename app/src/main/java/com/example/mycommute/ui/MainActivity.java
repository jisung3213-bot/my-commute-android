package com.example.mycommute.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import com.example.mycommute.data.TransitRouteCache;
import com.example.mycommute.model.TransitRoute;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mycommute.R;
import com.example.mycommute.alarms.ReminderScheduler;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.engine.RoutePlanner;
import com.example.mycommute.model.Routine;

public class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if(!Session.signedIn(this)) {startActivity(new Intent(this,LoginActivity.class));finish();return;}
        setContentView(R.layout.activity_main);Navigation.bind(this);
        findViewById(R.id.homeOutbound).setOnClickListener(v->chooseDirection(RoutineStore.OUTBOUND));
        findViewById(R.id.homeReturn).setOnClickListener(v->chooseDirection(RoutineStore.RETURN));
        findViewById(R.id.btnSelectRoutine).setOnClickListener(v->showNamedRoutes());
        findViewById(R.id.btnGoCustom).setOnClickListener(v -> startActivity(new Intent(this,CustomActivity.class)));
        findViewById(R.id.btnGoMap).setOnClickListener(v -> startActivity(new Intent(this,MapActivity.class)));
        findViewById(R.id.btnGoLogs).setOnClickListener(v -> startActivity(new Intent(this,AlarmLogActivity.class)));
        findViewById(R.id.btnGoTracking).setOnClickListener(v -> startActivity(new Intent(this,TrackingActivity.class)));
        findViewById(R.id.btnSignOut).setOnClickListener(v -> {
            ReminderScheduler.cancelCurrent(this);
            stopService(new Intent(this,com.example.mycommute.location.TrackingService.class));
            Session.signOut(this);
            Intent intent=new Intent(this,LoginActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);finish();
        });
    }
    @Override protected void onResume() { super.onResume(); if(Session.signedIn(this)) render(); }
    private void chooseDirection(String id) {
        if(RoutineStore.prefs(this).getBoolean("tracking_active",false)){
            android.widget.Toast.makeText(this,"위치 추적 중 · 경로 변경 불가",
                    android.widget.Toast.LENGTH_SHORT).show();return;
        }
        RoutineStore.selectDirection(this,id);
        ReminderScheduler.scheduleCurrent(this);
        render();
    }

    private void showNamedRoutes(){
        String direction=RoutineStore.activeDirection(this);
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
                .setItems(names,(d,index)->{
                    if(RoutineStore.prefs(this).getBoolean("tracking_active",false)){
                        android.widget.Toast.makeText(this,"위치 추적 중 · 변경 불가",
                                android.widget.Toast.LENGTH_SHORT).show();return;
                    }
                    RoutineStore.select(this,list.get(index).id);
                    ReminderScheduler.scheduleCurrent(this);
                    render();
                }).show();
    }

    private void render() {
        Routine r=RoutineStore.read(this);
        String direction=RoutineStore.activeDirection(this);
        findViewById(R.id.homeOutbound).setEnabled(!direction.equals(RoutineStore.OUTBOUND));
        findViewById(R.id.homeReturn).setEnabled(!direction.equals(RoutineStore.RETURN));
        ((TextView)findViewById(R.id.btnSelectRoutine)).setText("다른 경로 선택");
        RoutePlanner.Plan plan=RoutePlanner.select(r);
        ((TextView)findViewById(R.id.mainRoute)).setText(r.isConfigured()
                ?r.name+"\n"+r.startName+" → "+r.endName:"등록된 출퇴근 경로 없음");
        TextView time=findViewById(R.id.mainTime);
        if(!r.isConfigured()){
            UiNotice.show(time,UiNotice.State.EMPTY,"출퇴근 경로 등록 필요");
        }else if(r.apiBased){
            TransitRouteCache.Snapshot snapshot=TransitRouteCache.load(this,r);
            if(snapshot!=null){
                TransitRoute selected=snapshot.routes.get(snapshot.selected);
                time.setText("약 "+TransitRoute.minutes(selected.totalSeconds)+"분 · 목표 "+r.arrivalTime);
            }else time.setText("목표 "+r.arrivalTime+" · 경로 갱신 필요");
        }else if(plan.available){
            time.setText("출발 "+r.plannedDeparture(plan.durationMinutes)
                    +" · 목표 "+r.arrivalTime);
        }else time.setText("경로 재설정 필요");
    }
}
