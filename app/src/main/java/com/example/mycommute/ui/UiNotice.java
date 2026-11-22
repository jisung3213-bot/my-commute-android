package com.example.mycommute.ui;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.example.mycommute.R;

/** Human-readable, non-technical, foreground UI feedback. */
public final class UiNotice {
    public enum State { IDLE, LOADING, EMPTY, TIMEOUT, UNAVAILABLE, ERROR }
    private UiNotice(){}

    /** Stable content stays on its own cards; only exceptional states appear here. */
    public static void show(TextView target,State state,String message){
        if(target==null)return;
        if(state==State.IDLE){
            target.setText("");
            target.setVisibility(View.GONE);
            return;
        }
        target.setText(message);
        target.setTextColor(target.getContext().getColor(
                state==State.ERROR||state==State.TIMEOUT||state==State.UNAVAILABLE
                        ?R.color.warning:R.color.text_secondary));
        target.setVisibility(View.VISIBLE);
    }

    public static void toast(Context context,String message){
        Toast.makeText(context,message,Toast.LENGTH_SHORT).show();
    }

    /** Never show provider URLs, credentials, response bodies or stack traces. */
    public static String error(String technical){
        String text=technical==null?"":technical.toLowerCase(java.util.Locale.ROOT);
        if(text.contains("timeout")||text.contains("timed out")||
                text.contains("시간 초과")||text.contains("시간초과"))
            return "연결 시간 초과";
        if(text.contains("401")||text.contains("403")||
                text.contains("인증키")||text.contains("appkey")||
                text.contains("service_key")||text.contains("권한"))
            return "현재 이용할 수 없는 서비스";
        if(text.contains("429")||text.contains("한도")||text.contains("rate limit"))
            return "잠시 후 다시 이용 가능";
        if(text.contains("인터넷")||text.contains("unknownhost")||
                text.contains("connection")||text.contains("network"))
            return "네트워크 연결 오류";
        if(text.contains("검색 결과")||text.contains("경로 후보")||
                text.contains("데이터 없음")||text.contains("정보 없음")||text.contains("목록 없음"))
            return "검색 결과 없음";
        return "정보를 불러오지 못했습니다";
    }

    public static State classify(String technical){
        String message=error(technical);
        if(message.contains("시간 초과"))return State.TIMEOUT;
        if(message.contains("이용할 수 없는")||message.contains("다시 이용"))return State.UNAVAILABLE;
        if(message.contains("검색 결과 없음"))return State.EMPTY;
        return State.ERROR;
    }
}
