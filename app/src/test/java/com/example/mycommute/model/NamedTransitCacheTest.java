package com.example.mycommute.model;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class NamedTransitCacheTest {
    private static TransitRoute route(int seconds,String start,String end) throws Exception {
        String response="{\"metaData\":{\"plan\":{\"itineraries\":[{"
            +"\"totalTime\":"+seconds+",\"totalWalkTime\":120,\"totalWalkDistance\":150,"
            +"\"transferCount\":0,\"legs\":[{\"mode\":\"BUS\",\"route\":\"400\","
            +"\"sectionTime\":"+seconds+",\"start\":{\"name\":\""+start+"\",\"lat\":37.55,\"lon\":126.97},"
            +"\"end\":{\"name\":\""+end+"\"},"
            +"\"passShape\":{\"linestring\":\"126.97,37.55 126.98,37.56\"}}]}]}}}";
        return TransitRoute.parse(response).get(0);
    }

    @Test public void preservesRouteAndGeometryThroughShortLivedCacheFormat() throws Exception {
        TransitRoute original=route(600,"출발","경유");
        JSONObject cache=original.toCacheJson();
        TransitRoute restored=TransitRoute.fromCacheJson(new JSONObject(cache.toString()));
        assertEquals(600,restored.totalSeconds);
        assertEquals(1,restored.legs.size());
        assertEquals("400",restored.legs.get(0).routeName);
        assertEquals(2,restored.legs.get(0).points.size());
        assertEquals(37.55,restored.legs.get(0).points.get(0).lat,0.00001);
    }

    @Test public void waypointSectionsRemainInTravelOrder() throws Exception {
        TransitRoute first=route(600,"출발","경유");
        TransitRoute next=route(900,"경유","도착");
        TransitRoute combined=TransitRoute.combine(Arrays.asList(first,next));
        assertEquals(1500,combined.totalSeconds);
        assertEquals(2,combined.legs.size());
        assertEquals("경유",combined.legs.get(0).endName);
        assertEquals("경유",combined.legs.get(1).startName);
        assertEquals("도착",combined.legs.get(1).endName);
    }

    @Test public void namedRoutineRoundTripPreservesProfileIdentity() throws Exception {
        Routine r=new Routine();
        r.id="return_123";r.name="금요일 퇴근길";
        r.apiBased=true;r.arrivalTime="19:00";r.days="6";
        r.startName="회사";r.endName="집";
        r.startLat=37.56;r.startLon=126.97;r.endLat=37.57;r.endLon=126.98;
        Waypoint w=new Waypoint();w.name="마트";w.lat=37.565;w.lon=126.976;r.waypoints.add(w);
        Routine back=Routine.fromJson(new JSONObject(r.toJson().toString()));
        assertEquals("return_123",back.id);
        assertEquals("금요일 퇴근길",back.name);
        assertEquals("마트",back.waypoints.get(0).name);
        assertTrue(back.isConfigured());
    }
    @Test public void missingBoardingCoordinatesDoNotBreakCache() throws Exception {
        String response="{\"metaData\":{\"plan\":{\"itineraries\":[{\"totalTime\":420,"
                +"\"legs\":[{\"mode\":\"WALK\",\"sectionTime\":420,"
                +"\"start\":{\"name\":\"출발\"},\"end\":{\"name\":\"도착\"},"
                +"\"steps\":[{\"linestring\":\"126.97,37.55 126.98,37.56\"}]}]}]}}}";
        TransitRoute route=TransitRoute.parse(response).get(0);
        assertTrue(Double.isNaN(route.legs.get(0).startLat));
        TransitRoute restored=TransitRoute.fromCacheJson(
                new JSONObject(route.toCacheJson().toString()));
        assertEquals(420,restored.totalSeconds);
        assertEquals(2,restored.legs.get(0).points.size());
    }

}
