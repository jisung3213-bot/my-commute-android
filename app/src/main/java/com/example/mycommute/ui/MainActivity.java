package com.example.mycommute.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mycommute.R;
import com.example.mycommute.alarms.ReminderScheduler;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.engine.RoutePlanner;
import com.example.mycommute.engine.TripTimeline;
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
            android.widget.Toast.makeText(this,"이동 추적 중에는 경로를 바꿀 수 없어",
                    android.widget.Toast.LENGTH_SHORT).show();return;
        }
        RoutineStore.selectDirection(this,id);
        render();
    }

    private void showNamedRoutes(){
        String direction=RoutineStore.activeDirection(this);
        java.util.List<Routine> list=RoutineStore.list(this,direction);
        if(list.isEmpty()){
            android.widget.Toast.makeText(this,"저장된 경로가 없어. 루틴에서 등록해",
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
                        android.widget.Toast.makeText(this,"이동 추적 중에는 변경할 수 없어",
                                android.widget.Toast.LENGTH_SHORT).show();return;
                    }
                    RoutineStore.select(this,list.get(index).id);
                    render();
                }).show();
    }

    private void render() {
        Routine r=RoutineStore.read(this);
        String direction=RoutineStore.activeDirection(this);
        findViewById(R.id.homeOutbound).setEnabled(!direction.equals(RoutineStore.OUTBOUND));
        findViewById(R.id.homeReturn).setEnabled(!direction.equals(RoutineStore.RETURN));
        ((TextView)findViewById(R.id.btnSelectRoutine)).setText("선택된 경로: "
                +(r.name.isEmpty()?"등록된 경로 없음":r.name)
                +" · 이름별 목록 보기");
        RoutePlanner.Plan plan=RoutePlanner.select(r);
        ((TextView)findViewById(R.id.mainGreeting)).setText("로그인된 계정 · 루틴 저장");
        ((TextView)findViewById(R.id.mainRoute)).setText(r.isConfigured()?r.name+"\n"+r.startName+" → "+r.endName:"아직 등록된 루틴이 없어");
        ((TextView)findViewById(R.id.mainTime)).setText(r.apiBased
                ? "경로 검색 시 이동시간 갱신 · 실시간 출발 알림 미연동"
                : r.isConfigured()
                ? (plan.available ? r.plannedDeparture(plan.durationMinutes)+" 출발 권장 · "+TripTimeline.expectedArrival(r,plan.durationMinutes)+" 계획 도착 / "+r.arrivalTime+" 목표" : "대체 경로 필요 · 출발 알림 미예약")
                : "출퇴근 커스텀에서 첫 루틴을 등록해줘");
        ((TextView)findViewById(R.id.mainWeather)).setText(r.apiBased
                ? (r.weatherEnabled?"기상정보 반영 설정 ON · 경로 검색 시 평가":"기상정보 반영 설정 OFF")
                : plan.note);
        ((TextView)findViewById(R.id.mainTransit)).setText(r.apiBased
                ? "대중교통 검색 연결됨 · 첫 탑승 실시간 도착정보는 경로 선택 후 조회"
                : "버스·지하철 실시간 도착정보: API 미연동");
    }
}
