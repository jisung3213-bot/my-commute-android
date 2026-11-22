package com.example.mycommute.engine;

import com.example.mycommute.model.TransitRoute;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Stable non-reversible user preference, not a persisted provider itinerary. */
public final class RouteSignature {
    private RouteSignature(){}
    public static int match(List<TransitRoute> routes,String signature){
        if(signature==null||signature.isEmpty())return -1;
        for(int i=0;i<routes.size();i++)if(signature.equals(of(routes.get(i))))return i;
        return -1;
    }
    public static String of(TransitRoute route){
        if(route==null||route.legs.isEmpty())return "";
        StringBuilder data=new StringBuilder();
        for(TransitRoute.Leg leg:route.legs){
            data.append(leg.mode.trim()).append('|').append(leg.routeName.trim())
                .append('|').append(leg.startName.trim()).append('|')
                .append(leg.endName.trim()).append(';');
        }
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256")
                    .digest(data.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder value=new StringBuilder();
            for(byte b:hash)value.append(Character.forDigit((b>>>4)&0xf,16))
                    .append(Character.forDigit(b&0xf,16));
            return value.toString();
        }catch(Exception ex){throw new IllegalStateException("경로 선택값 생성 실패",ex);}
    }
}
