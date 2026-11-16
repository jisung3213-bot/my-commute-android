package com.example.mycommute.network;

import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ArrivalMatcherTest {
    private TransitRoute.Leg busLeg() throws Exception {
        String json="{\"metaData\":{\"plan\":{\"itineraries\":[{\"totalTime\":1200,\"legs\":["
                +"{\"mode\":\"BUS\",\"route\":\"간선:400\",\"sectionTime\":900,"
                +"\"start\":{\"name\":\"서울역버스환승센터\",\"lat\":37.555,\"lon\":126.972},"
                +"\"end\":{\"name\":\"시청역\"}}]}]}}}";
        List<TransitRoute> routes=TransitRoute.parse(json);
        return routes.get(0).legs.get(0);
    }

    @Test public void choosesVerifiedNearbyTAGOStop() throws Exception {
        TransitRoute.Leg leg=busLeg();
        String stops="{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":{\"item\":["
                +"{\"nodeid\":\"SEOUL001\",\"citycode\":\"25\",\"nodenm\":\"서울역버스환승센터\","
                +"\"gpslati\":37.555,\"gpslong\":126.972},"
                +"{\"nodeid\":\"FAR\",\"citycode\":\"25\",\"nodenm\":\"서울역버스환승센터\","
                +"\"gpslati\":37.556,\"gpslong\":126.973}]}}}}";
        ArrivalMatcher.Stop stop=ArrivalMatcher.chooseNearbyStop(stops,leg);
        assertNotNull(stop);assertEquals("SEOUL001",stop.nodeId);
    }

    @Test public void refusesAmbiguousSameNameStops() throws Exception {
        TransitRoute.Leg leg=busLeg();
        String stops="{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":{\"item\":["
                +"{\"nodeid\":\"A\",\"citycode\":\"25\",\"nodenm\":\"서울역버스환승센터\","
                +"\"gpslati\":37.555,\"gpslong\":126.972},"
                +"{\"nodeid\":\"B\",\"citycode\":\"25\",\"nodenm\":\"서울역버스환승센터\","
                +"\"gpslati\":37.55501,\"gpslong\":126.972}]}}}}";
        assertNull(ArrivalMatcher.chooseNearbyStop(stops,leg));
    }

    @Test public void matchesExactBusRouteAndStop() throws Exception {
        TransitRoute.Leg leg=busLeg();
        ArrivalMatcher.Stop stop=new ArrivalMatcher.Stop("25","SEOUL001","서울역버스환승센터",37.555,126.972);
        String arrivals="{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":{\"item\":["
                +"{\"nodeid\":\"SEOUL001\",\"routeno\":\"400\",\"arrtime\":225,\"arrprevstationcnt\":2},"
                +"{\"nodeid\":\"SEOUL001\",\"routeno\":\"401\",\"arrtime\":30,\"arrprevstationcnt\":1}]}}}}";
        TransitArrival result=ArrivalMatcher.busArrival(arrivals,leg,stop,1234567L);
        assertTrue(result.available);assertEquals(225,result.seconds);
        assertEquals(2,result.remainingStops);
    }

    @Test public void refusesUnknownSubwayDirection() throws Exception {
        String json="{\"metaData\":{\"plan\":{\"itineraries\":[{\"totalTime\":1200,\"legs\":["
                +"{\"mode\":\"SUBWAY\",\"route\":\"1호선\",\"start\":{\"name\":\"시청역\"},"
                +"\"end\":{\"name\":\"서울역\"}}]}]}}}";
        TransitRoute.Leg leg=TransitRoute.parse(json).get(0).legs.get(0);
        TransitArrival arrival=ArrivalMatcher.subwayArrival(
                "{\"realtimeArrivalList\":[{\"subwayId\":\"1001\",\"statnNm\":\"시청\","
                        +"\"trainLineNm\":\"청량리행\",\"barvlDt\":100}]}",
                leg,System.currentTimeMillis());
        assertFalse(arrival.available);
    }
}
