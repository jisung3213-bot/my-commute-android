package com.example.mycommute.engine;

import com.example.mycommute.model.TransitRoute;

/**
 * GPS proximity to itinerary geometry. A location near a rail line does not
 * prove boarding. Ambiguous intersections remain unknown.
 */
public final class StagePositionMatcher {
    public static final class Match {
        public final int legIndex;
        public final double distanceMeters;
        public final String description;
        Match(int index,double distance,String description){
            this.legIndex=index;this.distanceMeters=distance;this.description=description;
        }
        public boolean known(){return legIndex>=0;}
    }
    private StagePositionMatcher(){}

    public static Match locate(TransitRoute route,double lat,double lon,
                               long fixTime,float accuracy,long now){
        if(route==null||route.legs.isEmpty())return unknown("이동 경로 없음");
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat<33||lat>39||lon<124||lon>132||
                fixTime<=0||now-fixTime>90000||now-fixTime< -30000)
            return unknown("최신 GPS 위치 없음");
        if(!Float.isFinite(accuracy)||accuracy<0||accuracy>75)
            return unknown("GPS 정확도가 낮아");
        double best=Double.POSITIVE_INFINITY,second=Double.POSITIVE_INFINITY;
        int index=-1;
        for(int i=0;i<route.legs.size();i++){
            TransitRoute.Leg leg=route.legs.get(i);
            double dist=distanceToLeg(leg,lat,lon);
            if(dist<best){second=best;best=dist;index=i;}
            else if(dist<second)second=dist;
        }
        if(index<0||best>160)return unknown("경로 인근 위치 확인 불가");
        if(second-best<25)return unknown("경로 구간 중첩 · 현재 단계 확인 불가");
        TransitRoute.Leg leg=route.legs.get(index);
        String kind="WALK".equals(leg.mode)?"도보":
                "BUS".equals(leg.mode)?"버스 노선 주변":
                "SUBWAY".equals(leg.mode)?"지하철 선로 주변":"이동";
        return new Match(index,best,kind+" 위치 추정 · "+leg.endName+" 방향 (실제 승차 여부 미확인)");
    }

    public static int boardingIndex(TransitRoute route,Match match){
        if(route==null)return -1;
        int start=match!=null&&match.known()?match.legIndex:0;
        // When near a bus/subway segment, it is NOT safe to assume boarding.
        // Check the same leg before considering later transfers.
        for(int i=start;i<route.legs.size();i++){
            String mode=route.legs.get(i).mode;
            if("BUS".equals(mode)||"SUBWAY".equals(mode))return i;
        }
        for(int i=0;i<route.legs.size();i++)
            if("BUS".equals(route.legs.get(i).mode)||"SUBWAY".equals(route.legs.get(i).mode))return i;
        return -1;
    }

    private static Match unknown(String why){return new Match(-1,Double.NaN,why);}

    private static double distanceToLeg(TransitRoute.Leg leg,double lat,double lon){
        double best=Double.POSITIVE_INFINITY;
        if(leg.points.size()>=2){
            for(int i=1;i<leg.points.size();i++){
                TransitRoute.Point a=leg.points.get(i-1),b=leg.points.get(i);
                best=Math.min(best,segmentDistance(lat,lon,a.lat,a.lon,b.lat,b.lon));
            }
        }else{
            for(TransitRoute.Point p:leg.points)
                best=Math.min(best,meters(lat,lon,p.lat,p.lon));
        }
        if(Double.isFinite(leg.startLat)&&Double.isFinite(leg.startLon)&&
                leg.startLat>33&&leg.startLat<39)
            best=Math.min(best,meters(lat,lon,leg.startLat,leg.startLon));
        return best;
    }

    private static double segmentDistance(double lat,double lon,
                                          double lat1,double lon1,double lat2,double lon2){
        double scale=111195.0;
        double cos=Math.cos(Math.toRadians(lat));
        double ax=(lon1-lon)*scale*cos, ay=(lat1-lat)*scale;
        double bx=(lon2-lon)*scale*cos, by=(lat2-lat)*scale;
        double dx=bx-ax,dy=by-ay;
        double len=dx*dx+dy*dy;
        double t=len<0.001?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/len));
        return Math.hypot(ax+dx*t,ay+dy*t);
    }
    private static double meters(double lat1,double lon1,double lat2,double lon2){
        return segmentDistance(lat1,lon1,lat2,lon2,lat2,lon2);
    }
}
