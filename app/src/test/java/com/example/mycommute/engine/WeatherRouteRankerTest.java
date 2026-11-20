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

 @Test public void largerBufferWeightsTransfersMore(){
  var direct=new WeatherRouteRanker.Candidate("direct","subway",36,4,0,0,false);
  var transfers=new WeatherRouteRanker.Candidate("transfers","subway",32,4,0,2,false);
  var fine=new WeatherRouteRanker.Conditions(true,false,false);
  assertTrue(WeatherRouteRanker.commuteScore(direct,fine,20)
    <WeatherRouteRanker.commuteScore(transfers,fine,20));
  assertEquals(0,WeatherRouteRanker.acceptableExtraMinutes(0));
  assertEquals(10,WeatherRouteRanker.acceptableExtraMinutes(20));
  assertEquals(12,WeatherRouteRanker.acceptableExtraMinutes(90));
 }
 @Test public void rainPrefersShorterWalkingAtSimilarDuration(){
  var walking=new WeatherRouteRanker.Candidate("walk","subway",30,10,0,0,false);
  var covered=new WeatherRouteRanker.Candidate("covered","subway",34,2,0,0,false);
  assertTrue(WeatherRouteRanker.commuteScore(covered,
    new WeatherRouteRanker.Conditions(true,true,false),10)
    <WeatherRouteRanker.commuteScore(walking,
    new WeatherRouteRanker.Conditions(true,true,false),10));
 }

 @Test public void rankingAlwaysPrioritizesRoutesWithinTravelTimeAllowance(){
  var fast=new WeatherRouteRanker.Candidate("fast","transit",30,12,0,2,false);
  var longDirect=new WeatherRouteRanker.Candidate("long","transit",48,0,0,0,false);
  var rain=new WeatherRouteRanker.Conditions(true,true,false);
  var ranked=WeatherRouteRanker.rank(Arrays.asList(fast,longDirect),rain,10);
  assertEquals(Integer.valueOf(0),ranked.get(0));
  assertEquals(Integer.valueOf(1),ranked.get(1));
 }
 @Test public void rankingChangesFromFastToFewerTransfersWithBuffer(){
  var fast=new WeatherRouteRanker.Candidate("fast","transit",30,2,0,2,false);
  var direct=new WeatherRouteRanker.Candidate("direct","transit",34,2,0,0,false);
  var fine=new WeatherRouteRanker.Conditions(true,false,false);
  assertEquals(Integer.valueOf(0),WeatherRouteRanker.rank(Arrays.asList(fast,direct),fine,0).get(0));
  assertEquals(Integer.valueOf(1),WeatherRouteRanker.rank(Arrays.asList(fast,direct),fine,20).get(0));
 }
 @Test public void rankingChangesFromShorterTripToLessWalkingInRain(){
  var shortWalk=new WeatherRouteRanker.Candidate("short","transit",30,10,0,0,false);
  var lessWalk=new WeatherRouteRanker.Candidate("sheltered","transit",34,2,0,0,false);
  var good=new WeatherRouteRanker.Conditions(true,false,false);
  var rain=new WeatherRouteRanker.Conditions(true,true,false);
  assertEquals(Integer.valueOf(0),WeatherRouteRanker.rank(Arrays.asList(shortWalk,lessWalk),good,10).get(0));
  assertEquals(Integer.valueOf(1),WeatherRouteRanker.rank(Arrays.asList(shortWalk,lessWalk),rain,10).get(0));
 }
}
