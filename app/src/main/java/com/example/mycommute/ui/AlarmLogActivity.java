package com.example.mycommute.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.seoulmobilitytester.R;
import com.example.mycommute.data.AlarmLogEntity;
import com.example.mycommute.data.AlarmLogRepository;
import com.example.mycommute.data.Session;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlarmLogActivity extends AppCompatActivity {
    private LinearLayout list;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(!Session.signedIn(this)) {startActivity(new Intent(this,LoginActivity.class));finish();return;}
        setContentView(R.layout.activity_alarm_log);Navigation.bind(this);
        list=findViewById(R.id.logList);
        findViewById(R.id.btnMarkRead).setOnClickListener(v -> AlarmLogRepository.markRead(this,this::load));
    }
    @Override protected void onResume() {super.onResume();load();}
    private TextView text(String s,int size) {
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(getColor(R.color.text_primary));return t;
    }
    private void load() {AlarmLogRepository.list(this,this::render);}
    private void render(List<AlarmLogEntity> rows) {
        list.removeAllViews();
        if(rows.isEmpty()) {TextView empty=text("발송된 알림이 없어. 예약 알림이 발생하면 여기에 표시돼.",15);empty.setPadding(14,32,14,0);list.addView(empty);return;}
        SimpleDateFormat fmt=new SimpleDateFormat("yyyy.MM.dd E HH:mm",Locale.KOREA);
        for(AlarmLogEntity e:rows) {
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card);card.setPadding(20,18,20,18);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin=12;list.addView(card,lp);
            TextView header=text((e.important?"[중요] ":"")+e.title,17);
            header.setTypeface(null,e.read?Typeface.NORMAL:Typeface.BOLD);
            if(e.important)header.setTextColor(getColor(R.color.warning));card.addView(header);
            TextView body=text(e.message,14);body.setPadding(0,6,0,6);card.addView(body);
            TextView date=text(fmt.format(new Date(e.createdAt))+(e.read?" · 읽음":" · 미확인"),12);
            date.setTextColor(getColor(R.color.text_secondary));card.addView(date);
        }
    }
}
