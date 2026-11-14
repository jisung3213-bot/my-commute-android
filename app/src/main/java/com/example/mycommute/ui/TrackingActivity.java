package com.example.mycommute.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.seoulmobilitytester.R;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.Session;
import com.example.mycommute.engine.RoutePlanner;
import com.example.mycommute.engine.TripTimeline;
import com.example.mycommute.location.TrackingService;
import com.example.mycommute.model.Routine;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrackingActivity extends AppCompatActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){renderClock();handler.postDelayed(this,60000);}};
    private final BroadcastReceiver updates=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {updatePosition(intent.getDoubleExtra("lat",0),intent.getDoubleExtra("lon",0),intent.getLongExtra("timestamp",0));}
    };
    private boolean listening=false;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(!Session.signedIn(this)) {startActivity(new Intent(this,LoginActivity.class));finish();return;}
        String requested=getIntent().getStringExtra("routineId");
        if(requested!=null&&!RoutineStore.prefs(this).getBoolean("tracking_active",false))RoutineStore.select(this,requested);
        setContentView(R.layout.activity_tracking);Navigation.bind(this);
        findViewById(R.id.btnStartTracking).setOnClickListener(v -> startTracking());
        findViewById(R.id.btnStopTracking).setOnClickListener(v -> {
            Intent stop=new Intent(this,TrackingService.class);stop.setAction(TrackingService.ACTION_STOP);
            stopService(stop);RoutineStore.prefs(this).edit().putBoolean("tracking_active",false).apply();
            Toast.makeText(this,"위치 추적을 종료했어",Toast.LENGTH_SHORT).show();renderClock();
        });
        renderTimeline();
    }
    @Override protected void onResume() {
        super.onResume();renderTimeline();
        if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,new IntentFilter(TrackingService.ACTION_LOCATION),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(updates,new IntentFilter(TrackingService.ACTION_LOCATION));
        listening=true;handler.post(ticker);
        long last=RoutineStore.prefs(this).getLong("lastFixTime",0);
        if(last>0)updatePosition(RoutineStore.prefs(this).getFloat("lastLat",0),RoutineStore.prefs(this).getFloat("lastLon",0),last);
    }
    @Override protected void onPause() {
        if(listening){unregisterReceiver(updates);listening=false;}
        handler.removeCallbacks(ticker);super.onPause();
    }
    private void startTracking() {
        if(!RoutineStore.read(this).isConfigured()) {Toast.makeText(this,"먼저 루틴을 등록해",Toast.LENGTH_LONG).show();return;}
        if(!RoutePlanner.select(RoutineStore.read(this),RoutineStore.isRainDemo(this),RoutineStore.isColdDemo(this)).available){Toast.makeText(this,"대체 경로를 설정하세요",Toast.LENGTH_LONG).show();return;}
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION},308);return;
        }
        try {ContextCompat.startForegroundService(this,new Intent(this,TrackingService.class));renderClock();}
        catch(Exception e){Toast.makeText(this,"위치 추적을 시작할 수 없어: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==308&&grants.length>0&&(grants[0]==PackageManager.PERMISSION_GRANTED||(grants.length>1&&grants[1]==PackageManager.PERMISSION_GRANTED)))startTracking();
    }
    private void updatePosition(double lat,double lon,long timestamp) {
        if(timestamp==0)return;
        String time=new SimpleDateFormat("HH:mm:ss",Locale.KOREA).format(new Date(timestamp));
        ((TextView)findViewById(R.id.trackingLocation)).setText(String.format(Locale.KOREA,
                "GPS 위치: %.5f, %.5f · 최근 갱신 %s",lat,lon,time));
    }
    private void renderClock() {
        Routine r=RoutineStore.read(this);
        RoutePlanner.Plan plan=RoutePlanner.select(r,RoutineStore.isRainDemo(this),RoutineStore.isColdDemo(this));
        ((TextView)findViewById(R.id.trackingStatus)).setText(RoutineStore.prefs(this).getBoolean("tracking_active",false)
                ? "GPS 추적 실행 중 · 차량 도착정보 미연동" : "GPS 추적 중지됨 · 시작 버튼을 누르면 활성화돼");
        if(!r.isConfigured()) {
            ((TextView)findViewById(R.id.trackingEta)).setText("루틴 미등록");return;
        }
        if(!plan.available){((TextView)findViewById(R.id.trackingEta)).setText("대체 경로 필요");return;}
        boolean active=RoutineStore.prefs(this).getBoolean("tracking_active",false);
        long started=RoutineStore.prefs(this).getLong("trip_started",0);
        int elapsed=active&&started>0?(int)Math.max(0,(System.currentTimeMillis()-started)/60000):0;
        int remaining=Math.max(0,plan.durationMinutes-elapsed);
        String eta=TripTimeline.expectedArrival(r,plan.durationMinutes);
        if(active&&started>0)eta=new SimpleDateFormat("HH:mm",Locale.KOREA).format(new Date(started+plan.durationMinutes*60000L));
        ((TextView)findViewById(R.id.trackingEta)).setText(eta+" (계획 추정)");
        ((TextView)findViewById(R.id.trackingProgress)).setText((elapsed<0?"출발까지 "+(-elapsed)+"분":
                (active?"실제 시작 후 "+elapsed+"분 · 계획상 잔여 "+remaining+"분":"이동 시작 전 · "+r.plannedDeparture(plan.durationMinutes)+" 출발 권장"))+" / "+plan.mode);
        ((ProgressBar)findViewById(R.id.trackingBar)).setProgress(Math.max(0,Math.min(100,
                (int)(100.0*Math.max(0,elapsed)/Math.max(1,plan.durationMinutes)))));
    }
    private void renderTimeline() {
        LinearLayout container=findViewById(R.id.trackingTimeline);container.removeAllViews();
        Routine r=RoutineStore.read(this);
        if(!r.isConfigured())return;
        List<String> timeline=TripTimeline.steps(r);
        for(int i=0;i<timeline.size();i++) {
            TextView row=new TextView(this);row.setText((i+1)+". "+timeline.get(i));row.setTextSize(16);
            row.setPadding(20,18,20,18);row.setTextColor(getColor(R.color.text_primary));
            row.setBackgroundResource(R.drawable.bg_card);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=12;container.addView(row,p);
        }
    }
}
