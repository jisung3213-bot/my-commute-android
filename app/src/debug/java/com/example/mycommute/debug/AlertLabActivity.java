package com.example.mycommute.debug;

import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mycommute.alarms.AlertLabMode;
import com.example.mycommute.alarms.AlertCopy;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.model.Routine;
import com.example.mycommute.alarms.NotificationHelper;
import com.example.mycommute.data.AlarmLogRepository;
import com.example.mycommute.data.AlarmSettingsStore;
import com.example.mycommute.engine.AlertThresholds;
import com.example.mycommute.R;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Debug-only, isolated fixture runner. No provider, GPS or production scheduler calls.
 * This is an alert-output/threshold simulator, NOT a proof of end-to-end GPS/API reliability.
 */
public final class AlertLabActivity extends AppCompatActivity {
    private Spinner type,scenario;
    private EditText eta,walking,distance,accuracy,elapsed,delay,offset;
    private TextView result;
    private int event=0;
    private String lastReport="";
    private String lastApiReport="";
    private long virtualOffsetMs=0L;
    private final String[] names={"출발 준비","출발 권장","대중교통 도착 임박",
        "승차지 출발","환승 준비","하차 준비","도착 지연 예상",
        "날씨 주의","경로 이탈","목적지 도착"};
    private final String[] types=AlarmSettingsStore.TYPES;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(!AlertLabMode.enabled(this)){finish();return;}
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32,24,32,32);
        scroll.addView(root);
        setContentView(scroll);
        addLabel(root,"알림 시뮬레이터 · DEBUG",22);
        Button full=new Button(this);
        full.setText("전체 알림 자동 검증 시작 (한 번 실행)");
        root.addView(full);
        full.setOnClickListener(v->{
            full.setEnabled(false);
            updateResult("검증 실행 중...");
            AlertLabSuite.run(this,report->{
                lastReport=report;
                updateResult(report);
                full.setEnabled(true);
                copyReport();
                Toast.makeText(this,"전체 결과 복사 완료",Toast.LENGTH_LONG).show();
            });
        });
        Button copy=new Button(this);
        copy.setText("최근 전체 검증 결과 다시 복사");
        root.addView(copy);
        copy.setOnClickListener(v->copyReport());
        addLabel(root,"모의 알림은 실제 알림과 같은 문구를 사용하며 출퇴근 알림 기록에는 표시되지 않습니다.",13);
        addLabel(root,"실제 교통·기상 API 연결 진단 (실행당 최대 9건)",18);
        Button api=new Button(this);
        api.setText("실제 API 양식·응답·오류 진단 실행");
        root.addView(api);
        api.setOnClickListener(v->{
            api.setEnabled(false);
            updateResult("지하철·버스·기상청 실 API 진단 실행 중...");
            ApiProbe.run(this,report->{
                lastApiReport=report;
                updateResult(report);
                api.setEnabled(true);
                copyText("live-api-probe",lastApiReport);
                Toast.makeText(this,"API 진단 결과가 복사됐어",Toast.LENGTH_LONG).show();
            });
        });
        Button apiCopy=new Button(this);
        apiCopy.setText("최근 API 진단 보고서 다시 복사");
        root.addView(apiCopy);
        apiCopy.setOnClickListener(v->{
            if(lastApiReport.isEmpty())Toast.makeText(this,"API 진단 결과 없음 · 먼저 진단 실행",Toast.LENGTH_SHORT).show();
            else copyText("live-api-probe",lastApiReport);
        });
        addLabel(root,"경로 추천 점수·정렬 테스트",18);
        Button rankingLab=new Button(this);
        rankingLab.setText("경로 추천 로직 테스트 열기");
        root.addView(rankingLab);
        rankingLab.setOnClickListener(v->startActivity(
                new android.content.Intent(this,RouteRecommendationLabActivity.class)));
        addLabel(root,"가상 날씨·여유시간·경로 후보를 바꾸거나 저장된 경로 후보의 추천 순위를 비교할 수 있습니다.",13);
        addLabel(root,"경로상 GPS 마커 모의실험",18);
        Button markerLab=new Button(this);
        markerLab.setText("GPS 경로 마커 테스트 열기");
        root.addView(markerLab);
        markerLab.setOnClickListener(v->startActivity(
                new android.content.Intent(this,RouteGpsLabActivity.class)));
        addLabel(root,"저장된 경로의 구간과 진행률을 모의로 조정합니다.",13);
        addLabel(root,"지하철역별 도착예정시간 제공 여부 (12개 역 · 최대 12회 요청)",18);
        Button survey=new Button(this);
        survey.setText("지하철역 ETA 일괄 검사 실행");
        root.addView(survey);
        survey.setOnClickListener(v->{
            survey.setEnabled(false);
            updateResult("12개 역의 실시간 ETA 제공 여부 조사 중...");
            SubwayEtaSurvey.run(this,report->{
                lastApiReport=report;
                updateResult(report);
                survey.setEnabled(true);
                copyText("subway-eta-survey",report);
                Toast.makeText(this,"역별 ETA 조사 결과 복사 완료",Toast.LENGTH_LONG).show();
            });
        });
        addLabel(root,"요청 결과는 조회 시점의 표본이며, 동일 역에서도 시간대와 열차에 따라 달라질 수 있습니다. 연속 재실행은 15분 제한.",13);
        type=new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        root.addView(type);
        scenario=new Spinner(this);
        scenario.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"정상 조건","경계 실패","정보 만료/불확실"}));
        root.addView(scenario);
        eta=field(root,"차량 ETA (초)",300);
        walking=field(root,"도보 소요 (초)",120);
        distance=field(root,"경로/지점까지 거리 (m)",60);
        accuracy=field(root,"GPS 정확도 (m)",10);
        elapsed=field(root,"조건 지속시간 (초)",35);
        delay=field(root,"목표 대비 지연시간 (분)",6);
        offset=field(root,"가상 시계 진행 (분)",1);
        Button advance=new Button(this);advance.setText("가상 시계 진행");
        root.addView(advance);
        advance.setOnClickListener(v->{
            virtualOffsetMs+=(long)value(offset)*60000L;
            updateResult("가상 시계: "+time()+" · 실제 시스템 시각은 변경하지 않음");
        });
        Button run=new Button(this);run.setText("시나리오 실행 및 알림 발송");
        root.addView(run);
        run.setOnClickListener(v->simulate());
        Button all=new Button(this);all.setText("알림 10종 정상/실패 조건 일괄 검사 (발송 없음)");
        root.addView(all);
        all.setOnClickListener(v->{
            int pass=0;
            for(int i=0;i<types.length;i++)
                if(evaluate(i,0)&&!evaluate(i,1)&&!evaluate(i,2))pass++;
            updateResult("모의 조건 검사: "+pass+"/10\n시스템 알림·실 GPS/API 통합 검증은 별도");
        });
        result=new TextView(this);result.setTextSize(15);result.setPadding(0,20,0,12);
        root.addView(result);
        updateResult("가상 시각 "+time()+" · 테스트 모드");
    }
    private void addLabel(LinearLayout root,String s,int size){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);
        t.setPadding(0,8,0,10);root.addView(t);
    }
    private EditText field(LinearLayout root,String label,int initial){
        addLabel(root,label,13);
        EditText e=new EditText(this);e.setInputType(2);
        e.setSingleLine(true);e.setText(String.valueOf(initial));root.addView(e);return e;
    }
    private int value(EditText e){
        try{return Math.max(0,Integer.parseInt(e.getText().toString().trim()));}
        catch(NumberFormatException ex){return 0;}
    }
    private String time(){
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.KOREA)
                .format(new Date(System.currentTimeMillis()+virtualOffsetMs));
    }
    private boolean evaluate(int id,int variant){
        if(variant==2)return false; // stale/missing source never generates a fabricated alert
        if(variant==1)return false; // boundary-negative fixture
        switch(id){
            case 0:case 1:return true; // synthetic clock hits respective schedule
            case 2:return AlertThresholds.vehicleDue(300,5);
            case 3:return AlertThresholds.walkDue(480,300,120,2);
            case 4:return 85<100 && 16>=15;
            case 5:return 85<95 && 16>=15;
            case 6:return AlertThresholds.delayed(360000,0+60000,5);
            case 7:return true; // valid, fresh adverse forecast fixture
            case 8:return AlertThresholds.offRoute(190,20,30000);
            case 9:return AlertThresholds.nearDestination(30,true,4,5,70000,20000);
            default:return false;
        }
    }
    private boolean evaluateInputs(int id,int variant){
        if(variant!=0)return evaluate(id,variant);
        switch(id){
            case 2:return AlertThresholds.vehicleDue(value(eta),
                AlarmSettingsStore.vehicleMinutes(this));
            case 3:return AlertThresholds.walkDue(value(eta),value(walking),120,
                AlarmSettingsStore.walkSlackMinutes(this));
            case 4:return value(distance)<100&&value(elapsed)>=15&&value(accuracy)<=50;
            case 5:return value(distance)<95&&value(elapsed)>=15&&value(accuracy)<=50;
            case 6:return value(delay)>=AlarmSettingsStore.delayMinutes(this);
            case 8:return AlertThresholds.offRoute(value(distance),value(accuracy),
                value(elapsed)*1000L);
            case 9:return AlertThresholds.nearDestination(value(distance),true,
                4,5,70000,value(elapsed)*1000L);
            default:return evaluate(id,variant);
        }
    }
    private void simulate(){
        if(!AlertLabMode.enabled(this)){finish();return;}
        int id=type.getSelectedItemPosition(),variant=scenario.getSelectedItemPosition();
        boolean fire=evaluateInputs(id,variant);
        String trace=names[id]+" · "+time()+" · "+
                (variant==0?"정상":variant==1?"경계 실패":"정보 불확실");
        if(!fire){updateResult(trace+"\n판정: 발송하지 않음 (예상된 미발송)");return;}
        AlertCopy.Message copy=sampleCopy(id);
        boolean sent=NotificationHelper.show(this,copy.title,copy.body,
                ("lab-"+(++event)).hashCode(),"lab-fixture");
        AlarmLogRepository.write(this,"test:"+types[id],
                sent?copy.title:"알림 차단됨",copy.body,true,null);
        updateResult(trace+"\n제목: "+copy.title+"\n본문: "+copy.body
                +"\n판정: "+(sent?"알림 요청 성공":"기기 알림 권한 차단"));
    }
    /** The same user-visible copy factory as production, with fixed synthetic facts. */
    private AlertCopy.Message sampleCopy(int id){
        Routine saved=RoutineStore.read(this);
        String profile=saved.name==null||saved.name.trim().isEmpty()?"학교 가는 길":saved.name;
        switch(id){
            case 0:return AlertCopy.prepare(AlarmSettingsStore.prepareMinutes(this),
                    "08:10",profile,"");
            case 1:return AlertCopy.departure("09:00",profile);
            case 2:return AlertCopy.vehicle("광운대역",value(eta));
            case 3:return AlertCopy.walk("광운대역",value(eta)-value(walking)-120);
            case 4:return AlertCopy.transfer("시청역","2호선");
            case 5:return AlertCopy.exit("서울역");
            case 6:return AlertCopy.delay(value(delay));
            case 7:return AlertCopy.weather("비 예보 · 우산 준비");
            case 8:return AlertCopy.deviation();
            case 9:return AlertCopy.finish("광운대학교");
            default:throw new IllegalArgumentException("알림 종류 오류");
        }
    }

    private void copyReport(){
        if(lastReport.isEmpty()){
            Toast.makeText(this,"전체 검증 결과 없음 · 먼저 검증 실행",Toast.LENGTH_SHORT).show();return;
        }
        copyText("alert-lab-report",lastReport);
    }
    private void copyText(String label,String text){
        ClipboardManager manager=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(manager!=null)manager.setPrimaryClip(ClipData.newPlainText(label,text));
    }
    private void updateResult(String s){if(result!=null)result.setText(s);}
}
