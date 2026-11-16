package com.example.mycommute.model;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
public class PlaceTest {
 @Test public void prefersPedestrianEntranceAndRoadAddress() throws Exception {
  String json="{\"searchPoiInfo\":{\"pois\":{\"poi\":[{\"name\":\"광운대\","
   +"\"frontLat\":\"37.620\",\"frontLon\":\"127.060\","
   +"\"pnsLat\":\"37.619\",\"pnsLon\":\"127.061\","
   +"\"newAddressList\":{\"newAddress\":[{\"fullAddressRoad\":\"서울 노원구 광운로 20\"}]}}]}}}";
  List<Place> p=Place.parse(json);
  assertEquals(1,p.size());
  assertEquals(37.619,p.get(0).lat,0.00001);
  assertEquals(127.061,p.get(0).lon,0.00001);
  assertEquals("서울 노원구 광운로 20",p.get(0).address);
 }
}
