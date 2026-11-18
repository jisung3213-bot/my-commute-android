package com.example.mycommute.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextUtils;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import com.example.mycommute.model.TransitRoute;

/**
 * Readable topology of itinerary legs, not a geographic map or distance scale.
 * Each transit/walking leg is drawn with its station and duration.
 */
public final class RouteSchematicView extends View {
    private static final int MAX_VISIBLE=8;
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private TransitRoute route;
    private int currentLeg=-1;
    private final int dark=Color.rgb(34,39,58),muted=Color.rgb(105,112,133);
    private final int purple=Color.rgb(99,87,181);
    public RouteSchematicView(Context context,AttributeSet attrs){super(context,attrs);}

    public void setRoute(TransitRoute value){
        if(route==value)return;
        route=value;
        requestLayout();
        invalidate();
    }
    public void setCurrentLeg(int leg){if(currentLeg!=leg){currentLeg=leg;invalidate();}}

    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    private int color(String mode){
        if("BUS".equals(mode))return Color.rgb(44,117,195);
        if("SUBWAY".equals(mode))return Color.rgb(207,71,87);
        return Color.rgb(113,117,132);
    }
    private void text(Canvas c,String content,float x,float baseline,float maxWidth,
                      float sp,int color,boolean bold){
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTypeface(bold?android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL):android.graphics.Typeface.DEFAULT);
        paint.setTextSize(sp*getResources().getDisplayMetrics().scaledDensity);
        paint.setTextAlign(Paint.Align.LEFT);
        String label=TextUtils.ellipsize(content,paint,Math.max(12,maxWidth),
                TextUtils.TruncateAt.END).toString();
        c.drawText(label,x,baseline,paint);
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int count=route==null?0:Math.min(MAX_VISIBLE,route.legs.size());
        int desired=(int)dp(count==0?106:58+count*47+
                (route.legs.size()>MAX_VISIBLE?22:0));
        setMeasuredDimension(resolveSize((int)dp(280),widthSpec),
                resolveSize(desired,heightSpec));
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(route==null||route.legs.isEmpty()){
            text(canvas,"저장된 상세 경로가 없어",dp(18),dp(46),
                    getWidth()-dp(36),14,muted,false);
            text(canvas,"경로 검색 필요",dp(18),dp(73),
                    getWidth()-dp(36),12,muted,false);
            return;
        }
        int count=Math.min(MAX_VISIBLE,route.legs.size());
        float x=dp(25),labelX=dp(51),right=getWidth()-dp(18);
        text(canvas,"출발 · "+route.legs.get(0).startName,labelX,dp(30),
                right-labelX,13,dark,true);
        for(int i=0;i<count;i++){
            TransitRoute.Leg leg=route.legs.get(i);
            int tint=color(leg.mode);
            float startY=dp(27+i*47),endY=startY+dp(47);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(i==currentLeg?5:3));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(tint);
            canvas.drawLine(x,startY+dp(4),x,endY-dp(3),paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(i==currentLeg?purple:Color.WHITE);
            canvas.drawCircle(x,startY,dp(5),paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(i==currentLeg?purple:muted);
            canvas.drawCircle(x,startY,dp(5),paint);

            String mode=RouteSchematicLabels.modeLabel(leg);
            text(canvas,mode,labelX,startY+dp(22),right-labelX,12,
                    i==currentLeg?purple:tint,i==currentLeg);
            text(canvas,leg.endName,labelX,endY+dp(4),right-labelX,13,
                    dark,true);
        }
        float last=dp(27+count*47);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
        canvas.drawCircle(x,last,dp(5),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
        paint.setColor(muted);canvas.drawCircle(x,last,dp(5),paint);
        if(route.legs.size()>MAX_VISIBLE)
            text(canvas,"그 외 "+(route.legs.size()-MAX_VISIBLE)+"개 구간 · 전체 지도에서 확인",
                    dp(18),last+dp(23),right-dp(18),12,muted,false);
    }
}
