package com.example.mycommute.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class TransitArrivalFollowUpTest {
    @Test public void switchesFromFirstToSecondWithoutUsingExpiredData() {
        long now=2_000_000L;
        TransitArrival arrival=TransitArrival.found("광운대","1호선","",40,-1,150,now);
        assertEquals(40,arrival.currentSeconds(now));
        assertEquals(100,arrival.currentSeconds(now+50_000L));
        assertEquals(-1,arrival.currentSeconds(now+121_000L));
    }

    @Test public void invalidSecondVehicleDoesNotAppear() {
        long now=2_000_000L;
        TransitArrival arrival=TransitArrival.found("광운대","1호선","",40,-1,30,now);
        assertEquals(-1,arrival.nextRemainingSeconds(now));
    }
}
