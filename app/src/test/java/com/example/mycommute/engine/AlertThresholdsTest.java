package com.example.mycommute.engine;

import org.junit.Test;
import static org.junit.Assert.*;

public class AlertThresholdsTest {
    @Test public void vehicleRequiresPositiveVerifiedArrival(){
        assertFalse(AlertThresholds.vehicleDue(0,5));
        assertFalse(AlertThresholds.vehicleDue(-1,5));
        assertFalse(AlertThresholds.vehicleDue(301,5));
        assertTrue(AlertThresholds.vehicleDue(300,5));
    }
    @Test public void walkingRequiresNonnegativeSlack(){
        assertTrue(AlertThresholds.walkDue(480,300,120,2));
        assertFalse(AlertThresholds.walkDue(360,300,120,2));
        assertFalse(AlertThresholds.walkDue(480,-1,120,2));
        assertFalse(AlertThresholds.walkDue(0,10,120,2));
    }
    @Test public void delayMustMeetTargetThreshold(){
        assertFalse(AlertThresholds.delayed(1_000_000L,701_000L,5));
        assertTrue(AlertThresholds.delayed(1_000_000L,700_000L,5));
    }
    @Test public void deviationNeedsStableAccurateLocation(){
        assertFalse(AlertThresholds.offRoute(300,20,29_999));
        assertFalse(AlertThresholds.offRoute(500,70,90_000));
        assertTrue(AlertThresholds.offRoute(190,20,30_000));
    }
    @Test public void finishRequiresTravelProgressAndRepeatedFixes(){
        assertFalse(AlertThresholds.nearDestination(30,false,4,5,70_000,20_000));
        assertFalse(AlertThresholds.nearDestination(30,true,0,5,70_000,20_000));
        assertFalse(AlertThresholds.nearDestination(30,true,4,5,70_000,5_000));
        assertTrue(AlertThresholds.nearDestination(30,true,4,5,70_000,20_000));
    }
}
