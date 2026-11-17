package com.example.mycommute.ui;

import com.example.mycommute.model.TransitRoute;

/** Pure labels used in the schematic; keeps route names visible for long paths. */
public final class RouteSchematicLabels {
    private RouteSchematicLabels(){}
    public static String modeLabel(TransitRoute.Leg leg){
        String mode;
        if("BUS".equals(leg.mode))mode="버스";
        else if("SUBWAY".equals(leg.mode))mode="지하철";
        else if("WALK".equals(leg.mode))mode=
                leg.startName.equals(leg.endName)?"환승 통로":"도보";
        else mode="이동";
        String route=leg.routeName.trim();
        return mode+(route.isEmpty()?"":" "+route)+" · "+
                TransitRoute.minutes(leg.seconds)+"분";
    }
}
