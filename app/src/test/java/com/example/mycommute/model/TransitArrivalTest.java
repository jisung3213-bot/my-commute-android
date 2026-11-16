package com.example.mycommute.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class TransitArrivalTest {
    @Test public void switchesToSecondArrivalWhenFirstPasses() {
        long t=1_000_000L;
        TransitArrival a=TransitArrival.found("서울역","1호선","",25,-1,180,t);
        assertEquals(25,a.currentSeconds(t));
        assertEquals(150,a.currentSeconds(t+30_000L));
        assertEquals(-1,a.currentSeconds(t+121_000L));
    }
    @Test public void noSecondVehicleCannotBeInvented() {
        long t=1_000_000L;
        TransitArrival a=TransitArrival.found("서울역","1호선","",20,-1,t);
        assertEquals(-1,a.currentSeconds(t+30_000L));
    }
}
