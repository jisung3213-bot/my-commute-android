package com.example.mycommute.engine;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
/** Initial policy weights; score is not a travel-time prediction. */
public final class WeatherRouteRanker {
    public static final class Conditions {
        public final boolean known,rain,coldAlert,lowTemperature;
        public Conditions(boolean known,boolean rain,boolean coldAlert){this(known,rain,coldAlert,false);}
        public Conditions(boolean known,boolean rain,boolean coldAlert,boolean lowTemperature){this.known=known;this.rain=rain;this.coldAlert=coldAlert;this.lowTemperature=lowTemperature;}
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
        else if(w.known&&w.lowTemperature){walk+=1;wait+=1.5;}
        return r.durationMinutes+walk*r.walkingMinutes+wait*r.outdoorWaitMinutes+3*r.transfers;
    }
    /**
     * Commute preference score. More schedule buffer increases transfer avoidance;
     * adverse forecasts increase walking penalty. Not an ETA prediction.
     */
    public static double commuteScore(Candidate route,Conditions weather,int marginMinutes){
        int margin=Math.max(0,Math.min(180,marginMinutes));
        double transferPenalty=margin>=15?8:margin>=8?6:3;
        return score(route,weather)+(transferPenalty-3)*route.transfers;
    }

    /** Do not recommend an arbitrarily slower route just to save a transfer. */
    public static int acceptableExtraMinutes(int marginMinutes){
        return Math.min(12,Math.max(0,marginMinutes)/2);
    }
    /** Eligible routes always precede longer detours.
     * Scores are preference points, not journey-duration estimates. */
    public static List<Integer> rank(List<Candidate> candidates,Conditions weather,int marginMinutes){
        List<Integer> result=new ArrayList<>();
        if(candidates==null||candidates.isEmpty())return result;
        int fastest=Integer.MAX_VALUE;
        for(int i=0;i<candidates.size();i++){
            result.add(i);
            fastest=Math.min(fastest,candidates.get(i).durationMinutes);
        }
        final int limit=fastest+acceptableExtraMinutes(marginMinutes);
        Collections.sort(result,(a,b)->{
            Candidate x=candidates.get(a),y=candidates.get(b);
            boolean xEligible=x.durationMinutes<=limit;
            boolean yEligible=y.durationMinutes<=limit;
            if(xEligible!=yEligible)return xEligible?-1:1;
            int compare=Double.compare(commuteScore(x,weather,marginMinutes),
                    commuteScore(y,weather,marginMinutes));
            if(compare!=0)return compare;
            compare=Integer.compare(x.durationMinutes,y.durationMinutes);
            return compare!=0?compare:Integer.compare(a,b);
        });
        return result;
    }

    public static Candidate select(List<Candidate> routes,Conditions weather){
        Candidate best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(Candidate r:routes){double s=score(r,weather);if(s<bestScore){best=r;bestScore=s;}}
        return best;
    }
    private WeatherRouteRanker(){}
}
