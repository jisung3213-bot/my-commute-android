package com.example.mycommute.debug;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mycommute.alarms.AlertLabMode;
import com.example.mycommute.data.RoutineStore;
import com.example.mycommute.data.TransitRouteCache;
import com.example.mycommute.engine.WeatherRouteRanker;
import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitRoute;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Debug-only route scorer. No external APIs, GPS, caching mutations or actual route selection. */
public final class RouteRecommendationLabActivity extends AppCompatActivity {
    private static final String[] WEATHER={
            "기상정보 없음","맑음","비·눈","영하 기온","한파특보(모의)","비 + 한파특보(모의)"
    };
    private final EditText[] durations=new EditText[3],walks=new EditText[3],transfers=new EditText[3];
    private Spinner condition;
    private EditText margin;
    private TextView reportView;
    private String lastReport="";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!AlertLabMode.enabled(this)){finish();return;}
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(16),dp(16),dp(20));
        scroll.addView(root);
        setContentView(scroll);
        label(root,"경로 추천 로직 검증 · DEBUG",22);
        label(root,"실제 앱과 같은 추천 계산을 사용합니다. 기상 조건은 테스트 입력입니다.",13);
        label(root,"기상 조건",16);
        condition=new Spinner(this);
        condition.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,WEATHER));
        root.addView(condition);
        label(root,"도착 여유시간 (분, 0~180)",16);
        margin=number(root,10);
        final int[][] fixtures={{30,14,2},{36,4,0},{33,8,1}};
        for(int i=0;i<3;i++){
            label(root,"후보 "+(i+1)+" (총 소요시간 / 도보시간 / 환승 횟수)",16);
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            root.addView(row);
            durations[i]=number(row,fixtures[i][0]);
            walks[i]=number(row,fixtures[i][1]);
            transfers[i]=number(row,fixtures[i][2]);
            for(EditText input:new EditText[]{durations[i],walks[i],transfers[i]}){
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(52),1);
                lp.rightMargin=dp(6);
                input.setLayoutParams(lp);
            }
        }
        Button simulate=new Button(this);
        simulate.setText("가상 후보 추천 실행");
        root.addView(simulate);
        simulate.setOnClickListener(v->evaluate(false));
        Button cached=new Button(this);
        cached.setText("현재 저장된 실제 경로 후보로 검사");
        root.addView(cached);
        cached.setOnClickListener(v->evaluate(true));
        Button copy=new Button(this);
        copy.setText("결과 복사");
        root.addView(copy);
        copy.setOnClickListener(v->{
            if(lastReport.isEmpty())return;
            ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("route-recommendation-test",lastReport));
        });
        reportView=label(root,"검사 결과가 여기에 표시됩니다.",14);
    }

    private void evaluate(boolean actual){
        try{
            int buffer=value(margin);
            if(buffer>180)throw new IllegalArgumentException("여유시간 입력 범위 0~180분");
            WeatherRouteRanker.Conditions weather=weather();
            List<WeatherRouteRanker.Candidate> candidates=new ArrayList<>();
            if(actual){
                Routine routine=RoutineStore.read(this);
                TransitRouteCache.Snapshot cache=TransitRouteCache.load(this,routine);
                if(cache==null||cache.routes.isEmpty())
                    throw new IllegalArgumentException("23시간 이내에 검색한 경로 후보가 필요합니다.");
                for(int i=0;i<cache.routes.size();i++){
                    TransitRoute r=cache.routes.get(i);
                    int duration=Math.max(1,TransitRoute.minutes(r.totalSeconds));
                    int walk=Math.min(duration,TransitRoute.minutes(r.walkSeconds));
                    candidates.add(new WeatherRouteRanker.Candidate(
                            "저장 후보 "+(i+1),"대중교통",duration,walk,0,r.transfers,false));
                }
            }else{
                for(int i=0;i<3;i++){
                    int duration=value(durations[i]),walk=value(walks[i]),transfer=value(transfers[i]);
                    candidates.add(new WeatherRouteRanker.Candidate(
                            "후보 "+(i+1),"대중교통",duration,walk,0,transfer,false));
                }
            }
            List<Integer> ranked=WeatherRouteRanker.rank(candidates,weather,buffer);
            int fastest=Integer.MAX_VALUE;
            for(WeatherRouteRanker.Candidate c:candidates)
                fastest=Math.min(fastest,c.durationMinutes);
            int extra=WeatherRouteRanker.acceptableExtraMinutes(buffer);
            StringBuilder text=new StringBuilder();
            text.append("경로 추천 테스트 · ").append(actual?"실제 저장 후보":"모의 후보").append('\n');
            text.append("기상: ").append(WEATHER[condition.getSelectedItemPosition()])
                    .append(" / 여유: ").append(buffer).append("분\n");
            text.append("최단: ").append(fastest).append("분 / 우선 검토 상한: ")
                    .append(fastest+extra).append("분\n");
            text.append("점수 = 소요시간 + 도보 가중치×도보분 + 환승 가중치×환승횟수\n");
            text.append("낮을수록 우선. 상한 초과 후보는 먼저 뒤로 배치.\n\n");
            for(int i=0;i<ranked.size();i++){
                WeatherRouteRanker.Candidate c=candidates.get(ranked.get(i));
                double score=WeatherRouteRanker.commuteScore(c,weather,buffer);
                text.append(i==0?"[최고 추천] ":"[대안 "+i+"] ")
                        .append(c.id).append(" · ")
                        .append(c.durationMinutes).append("분 / 도보 ")
                        .append(c.walkingMinutes).append("분 / 환승 ")
                        .append(c.transfers).append("회\n")
                        .append("점수 ").append(String.format(Locale.KOREA,"%.2f",score))
                        .append(" / ").append(c.durationMinutes<=fastest+extra?"우선 검토":"상한 초과")
                        .append('\n');
            }
            lastReport=text.toString();
        }catch(Exception e){
            lastReport="입력 또는 경로 데이터 오류: "+e.getMessage();
        }
        reportView.setText(lastReport);
    }
    private WeatherRouteRanker.Conditions weather(){
        switch(condition.getSelectedItemPosition()){
            case 1:return new WeatherRouteRanker.Conditions(true,false,false,false);
            case 2:return new WeatherRouteRanker.Conditions(true,true,false,false);
            case 3:return new WeatherRouteRanker.Conditions(true,false,false,true);
            case 4:return new WeatherRouteRanker.Conditions(true,false,true,false);
            case 5:return new WeatherRouteRanker.Conditions(true,true,true,false);
            default:return new WeatherRouteRanker.Conditions(false,false,false,false);
        }
    }
    private int value(EditText field){
        try{return Integer.parseInt(field.getText().toString().trim());}
        catch(Exception e){throw new IllegalArgumentException("숫자 입력 확인 필요");}
    }
    private EditText number(LinearLayout parent,int initial){
        EditText input=new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setText(String.valueOf(initial));
        parent.addView(input,new LinearLayout.LayoutParams(-1,dp(52)));
        return input;
    }
    private TextView label(LinearLayout parent,String value,int size){
        TextView text=new TextView(this);
        text.setText(value);text.setTextSize(size);
        text.setPadding(0,dp(7),0,dp(7));
        parent.addView(text);
        return text;
    }
    private int dp(int amount){return (int)(amount*getResources().getDisplayMetrics().density+0.5f);}
}
