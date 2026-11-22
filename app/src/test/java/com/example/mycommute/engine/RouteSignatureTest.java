package com.example.mycommute.engine;

import com.example.mycommute.model.TransitRoute;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class RouteSignatureTest {
    private static TransitRoute parse(String name,int duration) throws Exception {
        String json="{\"metaData\":{\"plan\":{\"itineraries\":[{\"totalTime\":"+duration+
                ",\"legs\":[{\"mode\":\"SUBWAY\",\"route\":\"1호선\","
                +"\"sectionTime\":300,\"start\":{\"name\":\""+name+
                "\"},\"end\":{\"name\":\"서울역\"}}]}]}}}";
        List<TransitRoute> r=TransitRoute.parse(json);
        return r.get(0);
    }
    @Test public void ignoresTemporaryTravelTimeChanges() throws Exception {
        assertEquals(RouteSignature.of(parse("광운대역",1200)),
                RouteSignature.of(parse("광운대역",1800)));
    }
    @Test public void distinguishesDifferentBoardingPatterns() throws Exception {
        assertNotEquals(RouteSignature.of(parse("광운대역",1200)),
                RouteSignature.of(parse("청량리역",1200)));
    }

    @Test public void matchesThePinnedRouteEvenAfterTheProviderChangesOrder() throws Exception {
        TransitRoute original=parse("광운대역",1200);
        TransitRoute other=parse("청량리역",1100);
        TransitRoute fresh=parse("광운대역",1800);
        assertEquals(1,RouteSignature.match(List.of(other,fresh),RouteSignature.of(original)));
        assertEquals(-1,RouteSignature.match(List.of(other),RouteSignature.of(original)));
    }
}
