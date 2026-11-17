package com.example.mycommute.network;

import com.example.mycommute.model.TransitRoute;
import com.example.mycommute.model.TransitArrival;
import org.junit.Test;
import static org.junit.Assert.*;

public class SubwayUnverifiedDirectionTest {
    @Test public void showsUnderlyingLineCountButDoesNotClaimTrainDirection() throws Exception {
        String route="{\"metaData\":{\"plan\":{\"itineraries\":[{\"totalTime\":1200,\"legs\":[{\"mode\":\"SUBWAY\",\"route\":\"1호선\",\"sectionTime\":400,\"start\":{\"name\":\"광운대\"},\"end\":{\"name\":\"종로3가\"}}]}]}}}";
        TransitRoute.Leg leg=TransitRoute.parse(route).get(0).legs.get(0);
        String live="{\"realtimeArrivalList\":[{\"subwayId\":\"1001\",\"statnNm\":\"광운대\",\"trainLineNm\":\"인천행 - 석계방면\",\"barvlDt\":180,\"recptnDt\":\"2026-11-17 16:00:00\"}]}";
        // Use the record timestamp as the evaluation clock.
        java.text.SimpleDateFormat parser=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.KOREA);
        parser.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Seoul"));
        TransitArrival a=ArrivalMatcher.subwayArrival(live,leg,parser.parse("2026-11-17 16:00:01").getTime());
        assertFalse(a.available);
        assertTrue(a.message.contains("1건"));
        assertTrue(a.message.contains("방향 미검증"));
    }
}
