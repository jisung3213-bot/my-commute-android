package com.example.mycommute.ui;

import com.example.mycommute.model.TransitRoute;
import org.junit.Test;
import static org.junit.Assert.*;

public class RouteSchematicLabelsTest {
    private TransitRoute.Leg leg(String mode,String line,String start,String end) throws Exception {
        String json="{\"metaData\":{\"plan\":{\"itineraries\":[{\"totalTime\":900,"
                +"\"legs\":[{\"mode\":\""+mode+"\",\"route\":\""+line+"\","
                +"\"sectionTime\":540,\"start\":{\"name\":\""+start+
                "\"},\"end\":{\"name\":\""+end+"\"}}]}]}}}";
        return TransitRoute.parse(json).get(0).legs.get(0);
    }
    @Test public void namesBusLineAndDuration() throws Exception {
        assertEquals("버스 지선:7018 · 9분",
                RouteSchematicLabels.modeLabel(leg("BUS","지선:7018","승차","하차")));
    }
    @Test public void namesWithinStationTransfer() throws Exception {
        assertEquals("환승 통로 · 9분",
                RouteSchematicLabels.modeLabel(leg("WALK","","종로3가","종로3가")));
    }
    @Test public void namesSubwayLine() throws Exception {
        assertEquals("지하철 수도권1호선 · 9분",
                RouteSchematicLabels.modeLabel(leg("SUBWAY","수도권1호선","시청","광운대")));
    }
}
