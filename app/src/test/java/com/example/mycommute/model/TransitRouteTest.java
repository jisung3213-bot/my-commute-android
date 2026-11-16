package com.example.mycommute.model;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TransitRouteTest {
    @Test public void parsesTransitAndWalkSegmentsAndCoordinates() throws Exception {
        String json="{\"metaData\":{\"plan\":{\"itineraries\":[{\"totalTime\":873,"
                +"\"totalWalkTime\":462,\"totalWalkDistance\":492,\"transferCount\":0,"
                +"\"fare\":{\"regular\":{\"totalFare\":1500}},\"legs\":["
                +"{\"mode\":\"WALK\",\"sectionTime\":400,\"start\":{\"name\":\"출발\"},"
                +"\"end\":{\"name\":\"승차\"},\"steps\":[{\"linestring\":\"127.01,37.50 127.02,37.51\"}]},"
                +"{\"mode\":\"BUS\",\"route\":\"123\",\"sectionTime\":411,\"start\":{\"name\":\"승차\"},"
                +"\"end\":{\"name\":\"하차\"},\"passShape\":{\"linestring\":\"127.02,37.51 127.03,37.52\"}}"
                +"]}]}}}";
        List<TransitRoute> list=TransitRoute.parse(json);
        assertEquals(1,list.size());
        TransitRoute route=list.get(0);
        assertEquals(873,route.totalSeconds);
        assertEquals(15,TransitRoute.minutes(route.totalSeconds));
        assertEquals(462,route.walkSeconds);
        assertEquals(1500,route.fareWon);
        assertEquals(2,route.legs.size());
        assertEquals("WALK",route.legs.get(0).mode);
        assertEquals(2,route.legs.get(0).points.size());
        assertEquals(37.5,route.legs.get(0).points.get(0).lat,0.0001);
        assertEquals(127.01,route.legs.get(0).points.get(0).lon,0.0001);
        assertEquals("123",route.legs.get(1).routeName);
    }

    @Test public void failsExplicitlyForMissingPlan() {
        try {
            TransitRoute.parse("{\"metaData\":{}}");
            fail("Expected parse failure");
        } catch(Exception expected) {
            assertTrue(expected.getMessage().contains("경로 목록"));
        }
    }
}
