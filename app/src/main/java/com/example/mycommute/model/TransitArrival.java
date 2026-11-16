package com.example.mycommute.model;

/**
 * A single verified imminent arrival for the FIRST boarding leg. Not a predicted
 * end-to-end arrival, not a replacement for TMAP itinerary duration.
 */
public final class TransitArrival {
    public final boolean available;
    public final String station, line, direction, message;
    public final int seconds, remainingStops;
    public final long fetchedAtMillis;

    private TransitArrival(boolean available,String station,String line,String direction,
                           String message,int seconds,int remainingStops,long receivedAt) {
        this.available=available;this.station=station;this.line=line;this.direction=direction;
        this.message=message;this.seconds=seconds;this.remainingStops=remainingStops;
        this.fetchedAtMillis=receivedAt;
    }
    public static TransitArrival found(String station,String line,String direction,
                                        int seconds,int stops,long now) {
        return new TransitArrival(true,station,line,direction,"",
                Math.max(0,seconds),stops,now);
    }
    public static TransitArrival unavailable(String reason,long now) {
        return new TransitArrival(false,"","","",reason,-1,-1,now);
    }
    public String description() {
        if(!available)return message;
        String time=seconds<60?"약 1분 이내":("약 "+((seconds+59)/60)+"분 후");
        return station+" · "+line+" · "+time+(remainingStops>=0?" · "+remainingStops+"정거장 전":"")
                +(direction.isEmpty()?"":"\n"+direction)
                +" · 조회 시각 기준";
    }
}
