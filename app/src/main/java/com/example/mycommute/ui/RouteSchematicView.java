package com.example.mycommute.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.example.mycommute.model.TransitRoute;
import java.util.ArrayList;
import java.util.List;

/** Minimal schematic, not a map and not to scale. */
public final class RouteSchematicView extends View {
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<String> modes=new ArrayList<>();
    public RouteSchematicView(Context c,AttributeSet a){super(c,a);}
    public void setRoute(TransitRoute route){
        modes.clear();
        if(route!=null)for(TransitRoute.Leg leg:route.legs) {
            if(leg.mode!=null&&!leg.mode.isEmpty())modes.add(leg.mode);
        }
        invalidate();
    }
    private int color(String mode){
        if("BUS".equals(mode))return Color.rgb(43,111,214);
        if("SUBWAY".equals(mode))return Color.rgb(214,62,81);
        return Color.rgb(110,113,124);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float d=getResources().getDisplayMetrics().density;
        float left=25*d,right=getWidth()-25*d,y=37*d;
        if(right<=left)return;
        int edges=Math.max(1,modes.size());
        float step=(right-left)/edges;
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(5*d);
        pen.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i<edges;i++){
            pen.setColor(color(i<modes.size()?modes.get(i):"WALK"));
            canvas.drawLine(left+step*i,y,left+step*(i+1),y,pen);
        }
        pen.setStrokeWidth(2*d);
        for(int i=0;i<=edges;i++){
            pen.setStyle(Paint.Style.FILL);
            pen.setColor(Color.WHITE);canvas.drawCircle(left+step*i,y,6*d,pen);
            pen.setStyle(Paint.Style.STROKE);pen.setColor(Color.rgb(91,86,124));
            canvas.drawCircle(left+step*i,y,6*d,pen);
        }
        pen.setStyle(Paint.Style.FILL);pen.setTextAlign(Paint.Align.CENTER);
        pen.setTextSize(12*d);pen.setColor(Color.rgb(85,90,108));
        canvas.drawText("출발",left,y+23*d,pen);
        canvas.drawText("도착",right,y+23*d,pen);
        for(int i=1;i<edges;i++){
            if(i>8)break; // Avoid overlapping text on multi-leg routes.
            if(edges>4)break;
            String label="WALK".equals(modes.get(i-1))?"도보":
                    "BUS".equals(modes.get(i-1))?"버스":"SUBWAY".equals(modes.get(i-1))?"지하철":"이동";
            canvas.drawText(label,left+step*i,y-15*d,pen);
        }
    }
}
