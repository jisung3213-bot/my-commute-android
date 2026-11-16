package com.example.mycommute.engine;

import org.junit.Test;
import static org.junit.Assert.*;
import com.example.mycommute.engine.BoardingFeasibilityEngine.*;

public class BoardingFeasibilityEngineTest {
    private static final long NOW=1_000_000L;
    private Assessment assess(double gpsLat,double gpsLon,long gpsAge,float accuracy,
                              long arrivalAge,int arrivalInSeconds) {
        return BoardingFeasibilityEngine.assess(NOW,gpsLat,gpsLon,NOW-gpsAge,accuracy,
                37.5664,126.9778,NOW-arrivalAge,arrivalInSeconds);
    }

    @Test public void ampleTimeNearStopIsOnlyAnEstimate() {
        Assessment a=assess(37.5664,126.9778,2000,8,1000,900);
        assertEquals(State.TIME_AVAILABLE,a.state);
        assertEquals(0,a.directMeters);
        assertTrue(a.describe().contains("추정"));
    }
    @Test public void approachingVehicleIsNotReachableFromFarAway() {
        Assessment a=assess(37.5700,126.9810,2000,8,1000,120);
        assertEquals(State.TIME_INSUFFICIENT,a.state);
        assertTrue(a.slackSeconds<0);
    }
    @Test public void recentLowAccuracyLocationDoesNotProducePrediction() {
        Assessment a=assess(37.5664,126.9778,1000,350,1000,800);
        assertEquals(State.INACCURATE_POSITION,a.state);
        assertFalse(a.hasEstimate());
    }
    @Test public void staleLocationIsRejected() {
        assertEquals(State.STALE_POSITION,assess(37.5664,126.9778,120000,10,1000,800).state);
    }
    @Test public void staleArrivalIsRejected() {
        assertEquals(State.STALE_ARRIVAL,assess(37.5664,126.9778,1000,10,121000,800).state);
    }
    @Test public void expiredVehicleCannotBeUsedAsNextArrival() {
        assertEquals(State.EXPIRED_VEHICLE,assess(37.5664,126.9778,1000,10,60000,50).state);
    }
    @Test public void missingStopIsRejected() {
        Assessment a=BoardingFeasibilityEngine.assess(NOW,37.5664,126.9778,NOW,5,
                Double.NaN,Double.NaN,NOW,400);
        assertEquals(State.NO_STOP,a.state);
    }
}
