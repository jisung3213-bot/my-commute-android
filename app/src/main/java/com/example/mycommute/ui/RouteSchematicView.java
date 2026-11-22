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
    private float gpsProgress=0.5f;
    private boolean gpsVisible;
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

    /** Draw a marker at a chosen schematic position for an isolated debug preview.
     * Does not change Android GPS, route storage, or production location state. */
    public void setPreviewPosition(int leg,float progress){
        int bounded=route!=null&&leg>=0&&leg<route.legs.size()?leg:-1;
        setCurrentLeg(bounded);
        float next=Math.max(0f,Math.min(1f,progress));
        boolean visible=bounded>=0;
        if(gpsVisible!=visible||Math.abs(gpsProgress-next)>0.001f){
            gpsVisible=visible;gpsProgress=next;invalidate();
        }
    }

    /** A schematic progress indicator, not a geographically precise map pin.
     * Only the caller's fresh, accurately matched GPS position is displayed. */
    public void setGpsPosition(double lat,double lon,boolean verified){
        boolean visible=verified&&route!=null&&currentLeg>=0
                &&currentLeg<route.legs.size()
                &&Double.isFinite(lat)&&Double.isFinite(lon);
        float progress=0.5f;
        if(visible){
            TransitRoute.Leg leg=route.legs.get(currentLeg);
            if(leg.points.size()>=2){
                double total=0,best=Double.POSITIVE_INFINITY,selected=0;
                for(int i=1;i<leg.points.size();i++){
                    TransitRoute.Point a=leg.points.get(i-1),b=leg.points.get(i);
                    double scale=111195.0,cos=Math.cos(Math.toRadians(lat));
                    double ax=(a.lon-lon)*scale*cos,ay=(a.lat-lat)*scale;
                    double bx=(b.lon-lon)*scale*cos,by=(b.lat-lat)*scale;
                    double dx=bx-ax,dy=by-ay,length=Math.hypot(dx,dy);
                    double t=length<0.001?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/(length*length)));
                    double distance=Math.hypot(ax+dx*t,ay+dy*t);
                    if(distance<best){best=distance;selected=total+length*t;}
                    total+=length;
                }
                if(total>0.001)progress=(float)Math.max(0,Math.min(1,selected/total));
            }
        }
        if(gpsVisible!=visible||Math.abs(gpsProgress-progress)>0.005f){
            gpsVisible=visible;gpsProgress=progress;invalidate();
        }
    }

    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    private float rowHeight(){
        // Keep adjacent labels separated when the user increases system font size.
        return dp(47)*Math.max(1f,getResources().getConfiguration().fontScale);
    }
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
        int desired=(int)(count==0?dp(106)*Math.max(1f,getResources().getConfiguration().fontScale)
                :dp(58)+count*rowHeight()+
                (route.legs.size()>MAX_VISIBLE?dp(30):0));
        setMeasuredDimension(resolveSize((int)dp(280),widthSpec),
                resolveSize(desired,heightSpec));
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(route==null||route.legs.isEmpty()){
            text(canvas,"저장된 상세 경로 없음",dp(18),dp(46),
                    getWidth()-dp(36),14,muted,false);
            text(canvas,"경로 검색 필요",dp(18),dp(73),
                    getWidth()-dp(36),12,muted,false);
            return;
        }
        int count=Math.min(MAX_VISIBLE,route.legs.size());
        float x=dp(25),labelX=dp(51),right=getWidth()-dp(18);
        text(canvas,"출발 · "+route.legs.get(0).startName,labelX,dp(30),
                right-labelX,13,dark,true);
        float step=rowHeight();
        for(int i=0;i<count;i++){
            TransitRoute.Leg leg=route.legs.get(i);
            int tint=color(leg.mode);
            float startY=dp(27)+i*step,endY=startY+step;
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

            if(gpsVisible&&i==currentLeg){
                // Distinct from route/station nodes: blue GPS puck with white center.
                float markerY=startY+dp(4)+(step-dp(7))*gpsProgress;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x,markerY,dp(10),paint);
                paint.setColor(Color.rgb(44,117,195));
                canvas.drawCircle(x,markerY,dp(7.5f),paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x,markerY,dp(2.5f),paint);
            }

            String mode=RouteSchematicLabels.modeLabel(leg);
            text(canvas,mode,labelX,startY+dp(22),right-labelX,12,
                    i==currentLeg?purple:tint,i==currentLeg);
            text(canvas,leg.endName,labelX,endY+dp(4),right-labelX,13,
                    dark,true);
        }
        float last=dp(27)+count*step;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
        canvas.drawCircle(x,last,dp(5),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
        paint.setColor(muted);canvas.drawCircle(x,last,dp(5),paint);
        if(route.legs.size()>MAX_VISIBLE)
            text(canvas,"그 외 "+(route.legs.size()-MAX_VISIBLE)+"개 구간 · 전체 지도에서 확인",
                    dp(18),last+dp(23),right-dp(18),12,muted,false);
    }
}
