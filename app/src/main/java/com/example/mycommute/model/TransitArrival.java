package com.example.mycommute.model;

/**
 * A single verified imminent arrival for the FIRST boarding leg. Not a predicted
 * end-to-end arrival, not a replacement for TMAP itinerary duration.
 */
public final class TransitArrival {
    public final boolean available;
    public final String station, line, direction, message;
    public final int seconds, remainingStops;
    /** Second verified vehicle, or -1 when the provider did not return one. */
    public final int nextSeconds;
    public final long fetchedAtMillis;
    /** Verified line/direction status snapshot, NOT a predicted future arrival. */
    public final boolean statusOnly;
    public final String statusText;
    public final long statusObservedMillis;
    private static final long MAX_STATUS_AGE_MS=120_000L;

    private TransitArrival(boolean available,String station,String line,String direction,
                           String message,int seconds,int remainingStops,int nextSeconds,long receivedAt,
                           boolean statusOnly,String statusText,long observedAt) {
        this.available=available;this.station=station;this.line=line;this.direction=direction;
        this.message=message;this.seconds=seconds;this.remainingStops=remainingStops;
        this.nextSeconds=nextSeconds;
        this.fetchedAtMillis=receivedAt;
        this.statusOnly=statusOnly;this.statusText=statusText;this.statusObservedMillis=observedAt;
    }
    public static TransitArrival found(String station,String line,String direction,
                                        int seconds,int stops,long now) {
        return found(station,line,direction,seconds,stops,-1,now);
    }
    public static TransitArrival found(String station,String line,String direction,
                                        int seconds,int stops,int nextSeconds,long now) {
        return new TransitArrival(true,station,line,direction,"",
                Math.max(0,seconds),stops,nextSeconds>seconds?nextSeconds:-1,now,
                false,"",0);
    }
    public int currentSeconds(long now) {
        if(!available||now<fetchedAtMillis-30000L||now-fetchedAtMillis>120000L)return -1;
        int past=(int)Math.max(0,(now-fetchedAtMillis)/1000L);
        int primary=seconds-past;
        if(primary>0)return primary;
        int following=nextSeconds-past;
        return nextSeconds>=0&&following>0?following:-1;
    }
    public int nextRemainingSeconds(long now) {
        if(!available||nextSeconds<0||now<fetchedAtMillis-30000L||now-fetchedAtMillis>120000L)return -1;
        int past=(int)Math.max(0,(now-fetchedAtMillis)/1000L);
        return nextSeconds-past>0?nextSeconds-past:-1;
    }
    /** Status exists but a future ETA is unavailable. Never makes available=true. */
    public static TransitArrival status(String station,String line,String direction,
                                         String statusText,long observedAt,long now) {
        return new TransitArrival(false,station,line,direction,"", -1,-1,-1,now,
                true,statusText,observedAt);
    }
    public boolean hasFreshStatus(long now) {
        long age=now-statusObservedMillis;
        return statusOnly&&statusObservedMillis>0&&age>= -30_000L&&age<=MAX_STATUS_AGE_MS;
    }
    public static TransitArrival unavailable(String reason,long now) {
        return new TransitArrival(false,"","","",reason,-1,-1,-1,now,false,"",0);
    }
    public String description() {
        if(statusOnly){
            return hasFreshStatus(System.currentTimeMillis())
                    ?station+" · "+line+" · 조회 당시 "+statusText+" (도착예정시간 미제공)"
                    :"열차 상태정보 만료 · 새 조회 필요";
        }
        if(!available)return message;
        int live=currentSeconds(System.currentTimeMillis());
        String time=live<0?"도착정보 만료":live<60?"약 1분 이내":("약 "+((live+59)/60)+"분 후");
        return station+" · "+line+" · "+time+(remainingStops>=0?" · "+remainingStops+"정거장 전":"")
                +(direction.isEmpty()?"":"\n"+direction)
                +(nextRemainingSeconds(System.currentTimeMillis())>0?"\n다음 차량 약 "+((nextRemainingSeconds(System.currentTimeMillis())+59)/60)+"분 후":"")
                +" · 조회 시각 기준";
    }
}
