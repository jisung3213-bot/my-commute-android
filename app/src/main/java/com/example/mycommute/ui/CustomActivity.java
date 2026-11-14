package com.example.mycommute.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.seoulmobilitytester.R;
import com.example.mycommute.alarms.ReminderScheduler;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.Waypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomActivity extends AppCompatActivity {
    private static final String[] MODES={"대중교통","도보","자전거","혼합","자동차"};
    private final List<Waypoint> points=new ArrayList<>();
    private final int[] dayIds={R.id.dayMon,R.id.dayTue,R.id.dayWed,R.id.dayThu,R.id.dayFri,R.id.daySat,R.id.daySun};
    private final int[] calendarDays={2,3,4,5,6,7,1};
    private Spinner normal;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(!Session.signedIn(this)) {startActivity(new Intent(this,LoginActivity.class));finish();return;}
        setContentView(R.layout.activity_custom);Navigation.bind(this);
        normal=findViewById(R.id.spinnerMode);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,MODES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        normal.setAdapter(adapter);
        restore(RoutineStore.read(this));
        findViewById(R.id.btnAddWaypoint).setOnClickListener(v -> waypointDialog());
        findViewById(R.id.btnClearWaypoints).setOnClickListener(v -> {points.clear();renderPoints();});
        findViewById(R.id.btnSaveRoutine).setOnClickListener(v -> save());
    }
    private EditText field(int id) { return findViewById(id); }
    private String value(int id) {return field(id).getText().toString().trim();}
    private void text(int id,String value) {field(id).setText(value);}
    private void restore(Routine r) {
        text(R.id.editRoutineName,r.name);text(R.id.editStart,r.startName);text(R.id.editEnd,r.endName);
        coordinate(R.id.editStartLat,r.startLat);coordinate(R.id.editStartLon,r.startLon);
        coordinate(R.id.editEndLat,r.endLat);coordinate(R.id.editEndLon,r.endLon);
        text(R.id.editDeparture,r.arrivalTime);text(R.id.editMargin,""+r.marginMinutes);text(R.id.editDuration,r.estimatedMinutes==0?"":""+r.estimatedMinutes);

        text(R.id.editNormalWalk,""+r.normalWalkMinutes);
        text(R.id.editAlertLead,""+r.alertLeadMinutes);
        normal.setSelection(indexOf(r.transportMode));
        for(int i=0;i<dayIds.length;i++) ((CheckBox)findViewById(dayIds[i])).setChecked(r.runsOn(calendarDays[i]));
        text(R.id.editBoardingStopId,r.boardingStopId);text(R.id.editBoardingRoute,r.boardingRoute);
        text(R.id.editBoardingDirection,r.boardingDirection);
        ((CheckBox)findViewById(R.id.checkShowMap)).setChecked(r.mapVisible);
        ((CheckBox)findViewById(R.id.checkDemoRain)).setChecked(RoutineStore.isRainDemo(this));
        ((CheckBox)findViewById(R.id.checkDemoCold)).setChecked(RoutineStore.isColdDemo(this));
        points.clear();for(Waypoint point:r.waypoints)if(point.category.equals("경유"))points.add(point);renderPoints();
    }
    private void coordinate(int id,double v) {text(id,v==0?"":String.format(Locale.US,"%.6f",v));}
    private int indexOf(String x) {for(int i=0;i<MODES.length;i++)if(MODES[i].equals(x))return i;return 0;}
    private double numericCoord(int id) {String s=value(id);return s.isEmpty()?0:Double.parseDouble(s);}
    private int integer(int id) {return Integer.parseInt(value(id));}
    private static boolean validTime(String s) {return s.matches("(?:[01]\\d|2[0-3]):[0-5]\\d");}
    private void save() {
        if(RoutineStore.prefs(this).getBoolean("tracking_active",false)){toast("이동을 종료한 뒤 루틴을 수정하세요");return;}
        Routine prior=RoutineStore.read(this);
        Routine r=new Routine();
        try {
            r.name=value(R.id.editRoutineName);r.startName=value(R.id.editStart);r.endName=value(R.id.editEnd);
            r.startLat=numericCoord(R.id.editStartLat);r.startLon=numericCoord(R.id.editStartLon);
            r.endLat=numericCoord(R.id.editEndLat);r.endLon=numericCoord(R.id.editEndLon);
            r.id=RoutineStore.selected(this);r.arrivalTime=value(R.id.editDeparture);r.marginMinutes=integer(R.id.editMargin);r.estimatedMinutes=integer(R.id.editDuration);

            r.normalWalkMinutes=integer(R.id.editNormalWalk);
            r.alertLeadMinutes=integer(R.id.editAlertLead);
            r.transportMode=normal.getSelectedItem().toString();
            r.boardingStopId=value(R.id.editBoardingStopId);r.boardingRoute=value(R.id.editBoardingRoute);
            r.boardingDirection=value(R.id.editBoardingDirection);
            r.mapVisible=((CheckBox)findViewById(R.id.checkShowMap)).isChecked();
            List<String> days=new ArrayList<>();
            for(int i=0;i<dayIds.length;i++) if(((CheckBox)findViewById(dayIds[i])).isChecked()) days.add(""+calendarDays[i]);
            r.days=android.text.TextUtils.join(",",days);
            r.waypoints=new ArrayList<>(points);
        } catch(NumberFormatException ex) {toast("시간(분)과 좌표는 숫자로 입력해줘");return;}
        if(!r.isConfigured() || r.name.isEmpty() || !validTime(r.arrivalTime)) {toast("이름·출발지·도착지·목표 도착 시각을 확인해줘");return;}
        if(r.estimatedMinutes<1||r.estimatedMinutes>720||r.normalWalkMinutes<0||r.normalWalkMinutes>r.estimatedMinutes||r.marginMinutes<0||r.marginMinutes>180
                ||r.alertLeadMinutes<0||r.alertLeadMinutes>180||r.days.isEmpty()) {
            toast("이동시간(양수), 사전알림(0~180분), 운행 요일을 확인해줘");return;
        }
        if((r.startLat!=0||r.startLon!=0||r.endLat!=0||r.endLon!=0)&&!r.validCoordinates()) {
            toast("좌표를 입력하려면 출발·도착 위도와 경도를 모두 유효하게 입력해줘");return;
        }
        for(Waypoint w:r.waypoints) if(!w.plannedTime.isEmpty()&&!validTime(w.plannedTime)) {
            toast("경유지 알림 시각의 HH:mm 형식을 확인해줘");return;
        }
        ReminderScheduler.cancel(this,prior);
        RoutineStore.save(this,r);
        RoutineStore.setRainDemo(this,((CheckBox)findViewById(R.id.checkDemoRain)).isChecked());
        RoutineStore.setColdDemo(this,((CheckBox)findViewById(R.id.checkDemoCold)).isChecked());
        ReminderScheduler.scheduleCurrent(this);
        toast("루틴 저장 완료 · 대체 경로 필요 시 출발 알림은 예약하지 않습니다");
        requestNotificationAndExactAlarmPermissions();
    }
    private void toast(String s) {Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void requestNotificationAndExactAlarmPermissions() {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)
                !=PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},302);
        if(Build.VERSION.SDK_INT>=31) {
            AlarmManager manager=getSystemService(AlarmManager.class);
            if(manager!=null&&!manager.canScheduleExactAlarms()) {
                new AlertDialog.Builder(this).setTitle("정확한 출발 알림 권한")
                        .setMessage("현재는 시스템 사정에 따라 알림이 늦을 수 있어. 정확한 알림을 위해 시스템의 '알람 및 리마인더' 권한을 허용할 수 있어.")
                        .setNegativeButton("나중에",null).setPositiveButton("설정 열기",(d,w)-> {
                            try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));}catch(Exception ignored){}
                        }).show();
            }
        }
    }
    private void renderPoints() {
        TextView t=findViewById(R.id.textWaypoints);
        if(points.isEmpty()) {t.setText("등록된 경유지가 없어");return;}
        StringBuilder b=new StringBuilder();
        for(int i=0;i<points.size();i++) {
            Waypoint p=points.get(i);b.append(i+1).append(". ").append(p.category).append(" · ").append(p.name);
            if(!p.plannedTime.isEmpty()) b.append(" · ").append(p.plannedTime);
            b.append("\n");
        }
        t.setText(b.toString().trim());
    }
    private void waypointDialog() {
        LinearLayout root=new LinearLayout(this);root.setPadding(36,18,36,0);root.setOrientation(LinearLayout.VERTICAL);
        EditText name=new EditText(this);name.setHint("경유지 이름");root.addView(name);
        String[] types={"경유"};
        Spinner kind=new Spinner(this);kind.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));root.addView(kind);
        EditText time=new EditText(this);time.setText("");
        EditText lat=new EditText(this);lat.setHint("위도 (선택)");lat.setInputType(8194);root.addView(lat);
        EditText lon=new EditText(this);lon.setHint("경도 (선택)");lon.setInputType(8194);root.addView(lon);
        new AlertDialog.Builder(this).setTitle("경유지 추가")
                .setView(root).setNegativeButton("취소",null)
                .setPositiveButton("추가",(dialog,which)-> {
                    Waypoint p=new Waypoint();p.name=name.getText().toString().trim();
                    p.category=kind.getSelectedItem().toString();p.plannedTime=time.getText().toString().trim();
                    if(p.name.isEmpty()||(!p.plannedTime.isEmpty()&&!validTime(p.plannedTime))) {toast("이름과 시간 형식을 확인해줘");return;}
                    try {
                        if(!lat.getText().toString().trim().isEmpty()||!lon.getText().toString().trim().isEmpty()) {
                            p.lat=Double.parseDouble(lat.getText().toString());p.lon=Double.parseDouble(lon.getText().toString());
                            if(!p.hasCoordinates()) {toast("경유지 좌표가 유효하지 않아");return;}
                        }
                    } catch(Exception e) {toast("경유지 좌표를 확인해줘");return;}
                    points.add(p);renderPoints();
                }).show();
    }
}
