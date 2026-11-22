package com.example.mycommute.network;

import com.example.mycommute.model.Routine;
import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.model.Waypoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * On-demand waypoint route search. Each leg is a separate API call; no implicit
 * claim of guaranteed timed connections across waypoint segments.
 */
public final class TmapWaypointRouteClient {
    private static final int MAX_WAYPOINTS=2;
    private TmapWaypointRouteClient(){}

    public interface Callback{
        void success(List<TransitRoute> combined,int calls);
        void error(String reason);
    }

    public static void search(Routine routine,Callback callback){
        search(routine,3,callback);
    }

    public static void searchForRefresh(Routine routine,Callback callback){
        search(routine,27,callback);
    }

    private static void search(Routine routine,int limit,Callback callback){
        if(routine==null||!routine.validCoordinates()){
            callback.error("출발·도착 장소 좌표 필요");return;
        }
        List<Waypoint> points=new ArrayList<>();
        for(Waypoint w:routine.waypoints)if(w.hasCoordinates())points.add(w);
        if(points.size()>MAX_WAYPOINTS){
            callback.error("대중교통 경로의 경유지는 최대 "+MAX_WAYPOINTS+"곳입니다.");return;
        }
        if(points.isEmpty()){
            TmapTransitClient.search(routine,new TmapTransitClient.Callback(){
                @Override public void success(List<TransitRoute> result){callback.success(result,1);}
                @Override public void error(String why){callback.error(why);}
            });
            return;
        }
        List<Routine> segments=new ArrayList<>();
        double lat=routine.startLat,lon=routine.startLon;
        String name=routine.startName;
        for(int i=0;i<=points.size();i++){
            double endLat=i<points.size()?points.get(i).lat:routine.endLat;
            double endLon=i<points.size()?points.get(i).lon:routine.endLon;
            String endName=i<points.size()?points.get(i).name:routine.endName;
            Routine piece=new Routine();
            piece.startLat=lat;piece.startLon=lon;piece.startName=name;
            piece.endLat=endLat;piece.endLon=endLon;piece.endName=endName;
            segments.add(piece);
            lat=endLat;lon=endLon;name=endName;
        }
        collect(segments,0,new ArrayList<>(),limit,callback);
    }

    private static void collect(List<Routine> segments,int i,
                                List<List<TransitRoute>> options,int limit,Callback callback){
        if(i==segments.size()){
            List<TransitRoute> all=new ArrayList<>();
            buildCombinations(options,0,new ArrayList<>(),all);
            all.sort(java.util.Comparator.comparingInt(r->r.totalSeconds));
            if(all.isEmpty())callback.error("경유지를 연결하는 경로 없음");
            else callback.success(new ArrayList<>(all.subList(0,Math.min(all.size(),limit))),
                    segments.size());
            return;
        }
        TmapTransitClient.search(segments.get(i),new TmapTransitClient.Callback(){
            @Override public void success(List<TransitRoute> result){
                if(result.isEmpty()){callback.error((i+1)+"번째 구간 경로 검색 결과 없음");return;}
                options.add(result);
                collect(segments,i+1,options,limit,callback);
            }
            @Override public void error(String reason){
                callback.error((i+1)+"/"+segments.size()+"번째 경유 구간 검색 실패: "+reason);
            }
        });
    }

    private static void buildCombinations(List<List<TransitRoute>> possibilities,int section,
                                          List<TransitRoute> chosen,List<TransitRoute> output){
        if(section>=possibilities.size()){
            output.add(TransitRoute.combine(chosen));
            return;
        }
        for(TransitRoute option:possibilities.get(section)){
            chosen.add(option);
            buildCombinations(possibilities,section+1,chosen,output);
            chosen.remove(chosen.size()-1);
        }
    }

}
