package com.example.mycommute.engine;
import java.util.List;
/** Initial policy weights; score is not a travel-time prediction. */
public final class WeatherRouteRanker {
    public static final class Conditions {
        public final boolean known,rain,coldAlert;
        public Conditions(boolean known,boolean rain,boolean coldAlert){this.known=known;this.rain=rain;this.coldAlert=coldAlert;}
    }
    public static final class Candidate {
        public final String id,mode;
        public final int durationMinutes,walkingMinutes,outdoorWaitMinutes,transfers;
        public final boolean includesBicycle;
        public Candidate(String id,String mode,int duration,int walk,int wait,int transfers,boolean bike){
            if(duration<1||walk<0||wait<0||transfers<0||walk+wait>duration)throw new IllegalArgumentException("Invalid metrics");
            this.id=id;this.mode=mode;durationMinutes=duration;walkingMinutes=walk;outdoorWaitMinutes=wait;this.transfers=transfers;includesBicycle=bike;
        }
    }
    public static double score(Candidate r,Conditions w){
        if(w.known&&(w.rain||w.coldAlert)&&r.includesBicycle)return Double.POSITIVE_INFINITY;
        double walk=.25,wait=.25;
        if(w.known&&w.rain){walk+=1.5;wait+=1.0;}
        if(w.known&&w.coldAlert){walk+=2;wait+=2.5;}
        return r.durationMinutes+walk*r.walkingMinutes+wait*r.outdoorWaitMinutes+3*r.transfers;
    }
    public static Candidate select(List<Candidate> routes,Conditions weather){
        Candidate best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(Candidate r:routes){double s=score(r,weather);if(s<bestScore){best=r;bestScore=s;}}
        return best;
    }
    private WeatherRouteRanker(){}
}
