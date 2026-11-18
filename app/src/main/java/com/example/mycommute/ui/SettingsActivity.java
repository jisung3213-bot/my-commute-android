package com.example.mycommute.ui;

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
import com.example.mycommute.R;
import com.example.mycommute.BuildConfig;
import com.example.mycommute.alarms.AlertLabMode;
import com.example.mycommute.alarms.ReminderScheduler;
import com.example.mycommute.data.AlarmSettingsStore;
import com.example.mycommute.data.FirestoreSync;
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
        all=findViewById(R.id.switchAllAlerts);
        list=findViewById(R.id.alertSwitchList);
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
        if(BuildConfig.DEBUG){
            SwitchMaterial lab=new SwitchMaterial(this);
            lab.setText("알림 테스트 모드 (디버그 전용)");
            lab.setChecked(AlertLabMode.enabled(this));
            lab.setOnCheckedChangeListener((v,enabled)->{
                if(enabled){
                    ReminderScheduler.cancelCurrent(this);
                    stopService(new Intent(this,com.example.mycommute.location.TrackingService.class));
                }
                AlertLabMode.setEnabled(this,enabled);
                if(!enabled)ReminderScheduler.scheduleCurrent(this);
                Toast.makeText(this,enabled?"테스트 모드 활성화 · 실제 알림 중지":"실제 모드 복귀",Toast.LENGTH_SHORT).show();
            });
            list.addView(lab,0);
            com.google.android.material.button.MaterialButton openLab=
                    new com.google.android.material.button.MaterialButton(this);
            openLab.setText("알림 시뮬레이터 열기");
            openLab.setOnClickListener(v->{
                if(!AlertLabMode.enabled(this)){
                    Toast.makeText(this,"먼저 테스트 모드를 켜세요",Toast.LENGTH_SHORT).show();return;
                }
                startActivity(new Intent().setClassName(this,
                    "com.example.mycommute.debug.AlertLabActivity"));
            });
            list.addView(openLab,1);
        }
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
        updateExactAlarmPermission();
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
                "정시 알림 권한이 없어 예정 시간보다 늦어질 수 있음");
    }

    private void refreshSwitchStates(){
        for(int i=0;i<list.getChildCount();i++)
            list.getChildAt(i).setEnabled(AlarmSettingsStore.overall(this));
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
