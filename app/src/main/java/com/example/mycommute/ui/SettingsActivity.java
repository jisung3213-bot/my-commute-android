package com.example.mycommute.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.example.mycommute.R;
import com.example.mycommute.alarms.AutoCommute;
import com.example.mycommute.alarms.ReminderScheduler;
import com.example.mycommute.data.AlarmSettingsStore;
import com.example.mycommute.data.ClockPreference;
import com.example.mycommute.data.FirestoreSync;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {
    private SwitchMaterial all;
    private LinearLayout list;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(!Session.signedIn(this)){
            startActivity(new Intent(this,LoginActivity.class));finish();return;
        }
        setContentView(R.layout.activity_settings);
        Navigation.bind(this);
        SwitchMaterial tracking=findViewById(R.id.switchAutoTracking);
        tracking.setChecked(AutoCommute.enabled(this));
        tracking.setOnCheckedChangeListener((button,enabled)->{
            AutoCommute.setEnabled(this,enabled);
            if(enabled)AutoCommute.resume(this,true);
            updateTrackingPermission();
        });
        findViewById(R.id.btnAutoLocation).setOnClickListener(v->requestTrackingPermission());
        SwitchMaterial originNotice=findViewById(R.id.switchOriginNotice);
        originNotice.setChecked(RoutineStore.showOriginNotice(this));
        originNotice.setOnCheckedChangeListener((button,enabled)->
                RoutineStore.setShowOriginNotice(this,enabled));
        SwitchMaterial amPm=findViewById(R.id.switchAmPm);
        amPm.setChecked(ClockPreference.amPm(this));
        amPm.setOnCheckedChangeListener((button,enabled)->{
            ClockPreference.setAmPm(this,enabled);
            updateClockExample();
        });
        updateClockExample();
        all=findViewById(R.id.switchAllAlerts);
        list=findViewById(R.id.alertSwitchList);
        TextView types=findViewById(R.id.btnAlertTypes);
        types.setOnClickListener(v->{
            boolean expand=list.getVisibility()!=View.VISIBLE;
            list.setVisibility(expand?View.VISIBLE:View.GONE);
            types.setText(expand?"알림 종류 접기":"알림 종류 펼치기");
        });
        all.setChecked(AlarmSettingsStore.overall(this));
        all.setOnCheckedChangeListener((button,enabled)->{
            AlarmSettingsStore.setOverall(this,enabled);
            persist();
            refreshSwitchStates();
        });
        for(int i=0;i<AlarmSettingsStore.TYPES.length;i++){
            String type=AlarmSettingsStore.TYPES[i];
            SwitchMaterial button=new SwitchMaterial(this);
            button.setText(AlarmSettingsStore.LABELS[i]);
            button.setTextSize(15);
            button.setTextColor(getColor(R.color.text_primary));
            button.setPadding(3,8,3,8);
            button.setChecked(AlarmSettingsStore.typeEnabled(this,type));
            button.setOnCheckedChangeListener((v,enabled)->{
                AlarmSettingsStore.setType(this,type,enabled);
                persist();
            });
            list.addView(button,new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        refreshSwitchStates();
        number(R.id.settingPrepareMinutes,AlarmSettingsStore.prepareMinutes(this));
        number(R.id.settingVehicleMinutes,AlarmSettingsStore.vehicleMinutes(this));
        number(R.id.settingWalkSlackMinutes,AlarmSettingsStore.walkSlackMinutes(this));
        number(R.id.settingDelayMinutes,AlarmSettingsStore.delayMinutes(this));
        findViewById(R.id.btnSaveAlarmSettings).setOnClickListener(v->saveThresholds());
        number(R.id.settingDefaultMargin,AlarmSettingsStore.defaultMargin(this));
        ((SwitchMaterial)findViewById(R.id.switchDefaultWeather))
                .setChecked(AlarmSettingsStore.defaultWeather(this));
        findViewById(R.id.btnSaveDefaultSettings).setOnClickListener(v->{
            try{
                int margin=read(R.id.settingDefaultMargin);
                if(margin<0||margin>120){
                    Toast.makeText(this,"여유시간은 0~120분",Toast.LENGTH_SHORT).show();
                    return;
                }
                AlarmSettingsStore.setDefaultMargin(this,margin);
                AlarmSettingsStore.setDefaultWeather(this,((SwitchMaterial)
                        findViewById(R.id.switchDefaultWeather)).isChecked());
                persist();
                Toast.makeText(this,"기본값 저장 완료",Toast.LENGTH_SHORT).show();
            }catch(NumberFormatException ex){
                Toast.makeText(this,"숫자를 입력하세요",Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btnSettingsLogout).setOnClickListener(v->{
            ReminderScheduler.cancelCurrent(this);
            stopService(new Intent(this,com.example.mycommute.location.TrackingService.class));
            Session.signOut(this);
            Intent intent=new Intent(this,LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);finish();
        });
        findViewById(R.id.btnSettingsRoutine).setOnClickListener(v->
                startActivity(new Intent(this,CustomActivity.class)));
        findViewById(R.id.btnSettingsLogs).setOnClickListener(v->
                startActivity(new Intent(this,AlarmLogActivity.class)));
        findViewById(R.id.btnSettingsSystemNotifications).setOnClickListener(v->{
            Intent intent=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
            startActivity(intent);
        });
        findViewById(R.id.btnSettingsLocation).setOnClickListener(v->{
            Intent intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+getPackageName()));
            startActivity(intent);
        });
        findViewById(R.id.btnSettingsExactAlarms).setOnClickListener(v->{
            if(Build.VERSION.SDK_INT>=31){
                Intent intent=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:"+getPackageName()));
                startActivity(intent);
            }
        });
    }

    @Override protected void onResume(){
        super.onResume();
        if(!Session.signedIn(this))return;
        updateExactAlarmPermission();
        updateTrackingPermission();
        AutoCommute.resume(this,true);
    }

    private void updateClockExample(){
        ((TextView)findViewById(R.id.clockExample)).setText(ClockPreference.amPm(this)
                ?"12시간제 · "+ClockPreference.time(this,"18:30"):"24시간제 · 18:30");
    }

    private void updateTrackingPermission(){
        boolean enabled=AutoCommute.enabled(this);
        boolean permission=AutoCommute.hasLocation(this)&&AutoCommute.hasBackgroundLocation(this);
        ((TextView)findViewById(R.id.autoTrackingStatus)).setText(enabled&&!permission?"앱을 닫아도 추적하려면 위치를 ‘항상 허용’으로 설정하세요."
                :"예정 출발 30분 전부터 출발지 500m 이내 위치를 확인합니다.");
        findViewById(R.id.btnAutoLocation).setVisibility(enabled&&!permission?View.VISIBLE:View.GONE);
    }

    private void requestTrackingPermission(){
        if(!AutoCommute.hasLocation(this)&&!RoutineStore.prefs(this)
                .getBoolean("auto_location_requested",false)){
            RoutineStore.prefs(this).edit().putBoolean("auto_location_requested",true).apply();
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION},420);
        }else{
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+getPackageName())));
        }
    }

    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==420){
            updateTrackingPermission();
            AutoCommute.resume(this,true);
        }
    }

    private void updateExactAlarmPermission(){
        boolean missing=false;
        if(Build.VERSION.SDK_INT>=31){
            AlarmManager manager=getSystemService(AlarmManager.class);
            missing=manager!=null&&!manager.canScheduleExactAlarms();
        }
        findViewById(R.id.btnSettingsExactAlarms).setVisibility(missing?View.VISIBLE:View.GONE);
        UiNotice.show(findViewById(R.id.settingsPermissionNotice),
                missing?UiNotice.State.UNAVAILABLE:UiNotice.State.IDLE,
                "정시 알림 권한이 없으면 알림이 늦어질 수 있습니다.");
    }

    private void refreshSwitchStates(){
        boolean enabled=AlarmSettingsStore.overall(this);
        for(int i=0;i<list.getChildCount();i++){
            list.getChildAt(i).setEnabled(enabled);
        }
    }

    private void number(int id,int value){
        ((EditText)findViewById(id)).setText(String.valueOf(value));
    }

    private int read(int id){
        String value=((EditText)findViewById(id)).getText().toString().trim();
        return Integer.parseInt(value);
    }

    private void saveThresholds(){
        try{
            if(!AlarmSettingsStore.setThresholds(this,
                    read(R.id.settingPrepareMinutes),read(R.id.settingVehicleMinutes),
                    read(R.id.settingWalkSlackMinutes),read(R.id.settingDelayMinutes))){
                Toast.makeText(this,"입력 범위를 확인하세요",Toast.LENGTH_SHORT).show();
                return;
            }
            persist();
            Toast.makeText(this,"알림 기준 저장 완료",Toast.LENGTH_SHORT).show();
        }catch(NumberFormatException ex){
            Toast.makeText(this,"숫자를 입력하세요",Toast.LENGTH_SHORT).show();
        }
    }

    private void persist(){
        FirestoreSync.uploadPreferences(this);
        ReminderScheduler.scheduleCurrent(this);
    }
}
