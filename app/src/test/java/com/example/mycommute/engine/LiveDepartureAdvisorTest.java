package com.example.mycommute.engine;

import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.model.TransitArrival;
import org.junit.Test;
import static org.junit.Assert.*;

public class LiveDepartureAdvisorTest {
    private static TransitRoute route() throws Exception {
        return TransitRoute.parse("{\"metaData\":{\"plan\":{\"itineraries\":[{\"totalTime\":2700,"
                +"\"legs\":[{\"mode\":\"WALK\",\"sectionTime\":600,\"start\":{\"name\":\"출발\"},"
                +"\"end\":{\"name\":\"승차\"}},{\"mode\":\"SUBWAY\",\"route\":\"1호선\","
                +"\"sectionTime\":2100,\"start\":{\"name\":\"승차\"},\"end\":{\"name\":\"도착\"}}]}]}}}").get(0);
    }
    @Test public void identifiesUnreachableIncomingTrain() throws Exception {
        long now=1_000_000L;
        TransitArrival a=TransitArrival.found("승차","1호선","",180,-1,now);
        LiveDepartureAdvisor.Advice d=LiveDepartureAdvisor.compute(now,now+40*60000,5,route(),600,a);
        assertTrue(d.vehicleVerified);
        assertTrue(d.firstBoardingSlackSeconds<0);
        assertTrue(d.headline.contains("어려움"));
    }
    @Test public void doesNotClaimVerifiedWithoutArrival() throws Exception {
        long now=1_000_000L;
        LiveDepartureAdvisor.Advice d=LiveDepartureAdvisor.compute(now,now+90*60000,5,route(),600,null);
        assertFalse(d.vehicleVerified);
        assertTrue(d.note.contains("미반영"));
    }
}
