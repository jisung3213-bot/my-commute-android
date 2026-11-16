package com.example.mycommute.engine;

import com.example.mycommute.model.TransitRoute;
import org.junit.Test;
import static org.junit.Assert.*;

public class StagePositionMatcherTest {
    private TransitRoute route() throws Exception {
        String json="{\"metaData\":{\"plan\":{\"itineraries\":[{\"totalTime\":1200,\"legs\":["
                +"{\"mode\":\"WALK\",\"sectionTime\":180,\"start\":{\"name\":\"집\"},"
                +"\"end\":{\"name\":\"역입구\"},\"steps\":[{\"linestring\":\"127.00,37.50 127.001,37.501\"}]},"
                +"{\"mode\":\"SUBWAY\",\"route\":\"1호선\",\"sectionTime\":900,"
                +"\"start\":{\"name\":\"역입구\",\"lat\":37.505,\"lon\":127.005},"
                +"\"end\":{\"name\":\"다음역\"},"
                +"\"passShape\":{\"linestring\":\"127.005,37.505 127.01,37.51\"}}"
                +"]}]}}}";
        return TransitRoute.parse(json).get(0);
    }

    @Test public void validFixMatchesClosestWalkingLeg() throws Exception {
        TransitRoute r=route();
        StagePositionMatcher.Match m=StagePositionMatcher.locate(
                r,37.5004,127.0004,999_000L,10f,1_000_000L);
        assertTrue(m.known());
        assertEquals(0,m.legIndex);
        assertEquals(1,StagePositionMatcher.boardingIndex(r,m));
    }

    @Test public void staleFixIsNotUsed() throws Exception {
        StagePositionMatcher.Match m=StagePositionMatcher.locate(
                route(),37.5004,127.0004,800_000L,10f,1_000_000L);
        assertFalse(m.known());
    }

    @Test public void unrelatedGpsCannotClaimBoarding() throws Exception {
        StagePositionMatcher.Match m=StagePositionMatcher.locate(
                route(),37.65,127.20,999_000L,10f,1_000_000L);
        assertFalse(m.known());
    }
}
