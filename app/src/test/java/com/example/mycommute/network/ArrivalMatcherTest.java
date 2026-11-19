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

    private TransitRoute.Leg subwayLegWithNext() throws Exception {
        String route="{\"metaData\":{\"plan\":{\"itineraries\":[{\"totalTime\":1200,\"legs\":["
                +"{\"mode\":\"SUBWAY\",\"route\":\"수도권 1호선\","
                +"\"start\":{\"name\":\"시청역\",\"lat\":37.564,\"lon\":126.978},"
                +"\"end\":{\"name\":\"서울역\"},"
                +"\"passStopList\":{\"stationList\":[{\"stationName\":\"시청\"},"
                +"{\"stationName\":\"서울역\"}]}}]}]}}}";
        return TransitRoute.parse(route).get(0).legs.get(0);
    }

    @Test public void subwayMatchesVerifiedNextStationInTrainDirection() throws Exception {
        TransitRoute.Leg leg=subwayLegWithNext();
        long now=System.currentTimeMillis();
        java.text.SimpleDateFormat time=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.US);
        time.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Seoul"));
        String received=time.format(new java.util.Date(now));
        String json="{\"realtimeArrivalList\":["
                +"{\"subwayId\":\"1001\",\"statnNm\":\"시청\",\"trainLineNm\":\"청량리행 (서울역방면)\","
                +"\"bstatnNm\":\"청량리\",\"recptnDt\":\""+received+"\",\"barvlDt\":210},"
                +"{\"subwayId\":\"1001\",\"statnNm\":\"시청\",\"trainLineNm\":\"인천행 (종각방면)\","
                +"\"bstatnNm\":\"인천\",\"recptnDt\":\""+received+"\",\"barvlDt\":20}]}";
        TransitArrival result=ArrivalMatcher.subwayArrival(json,leg,now);
        assertTrue(result.available);
        assertEquals(210,result.seconds);
    }

    @Test public void subwayAcceptsExplicitNextStationInArrivalDirection() throws Exception {
        TransitRoute.Leg leg=subwayLegWithNext();
        long now=System.currentTimeMillis();
        java.text.SimpleDateFormat time=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.US);
        time.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Seoul"));
        String received=time.format(new java.util.Date(now));
        String json="{\"realtimeArrivalList\":["
                +"{\"subwayId\":\"1001\",\"statnNm\":\"시청\",\"trainLineNm\":\"청량리행\","
                +"\"arvlMsg3\":\"서울역 방면\",\"recptnDt\":\""+received+"\",\"barvlDt\":180}]}";
        TransitArrival result=ArrivalMatcher.subwayArrival(json,leg,now);
        assertTrue(result.available);
        assertEquals(180,result.seconds);
    }

    @Test public void subwayInfo200MeansNoProviderDataNotAuthFailure() throws Exception {
        TransitRoute.Leg leg=subwayLegWithNext();
        String json="{\"errorMessage\":{\"status\":500,\"code\":\"INFO-200\","
                +"\"message\":\"해당하는 데이터가 없습니다.\"}}";
        TransitArrival result=ArrivalMatcher.subwayArrival(json,leg,System.currentTimeMillis());
        assertFalse(result.available);
        assertTrue(result.message.contains("제공하지 않아"));
    }

}
