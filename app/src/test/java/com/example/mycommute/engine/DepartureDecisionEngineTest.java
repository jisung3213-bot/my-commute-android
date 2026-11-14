package com.example.mycommute.engine;

import org.junit.Test;
import static org.junit.Assert.*;

public class DepartureDecisionEngineTest {
    @Test public void vehicleEtaSubtractsWalkAndBoardingBuffer() {
        long now=1_000_000L;
        DepartureDecisionEngine.Decision d=DepartureDecisionEngine.compute(now,now+20*60000L,8,2);
        assertEquals(now+10*60000L,d.latestSafeDepartureMillis);
        assertFalse(d.shouldLeaveNow);
    }
    @Test public void dueNowWhenTimeToStopExceedsArrival() {
        long now=1_000_000L;
        DepartureDecisionEngine.Decision d=DepartureDecisionEngine.compute(now,now+5*60000L,7,2);
        assertTrue(d.shouldLeaveNow);
        assertTrue(d.vehicleLikelyMissed);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsMissingLiveEta() {
        DepartureDecisionEngine.compute(10,0,5,2);
    }
}
