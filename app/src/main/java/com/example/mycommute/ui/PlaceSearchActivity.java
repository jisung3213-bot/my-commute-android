package com.example.mycommute.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mycommute.R;
import com.example.mycommute.model.Place;
import com.example.mycommute.network.TmapPoiClient;
import java.util.List;

public class PlaceSearchActivity extends AppCompatActivity {
    public static final String EXTRA_NAME="placeName",EXTRA_LAT="placeLat",EXTRA_LON="placeLon";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Runnable pending;
    private EditText query;
    private LinearLayout results;
    private TextView status;
    private int generation;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_place_search);
        query=findViewById(R.id.editPlaceQuery);
        results=findViewById(R.id.placeResults);
        status=findViewById(R.id.placeStatus);
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                if(pending!=null)handler.removeCallbacks(pending);
                generation++;results.removeAllViews();
                String text=s.toString().trim();
                if(text.length()<2) {
                    UiNotice.show(status,UiNotice.State.IDLE,"");
                    return;
                }
                int token=generation;
                pending=()->lookup(text,token,false);
                handler.postDelayed(pending,650);
                UiNotice.show(status,UiNotice.State.IDLE,"");
            }
            @Override public void afterTextChanged(Editable s){}
        });
        findViewById(R.id.btnPlaceSearch).setOnClickListener(v-> {
            if(pending!=null)handler.removeCallbacks(pending);
            ++generation;results.removeAllViews();lookup(query.getText().toString().trim(),generation,true);
        });
    }

    private void lookup(String text,int token,boolean manual) {
        if(text.length()<2){UiNotice.show(status,UiNotice.State.EMPTY,"두 글자 이상 입력");return;}
        UiNotice.show(status,UiNotice.State.LOADING,"검색 중…");
        TmapPoiClient.search(text,new TmapPoiClient.Callback(){
            @Override public void success(List<Place> places) {
                if(isFinishing()||isDestroyed()||token!=generation)return;
                results.removeAllViews();
                UiNotice.show(status,places.isEmpty()?UiNotice.State.EMPTY:UiNotice.State.IDLE,
                        "검색 결과 없음");
                for(Place place:places) {
                    TextView item=new TextView(PlaceSearchActivity.this);
                    item.setText(place.name+"\n"+place.address);
                    item.setTextColor(getResources().getColor(R.color.text_primary));
                    item.setTextSize(16);
                    item.setPadding(20,22,20,22);
                    item.setBackgroundResource(R.drawable.bg_card);
                    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
                    p.bottomMargin=12;results.addView(item,p);
                    item.setOnClickListener(v->{
                        Intent result=new Intent();
                        result.putExtra(EXTRA_NAME,place.name);
                        result.putExtra(EXTRA_LAT,place.lat);
                        result.putExtra(EXTRA_LON,place.lon);
                        setResult(Activity.RESULT_OK,result);finish();
                    });
                }
            }
            @Override public void error(String message) {
                if(isFinishing()||isDestroyed()||token!=generation)return;
                results.removeAllViews();
                String display=UiNotice.error(message);
                UiNotice.show(status,UiNotice.classify(message),display);
                if(manual)UiNotice.toast(PlaceSearchActivity.this,display);
            }
        });
    }
    @Override protected void onDestroy() {
        generation++;if(pending!=null)handler.removeCallbacks(pending);
        super.onDestroy();
    }
}
