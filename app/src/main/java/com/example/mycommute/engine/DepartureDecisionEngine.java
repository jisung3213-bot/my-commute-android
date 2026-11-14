package com.example.mycommute.engine;

/** Pure calculation for the future live transit adapter. Does not call a provider by itself. */
public final class DepartureDecisionEngine {
    public static final class Decision {
        public final long latestSafeDepartureMillis;
        public final long secondsRemaining;
        public final boolean shouldLeaveNow;
        public final boolean vehicleLikelyMissed;
        Decision(long timestamp,long remaining,boolean now,boolean missed) {
            latestSafeDepartureMillis=timestamp;secondsRemaining=remaining;
            shouldLeaveNow=now;vehicleLikelyMissed=missed;
        }
    }
    private DepartureDecisionEngine() { }
    /** Called once the live provider returned a fresh first-vehicle ETA and walking duration is grounded. */
    public static Decision compute(long nowMillis,long firstVehicleArrivalMillis,
                                   int walkingToStopMinutes,int boardingBufferMinutes) {
        if(firstVehicleArrivalMillis<=0||walkingToStopMinutes<0||boardingBufferMinutes<0)
            throw new IllegalArgumentException("arrival and positive minutes required");
        long latest=firstVehicleArrivalMillis-(walkingToStopMinutes+boardingBufferMinutes)*60000L;
        long left=(latest-nowMillis)/1000L;
        return new Decision(latest,left,nowMillis>=latest,nowMillis>latest);
    }
}
