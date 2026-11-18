package com.example.mycommute.network;

import com.example.mycommute.model.TransitArrival;
import com.example.mycommute.model.TransitRoute;
import org.junit.Test;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import static org.junit.Assert.*;

/** Uses field combinations from a real reported Gwangun Univ. Station response. */
public class SubwayArrivalStatusTest {
    private static long at(String time) throws Exception {
        SimpleDateFormat parser=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);
        parser.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return parser.parse(time).getTime();
    }

    private static TransitRoute.Leg gwangunToSeokgye() throws Exception {
        String json="{\"metaData\":{\"plan\":{\"itineraries\":[{\"totalTime\":1200,"
                +"\"legs\":[{\"mode\":\"SUBWAY\",\"route\":\"수도권1호선\","
                +"\"sectionTime\":800,\"start\":{\"name\":\"광운대\"},"
                +"\"end\":{\"name\":\"종로3가\"},\"passStopList\":{\"stationList\":["
                +"{\"stationName\":\"광운대\"},{\"stationName\":\"석계\"}]}}]}]}}}";
        return TransitRoute.parse(json).get(0).legs.get(0);
    }

    private static String response(String entries) {
        return "{\"errorMessage\":{\"status\":200,\"code\":\"INFO-000\"},"
                +"\"realtimeArrivalList\":["+entries+"]}";
    }

    private static String entry(String line,String terminus,int eta,String status,
                                String message,String observedAt) {
        return "{\"subwayId\":\"1001\",\"statnNm\":\"광운대\","
                +"\"trainLineNm\":\""+line+"\",\"bstatnNm\":\""+terminus+"\","
                +"\"barvlDt\":\""+eta+"\",\"arvlCd\":\""+status+"\","
                +"\"arvlMsg2\":\""+message+"\",\"recptnDt\":\""+observedAt+"\"}";
    }

    @Test public void zeroEtaShowsVerifiedStateButNotFutureArrival() throws Exception {
        String json=response(entry("구로행 - 석계방면","구로",0,"1",
                "광운대 도착","2026-11-18 23:41:50"));
        TransitArrival result=ArrivalMatcher.subwayArrival(
                json,gwangunToSeokgye(),at("2026-11-18 23:41:55"));
        assertFalse(result.available);
        assertTrue(result.statusOnly);
        assertTrue(result.hasFreshStatus(at("2026-11-18 23:42:00")));
        assertEquals("광운대 도착",result.statusText);
        assertEquals(-1,result.currentSeconds(at("2026-11-18 23:42:00")));
    }

    @Test public void rejectsTerminatingAtBoardingStationEvenIfDirectionMentionsSeokgye() throws Exception {
        String json=response(entry("광운대행 - 석계방면","광운대",0,"99",
                "[2]번째 전역 (녹천)","2026-11-18 23:41:50"));
        TransitArrival result=ArrivalMatcher.subwayArrival(
                json,gwangunToSeokgye(),at("2026-11-18 23:41:55"));
        assertFalse(result.statusOnly);
        assertFalse(result.available);
    }

    @Test public void positiveEtaTakesPriorityOverObservedStatus() throws Exception {
        String json=response(entry("구로행 - 석계방면","구로",0,"1",
                "광운대 도착","2026-11-18 23:41:50")+","
                +entry("인천행 - 석계방면","인천",240,"99",
                "3번째 전역","2026-11-18 23:41:50"));
        TransitArrival result=ArrivalMatcher.subwayArrival(
                json,gwangunToSeokgye(),at("2026-11-18 23:41:55"));
        assertTrue(result.available);
        assertFalse(result.statusOnly);
        assertEquals(235,result.seconds);
    }

    @Test public void oldStatusIsNeverPresentedAsFresh() throws Exception {
        String json=response(entry("구로행 - 석계방면","구로",0,"1",
                "광운대 도착","2026-11-18 23:41:50"));
        TransitArrival result=ArrivalMatcher.subwayArrival(
                json,gwangunToSeokgye(),at("2026-11-18 23:44:20"));
        assertFalse(result.statusOnly);
        assertFalse(result.available);
    }

    @Test public void freshStatusExpiresWithoutRefresh() throws Exception {
        String json=response(entry("구로행 - 석계방면","구로",0,"1",
                "광운대 도착","2026-11-18 23:41:50"));
        TransitArrival result=ArrivalMatcher.subwayArrival(
                json,gwangunToSeokgye(),at("2026-11-18 23:41:55"));
        assertTrue(result.statusOnly);
        assertFalse(result.hasFreshStatus(at("2026-11-18 23:44:00")));
    }
}
