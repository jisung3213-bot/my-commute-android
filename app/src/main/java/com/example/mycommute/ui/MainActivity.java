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
        findViewById(R.id.btnSelectRoutine).setOnClickListener(v->new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("루틴 선택")
            .setItems(new String[]{"출근 / 등교","퇴근 / 하교"},(d,index)->{
                if(RoutineStore.prefs(this).getBoolean("tracking_active",false)){android.widget.Toast.makeText(this,"현재 이동을 종료한 뒤 변경하세요",android.widget.Toast.LENGTH_SHORT).show();return;}
                RoutineStore.select(this,RoutineStore.IDS[index]);render();
            }).show());
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
    private void render() {
        Routine r=RoutineStore.read(this);
        ((TextView)findViewById(R.id.btnSelectRoutine)).setText(r.id.equals("outbound")?"현재: 출근 / 등교 · 변경":"현재: 퇴근 / 하교 · 변경");
        RoutePlanner.Plan plan=RoutePlanner.select(r);
        ((TextView)findViewById(R.id.mainGreeting)).setText("로그인된 계정 · 루틴 저장");
        ((TextView)findViewById(R.id.mainRoute)).setText(r.isConfigured()?r.startName+" → "+r.endName:"아직 등록된 루틴이 없어");
        ((TextView)findViewById(R.id.mainTime)).setText(r.apiBased
                ? "경로 검색 시 이동시간 갱신 · 실시간 출발 알림 미연동"
                : r.isConfigured()
                ? (plan.available ? r.plannedDeparture(plan.durationMinutes)+" 출발 권장 · "+TripTimeline.expectedArrival(r,plan.durationMinutes)+" 계획 도착 / "+r.arrivalTime+" 목표" : "대체 경로 필요 · 출발 알림 미예약")
                : "출퇴근 커스텀에서 첫 루틴을 등록해줘");
        ((TextView)findViewById(R.id.mainWeather)).setText(r.apiBased
                ? (r.weatherEnabled?"기상정보 반영 설정 ON · 경로 검색 시 평가":"기상정보 반영 설정 OFF")
                : plan.note);
        ((TextView)findViewById(R.id.mainTransit)).setText(r.apiBased
                ? "대중교통 경로 검색 연결됨 · 실시간 차량 도착정보 미연동"
                : "버스·지하철 실시간 도착정보: API 미연동");
    }
}
