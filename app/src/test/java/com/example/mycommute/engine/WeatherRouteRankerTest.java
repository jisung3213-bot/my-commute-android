package com.example.mycommute.engine;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
public class WeatherRouteRankerTest {
 private WeatherRouteRanker.Candidate route(String id,int time,int walk,int wait,boolean bike){return new WeatherRouteRanker.Candidate(id,"test",time,walk,wait,0,bike);}
 @Test public void rainPrefersLessOutdoorWalking(){
  var fast=route("fast",30,15,0,false);var sheltered=route("sheltered",38,2,0,false);
  assertSame(fast,WeatherRouteRanker.select(Arrays.asList(fast,sheltered),new WeatherRouteRanker.Conditions(true,false,false)));
  assertSame(sheltered,WeatherRouteRanker.select(Arrays.asList(fast,sheltered),new WeatherRouteRanker.Conditions(true,true,false)));
  assertEquals(38,sheltered.durationMinutes);
 }
 @Test public void rainExcludesBicycle(){var bike=route("bike",20,0,0,true);assertNull(WeatherRouteRanker.select(Arrays.asList(bike),new WeatherRouteRanker.Conditions(true,true,false)));}
 @Test public void coldExcludesBicycle(){var bike=route("bike",20,0,0,true);assertNull(WeatherRouteRanker.select(Arrays.asList(bike),new WeatherRouteRanker.Conditions(true,false,true)));}
 @Test public void combinedConditionsAccumulate(){var r=route("walk",30,10,5,false);assertTrue(WeatherRouteRanker.score(r,new WeatherRouteRanker.Conditions(true,true,true))>WeatherRouteRanker.score(r,new WeatherRouteRanker.Conditions(true,false,true)));}
 @Test public void unknownWeatherDoesNotInventAnAlert(){var r=route("walk",30,10,5,false);assertEquals(WeatherRouteRanker.score(r,new WeatherRouteRanker.Conditions(false,true,true)),WeatherRouteRanker.score(r,new WeatherRouteRanker.Conditions(false,false,false)),.001);}
 @Test(expected=IllegalArgumentException.class) public void rejectsImpossibleMetrics(){route("bad",10,20,0,false);}
}
